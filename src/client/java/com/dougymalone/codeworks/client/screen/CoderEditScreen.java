package com.dougymalone.codeworks.client.screen;

import com.dougymalone.codeworks.block.entity.CoderBlockEntity;
import com.dougymalone.codeworks.network.SaveCoderScriptPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class CoderEditScreen extends Screen {
	private final BlockPos pos;
	private final String initialScript;
	private final String status;
	private MultiLineEditBox editor;

	public CoderEditScreen(BlockPos pos, String initialScript, String status) {
		super(Component.translatable("gui.codeworks.coder_editor"));
		this.pos = pos;
		this.initialScript = initialScript;
		this.status = status == null ? "" : status;
	}

	@Override
	protected void init() {
		int editorWidth = Math.min(360, this.width - 40);
		int editorHeight = Math.min(140, this.height - 120);
		int x = (this.width - editorWidth) / 2;
		int y = 40;

		this.editor = MultiLineEditBox.builder()
			.setX(x)
			.setY(y)
			.setPlaceholder(Component.translatable("gui.codeworks.coder_placeholder"))
			.build(this.font, editorWidth, editorHeight, Component.translatable("gui.codeworks.coder_editor"));
		this.editor.setCharacterLimit(CoderBlockEntity.MAX_SCRIPT_LENGTH);
		this.editor.setValue(this.initialScript);
		this.addRenderableWidget(this.editor);

		int buttonY = y + editorHeight + 28;
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.save"), button -> this.save())
				.bounds(this.width / 2 - 154, buttonY, 150, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(this.width / 2 + 4, buttonY, 150, 20)
				.build()
		);
	}

	private void save() {
		ClientPlayNetworking.send(new SaveCoderScriptPayload(this.pos, this.editor.getValue()));
		this.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
		graphics.centeredText(this.font, Component.literal(this.status), this.width / 2, this.height - 30, 0xA0A0A0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
