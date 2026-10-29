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
 * Breaker picks ONE channel. Panel drawn before widgets; opaque ARGB text for MC 26.3.
 */
public class BreakerChannelScreen extends Screen {
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFFA0A0A0;
	private static final int ACCENT = 0xFF66CCFF;
	private static final int OK = 0xFF55FF55;
	private static final int PANEL = 0xE0101418;
	private static final int PANEL_EDGE = 0xFF2A3340;
	private static final int SELECTED_BG = 0xFF1A3A4A;

	private final BlockPos pos;
	private final List<String> availableChannels = new ArrayList<>();
	private EditBox channelBox;
	private String channel;
	private int cycleIndex = -1;
	private int listScroll;

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

	private int contentWidth() {
		return Math.min(280, Math.max(200, this.width - 40));
	}

	private int contentLeft() {
		return (this.width - contentWidth()) / 2;
	}

	@Override
	protected void init() {
		int width = contentWidth();
		int x = contentLeft();
		int y = 64;

		this.channelBox = new EditBox(this.font, x, y, width, 20, Component.translatable("gui.codeworks.channel"));
		this.channelBox.setMaxLength(ChannelIds.MAX_LENGTH);
		this.channelBox.setValue(this.channel);
		this.channelBox.setHint(Component.translatable("gui.codeworks.channel_hint"));
		this.addRenderableWidget(this.channelBox);

		int rowY = y + 28;
		if (!this.availableChannels.isEmpty()) {
			this.addRenderableWidget(
				Button.builder(Component.translatable("gui.codeworks.channel_prev"), b -> this.cycle(-1))
					.bounds(x, rowY, 64, 20)
					.build()
			);
			this.addRenderableWidget(
				Button.builder(Component.translatable("gui.codeworks.channel_next"), b -> this.cycle(1))
					.bounds(x + width - 64, rowY, 64, 20)
					.build()
			);
			rowY += 28;

			int listBottom = this.height - 56;
			int rowH = 22;
			int visible = Math.max(1, (listBottom - rowY) / rowH);
			int maxScroll = Math.max(0, this.availableChannels.size() - visible);
			if (this.listScroll > maxScroll) {
				this.listScroll = maxScroll;
			}
			for (int i = this.listScroll; i < this.availableChannels.size(); i++) {
				if (rowY + rowH > listBottom) {
					break;
				}
				final String id = this.availableChannels.get(i);
				boolean selected = id.equals(ChannelIds.sanitize(this.channel));
				String label = (selected ? "▶ " : "  ") + id;
				this.addRenderableWidget(
					Button.builder(Component.literal(label), b -> this.pickChannel(id))
						.bounds(x, rowY, width, 20)
						.build()
				);
				rowY += rowH;
			}
		}

		int footerY = this.height - 28;
		int bw = width / 2 - 4;
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.save"), button -> this.save())
				.bounds(x, footerY, bw, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_DONE, button -> this.save())
				.bounds(x + bw + 8, footerY, bw, 20)
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
			this.cycleIndex = Math.floorMod(this.cycleIndex + delta, this.availableChannels.size());
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
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (!this.availableChannels.isEmpty()) {
			if (scrollY > 0) {
				this.listScroll = Math.max(0, this.listScroll - 1);
			} else if (scrollY < 0) {
				this.listScroll++;
			}
			this.rebuildWidgets();
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int width = contentWidth();
		int x = contentLeft();
		int panelY = 40;
		int panelBottom = this.height - 36;
		graphics.fill(x - 10, panelY - 2, x + width + 10, panelBottom + 2, PANEL_EDGE);
		graphics.fill(x - 8, panelY, x + width + 8, panelBottom, PANEL);

		if (!this.availableChannels.isEmpty() && !this.channel.isEmpty()) {
			// highlight is approximate; buttons sit on top after super
			int listY = 64 + 28 + 28;
			int idx = this.availableChannels.indexOf(this.channel);
			if (idx >= this.listScroll) {
				int row = idx - this.listScroll;
				int hy = listY + row * 22;
				if (hy + 20 < panelBottom - 4) {
					graphics.fill(x - 1, hy - 1, x + width + 1, hy + 21, SELECTED_BG);
				}
			}
		}

		super.extractRenderState(graphics, mouseX, mouseY, a);

		graphics.centeredText(this.font, this.title, this.width / 2, 16, TEXT);
		graphics.centeredText(
			this.font,
			Component.translatable("gui.codeworks.breaker_channel_hint"),
			this.width / 2,
			36,
			MUTED
		);
		if (!this.availableChannels.isEmpty()) {
			graphics.centeredText(
				this.font,
				Component.translatable("gui.codeworks.breaker_known_count", this.availableChannels.size()),
				this.width / 2,
				52,
				ACCENT
			);
		} else {
			graphics.centeredText(
				this.font,
				Component.translatable("gui.codeworks.breaker_no_known"),
				this.width / 2,
				52,
				MUTED
			);
		}
		if (!this.channel.isEmpty()) {
			graphics.centeredText(
				this.font,
				Component.translatable("gui.codeworks.selected_channel", ChannelIds.sanitize(this.channel)),
				this.width / 2,
				this.height - 42,
				OK
			);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
