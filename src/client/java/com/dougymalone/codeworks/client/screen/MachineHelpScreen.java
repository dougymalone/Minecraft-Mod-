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

public class MachineHelpScreen extends Screen {
	private final Screen parent;
	private List<FormattedCharSequence> lines = List.of();
	private int scroll;

	public MachineHelpScreen(Screen parent) {
		super(Component.translatable("gui.codeworks.help_title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.scroll = 0;
		this.lines = buildLines();
		int buttonY = this.height - 28;
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.help_scroll_up"), button -> this.scroll = Math.max(0, this.scroll - 4))
				.bounds(this.width / 2 - 154, buttonY, 70, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.help_scroll_down"), button -> this.scroll += 4)
				.bounds(this.width / 2 - 80, buttonY, 70, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(this.width / 2 + 4, buttonY, 150, 20)
				.build()
		);
	}

	/** MC 26.3 GuiGraphicsExtractor skips text when ARGB.alpha(color)==0; RGB-only 0xRRGGBB is invisible. */
	private static final int TEXT_WHITE = 0xFFFFFFFF;
	private static final int COLOR_SIG = 0xFF55FF55;
	private static final int COLOR_BUILTIN = 0xFF66FFCC;
	private static final int COLOR_SETTING = 0xFFFFCC66;
	private static final int COLOR_DESC = 0xFFC0C0C0;
	private static final int COLOR_EXAMPLE = 0xFF88CCFF;
	private static final int COLOR_NOTE = 0xFFE0E0E0;

	private List<FormattedCharSequence> buildLines() {
		List<FormattedCharSequence> out = new ArrayList<>();
		int maxWidth = Math.min(340, this.width - 40);

		out.addAll(this.font.split(Component.translatable("gui.codeworks.help_api_header"), maxWidth));
		out.add(FormattedCharSequence.EMPTY);
		for (ScriptHelp.ApiEntry entry : ScriptHelp.HOST_API) {
			Component sig = Component.literal(entry.signature() + "  →  " + entry.returns())
				.withStyle(style -> style.withColor(COLOR_SIG));
			out.addAll(this.font.split(sig, maxWidth));
			out.addAll(this.font.split(Component.literal("  " + entry.description()).withStyle(style -> style.withColor(COLOR_DESC)), maxWidth));
			out.addAll(this.font.split(Component.literal("  ex: " + entry.example()).withStyle(style -> style.withColor(COLOR_EXAMPLE)), maxWidth));
			out.add(FormattedCharSequence.EMPTY);
		}

		out.addAll(this.font.split(Component.translatable("gui.codeworks.help_builtins_header"), maxWidth));
		out.add(FormattedCharSequence.EMPTY);
		for (ScriptHelp.ApiEntry entry : ScriptHelp.BUILTINS) {
			Component sig = Component.literal(entry.signature() + "  →  " + entry.returns())
				.withStyle(style -> style.withColor(COLOR_BUILTIN));
			out.addAll(this.font.split(sig, maxWidth));
			out.addAll(this.font.split(Component.literal("  " + entry.description()).withStyle(style -> style.withColor(COLOR_DESC)), maxWidth));
			out.addAll(this.font.split(Component.literal("  ex: " + entry.example()).withStyle(style -> style.withColor(COLOR_EXAMPLE)), maxWidth));
			out.add(FormattedCharSequence.EMPTY);
		}

		out.addAll(this.font.split(Component.translatable("gui.codeworks.help_settings_header"), maxWidth));
		out.add(FormattedCharSequence.EMPTY);
		for (ScriptHelp.SettingEntry setting : ScriptHelp.SETTINGS) {
			Component name = Component.literal(setting.name() + "  (" + setting.type() + ")")
				.withStyle(style -> style.withColor(COLOR_SETTING));
			out.addAll(this.font.split(name, maxWidth));
			out.addAll(this.font.split(Component.literal("  " + setting.description()).withStyle(style -> style.withColor(COLOR_DESC)), maxWidth));
			out.addAll(this.font.split(Component.literal("  ex: " + setting.example()).withStyle(style -> style.withColor(COLOR_EXAMPLE)), maxWidth));
			out.add(FormattedCharSequence.EMPTY);
		}

		out.addAll(this.font.split(Component.translatable("gui.codeworks.help_lang_header"), maxWidth));
		out.add(FormattedCharSequence.EMPTY);
		for (String note : ScriptHelp.LANGUAGE_NOTES) {
			out.addAll(this.font.split(Component.literal("• " + note).withStyle(style -> style.withColor(COLOR_NOTE)), maxWidth));
		}
		return out;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (scrollY > 0) {
			this.scroll = Math.max(0, this.scroll - 2);
		} else if (scrollY < 0) {
			this.scroll += 2;
		}
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 10, TEXT_WHITE);

		int panelWidth = Math.min(360, this.width - 30);
		int panelX = (this.width - panelWidth) / 2;
		int panelY = 24;
		int panelBottom = this.height - 36;
		graphics.fill(panelX - 4, panelY - 4, panelX + panelWidth + 4, panelBottom, 0xC0101010);

		int lineHeight = this.font.lineHeight + 1;
		int maxVisible = Math.max(1, (panelBottom - panelY - 2) / lineHeight);
		int maxScroll = Math.max(0, this.lines.size() - maxVisible);
		if (this.scroll > maxScroll) {
			this.scroll = maxScroll;
		}

		int y = panelY;
		for (int i = this.scroll; i < this.lines.size(); i++) {
			if (y + lineHeight > panelBottom - 2) {
				break;
			}
			graphics.text(this.font, this.lines.get(i), panelX, y, TEXT_WHITE);
			y += lineHeight;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
