package com.dougymalone.codeworks.client.screen;

import com.dougymalone.codeworks.block.entity.BreakerBlockEntity;
import com.dougymalone.codeworks.network.SaveBreakerScriptPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class BreakerEditScreen extends Screen {
	private final BlockPos pos;
	private String script;
	private final String status;
	private MultiLineEditBox editor;

	public BreakerEditScreen(BlockPos pos, String initialScript, String status) {
		super(Component.translatable("gui.codeworks.breaker_editor"));
		this.pos = pos;
		this.script = initialScript == null ? "" : initialScript;
		this.status = status == null ? "" : status;
	}

	@Override
	protected void init() {
		int editorWidth = Math.min(360, this.width - 40);
		int editorHeight = Math.min(140, this.height - 130);
		int x = (this.width - editorWidth) / 2;
		int y = 40;

		this.editor = MultiLineEditBox.builder()
			.setX(x)
			.setY(y)
			.setPlaceholder(Component.translatable("gui.codeworks.breaker_placeholder"))
			.build(this.font, editorWidth, editorHeight, Component.translatable("gui.codeworks.breaker_editor"));
		this.editor.setCharacterLimit(BreakerBlockEntity.MAX_SCRIPT_LENGTH);
		this.editor.setValue(this.script);
		this.addRenderableWidget(this.editor);

		int buttonY = y + editorHeight + 24;
		int left = this.width / 2 - 154;
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.save"), button -> this.save())
				.bounds(left, buttonY, 100, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.help"), button -> this.openHelp())
				.bounds(left + 104, buttonY, 100, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(left + 208, buttonY, 100, 20)
				.build()
		);
	}

	private void openHelp() {
		this.script = this.editor.getValue();
		this.minecraft.setScreenAndShow(new BreakerHelpScreen(this));
	}

	private void save() {
		this.script = this.editor.getValue();
		ClientPlayNetworking.send(new SaveBreakerScriptPayload(this.pos, this.script));
		this.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		// MC 26.3 skips text when ARGB.alpha(color)==0 — use opaque ARGB, not RGB-only.
		graphics.centeredText(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.literal(this.status), this.width / 2, this.height - 28, 0xFFA0A0A0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
