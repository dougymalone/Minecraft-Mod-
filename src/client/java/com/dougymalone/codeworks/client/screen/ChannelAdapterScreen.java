package com.dougymalone.codeworks.client.screen;

import com.dougymalone.codeworks.block.entity.ChannelAdapterBlockEntity;
import com.dougymalone.codeworks.network.SaveChannelAdapterPayload;
import com.dougymalone.codeworks.wireless.WirelessBus;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Simple channel / label editor for Channel Adapters (opaque ARGB text for MC 26.3). */
public class ChannelAdapterScreen extends Screen {
	private static final int TEXT_WHITE = 0xFFFFFFFF;
	private static final int TEXT_MUTED = 0xFFA0A0A0;

	private final BlockPos pos;
	private final String status;
	private EditBox channelBox;
	private EditBox nameBox;
	private String channel;
	private String displayName;

	public ChannelAdapterScreen(BlockPos pos, String channel, String displayName, String status) {
		super(Component.translatable("gui.codeworks.channel_adapter_title"));
		this.pos = pos;
		this.channel = channel == null ? "" : channel;
		this.displayName = displayName == null ? "" : displayName;
		this.status = status == null ? "" : status;
	}

	@Override
	protected void init() {
		int width = Math.min(280, this.width - 40);
		int x = (this.width - width) / 2;
		int y = 50;

		this.channelBox = new EditBox(this.font, x, y, width, 20, Component.translatable("gui.codeworks.channel_adapter_channel"));
		this.channelBox.setMaxLength(WirelessBus.MAX_CHANNEL_LENGTH);
		this.channelBox.setValue(this.channel);
		this.channelBox.setHint(Component.translatable("gui.codeworks.channel_adapter_channel_hint"));
		this.addRenderableWidget(this.channelBox);

		this.nameBox = new EditBox(this.font, x, y + 40, width, 20, Component.translatable("gui.codeworks.channel_adapter_name"));
		this.nameBox.setMaxLength(ChannelAdapterBlockEntity.MAX_NAME_LENGTH);
		this.nameBox.setValue(this.displayName);
		this.nameBox.setHint(Component.translatable("gui.codeworks.channel_adapter_name_hint"));
		this.addRenderableWidget(this.nameBox);

		int buttonY = y + 80;
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.save"), button -> this.save())
				.bounds(x, buttonY, width / 2 - 4, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(x + width / 2 + 4, buttonY, width / 2 - 4, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.help"), button -> this.openHelp())
				.bounds(x, buttonY + 28, width, 20)
				.build()
		);
	}

	private void openHelp() {
		this.channel = this.channelBox.getValue();
		this.displayName = this.nameBox.getValue();
		this.minecraft.setScreenAndShow(new MachineHelpScreen(this));
	}

	private void save() {
		this.channel = this.channelBox.getValue();
		this.displayName = this.nameBox.getValue();
		ClientPlayNetworking.send(new SaveChannelAdapterPayload(this.pos, this.channel, this.displayName));
		this.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 18, TEXT_WHITE);
		int width = Math.min(280, this.width - 40);
		int x = (this.width - width) / 2;
		graphics.text(this.font, Component.translatable("gui.codeworks.channel_adapter_channel"), x, 38, TEXT_MUTED);
		graphics.text(this.font, Component.translatable("gui.codeworks.channel_adapter_name"), x, 78, TEXT_MUTED);
		graphics.centeredText(this.font, Component.literal(this.status), this.width / 2, this.height - 28, TEXT_MUTED);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
