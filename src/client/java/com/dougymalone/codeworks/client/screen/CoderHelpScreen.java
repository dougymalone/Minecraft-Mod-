package com.dougymalone.codeworks.client.screen;

import com.dougymalone.codeworks.script.ScriptHelp;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class CoderHelpScreen extends Screen {
	private final Screen parent;
	private List<FormattedCharSequence> lines = List.of();

	public CoderHelpScreen(Screen parent) {
		super(Component.translatable("gui.codeworks.help_title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.lines = buildLines();
		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(this.width / 2 - 100, this.height - 28, 200, 20)
				.build()
		);
	}

	private List<FormattedCharSequence> buildLines() {
		List<FormattedCharSequence> out = new ArrayList<>();
		int maxWidth = Math.min(340, this.width - 40);

		out.addAll(this.font.split(Component.translatable("gui.codeworks.help_api_header"), maxWidth));
		out.add(FormattedCharSequence.EMPTY);
		for (ScriptHelp.ApiEntry entry : ScriptHelp.HOST_API) {
			Component sig = Component.literal(entry.signature() + "  →  " + entry.returns())
				.withStyle(style -> style.withColor(0x55FF55));
			out.addAll(this.font.split(sig, maxWidth));
			out.addAll(this.font.split(Component.literal("  " + entry.description()).withStyle(style -> style.withColor(0xC0C0C0)), maxWidth));
			out.add(FormattedCharSequence.EMPTY);
		}

		out.addAll(this.font.split(Component.translatable("gui.codeworks.help_lang_header"), maxWidth));
		out.add(FormattedCharSequence.EMPTY);
		for (String note : ScriptHelp.LANGUAGE_NOTES) {
			out.addAll(this.font.split(Component.literal("• " + note).withStyle(style -> style.withColor(0xE0E0E0)), maxWidth));
		}
		return out;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

		int panelWidth = Math.min(360, this.width - 30);
		int panelX = (this.width - panelWidth) / 2;
		int panelY = 28;
		int panelBottom = this.height - 36;
		graphics.fill(panelX - 4, panelY - 4, panelX + panelWidth + 4, panelBottom, 0xC0101010);

		int y = panelY;
		int lineHeight = this.font.lineHeight + 1;
		for (FormattedCharSequence line : this.lines) {
			if (y + lineHeight > panelBottom - 2) {
				graphics.text(this.font, Component.translatable("gui.codeworks.help_truncated"), panelX, y, 0xFFAA00);
				break;
			}
			graphics.text(this.font, line, panelX, y, 0xFFFFFF);
			y += lineHeight;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
