package com.dougymalone.codeworks.client.screen;

import com.dougymalone.codeworks.channel.ChannelIds;
import com.dougymalone.codeworks.network.SaveBreakerChannelPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Breaker's only UI: pick ONE channel. Can cycle / pick from Central Command registries,
 * or type a matching id manually.
 */
public class BreakerChannelScreen extends Screen {
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFFA0A0A0;
	private static final int ACCENT = 0xFF66CCFF;

	private final BlockPos pos;
	private final List<String> availableChannels = new ArrayList<>();
	private EditBox channelBox;
	private String channel;
	private int cycleIndex = -1;

	public BreakerChannelScreen(BlockPos pos, String channel, String availableChannelsCsv) {
		super(Component.translatable("gui.codeworks.breaker_channel_title"));
		this.pos = pos;
		this.channel = channel == null ? "" : ChannelIds.sanitize(channel);
		if (availableChannelsCsv != null && !availableChannelsCsv.isBlank()) {
			for (String part : availableChannelsCsv.split("\n")) {
				String id = ChannelIds.sanitize(part);
				if (!id.isEmpty() && !this.availableChannels.contains(id)) {
					this.availableChannels.add(id);
				}
			}
		}
		if (!this.channel.isEmpty()) {
			this.cycleIndex = this.availableChannels.indexOf(this.channel);
		}
	}

	@Override
	protected void init() {
		int width = Math.min(260, this.width - 40);
		int x = (this.width - width) / 2;
		int y = 70;
		this.channelBox = new EditBox(this.font, x, y, width, 20, Component.translatable("gui.codeworks.channel"));
		this.channelBox.setMaxLength(ChannelIds.MAX_LENGTH);
		this.channelBox.setValue(this.channel);
		this.channelBox.setHint(Component.translatable("gui.codeworks.channel_hint"));
		this.addRenderableWidget(this.channelBox);

		int rowY = y + 28;
		if (!this.availableChannels.isEmpty()) {
			this.addRenderableWidget(
				Button.builder(Component.translatable("gui.codeworks.channel_prev"), b -> this.cycle(-1))
					.bounds(x, rowY, 60, 20)
					.build()
			);
			this.addRenderableWidget(
				Button.builder(Component.translatable("gui.codeworks.channel_next"), b -> this.cycle(1))
					.bounds(x + width - 60, rowY, 60, 20)
					.build()
			);
			rowY += 28;
			int listTop = rowY;
			int maxRows = Math.min(this.availableChannels.size(), 6);
			for (int i = 0; i < maxRows; i++) {
				final String id = this.availableChannels.get(i);
				boolean selected = id.equals(ChannelIds.sanitize(this.channelBox.getValue()));
				String label = (selected ? "> " : "  ") + id;
				this.addRenderableWidget(
					Button.builder(Component.literal(label), b -> this.pickChannel(id))
						.bounds(x, listTop + i * 22, width, 20)
						.build()
				);
			}
			rowY = listTop + maxRows * 22 + 8;
		} else {
			rowY += 8;
		}

		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.save"), button -> this.save())
				.bounds(x, rowY, width / 2 - 4, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_DONE, button -> this.save())
				.bounds(x + width / 2 + 4, rowY, width / 2 - 4, 20)
				.build()
		);
	}

	private void cycle(int delta) {
		if (this.availableChannels.isEmpty()) {
			return;
		}
		if (this.cycleIndex < 0) {
			this.cycleIndex = delta > 0 ? 0 : this.availableChannels.size() - 1;
		} else {
			int size = this.availableChannels.size();
			this.cycleIndex = Math.floorMod(this.cycleIndex + delta, size);
		}
		pickChannel(this.availableChannels.get(this.cycleIndex));
	}

	private void pickChannel(String id) {
		this.channel = id;
		this.cycleIndex = this.availableChannels.indexOf(id);
		if (this.channelBox != null) {
			this.channelBox.setValue(id);
		}
		this.rebuildWidgets();
	}

	private void save() {
		this.channel = this.channelBox.getValue();
		ClientPlayNetworking.send(new SaveBreakerChannelPayload(this.pos, this.channel));
		this.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 24, TEXT);
		graphics.centeredText(
			this.font,
			Component.translatable("gui.codeworks.breaker_channel_hint"),
			this.width / 2,
			48,
			MUTED
		);
		if (!this.availableChannels.isEmpty()) {
			graphics.centeredText(
				this.font,
				Component.translatable("gui.codeworks.breaker_known_count", this.availableChannels.size()),
				this.width / 2,
				58,
				ACCENT
			);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
