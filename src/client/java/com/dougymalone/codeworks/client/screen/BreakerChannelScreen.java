package com.dougymalone.codeworks.client.screen;

import com.dougymalone.codeworks.channel.ChannelIds;
import com.dougymalone.codeworks.network.SaveBreakerChannelPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Breaker's only UI: pick the channel that Central Command scripts target. */
public class BreakerChannelScreen extends Screen {
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFFA0A0A0;

	private final BlockPos pos;
	private EditBox channelBox;
	private String channel;

	public BreakerChannelScreen(BlockPos pos, String channel) {
		super(Component.translatable("gui.codeworks.breaker_channel_title"));
		this.pos = pos;
		this.channel = channel == null ? "" : channel;
	}

	@Override
	protected void init() {
		int width = Math.min(240, this.width - 40);
		int x = (this.width - width) / 2;
		int y = 70;
		this.channelBox = new EditBox(this.font, x, y, width, 20, Component.translatable("gui.codeworks.channel"));
		this.channelBox.setMaxLength(ChannelIds.MAX_LENGTH);
		this.channelBox.setValue(this.channel);
		this.channelBox.setHint(Component.translatable("gui.codeworks.channel_hint"));
		this.addRenderableWidget(this.channelBox);

		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.save"), button -> this.save())
				.bounds(x, y + 36, width / 2 - 4, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(x + width / 2 + 4, y + 36, width / 2 - 4, 20)
				.build()
		);
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
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
