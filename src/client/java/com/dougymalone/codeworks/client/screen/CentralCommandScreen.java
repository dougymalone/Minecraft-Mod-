package com.dougymalone.codeworks.client.screen;

import com.dougymalone.codeworks.block.entity.CentralCommandBlockEntity;
import com.dougymalone.codeworks.channel.ChannelIds;
import com.dougymalone.codeworks.network.SaveCentralCommandPayload;
import com.dougymalone.codeworks.script.ScriptHelp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Rich Central Command UI: Channels / Breaker / Placer(stub) / Script / Help.
 * Opaque ARGB text for MC 26.3.
 */
public class CentralCommandScreen extends Screen {
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFFA0A0A0;
	private static final int ACCENT = 0xFF66CCFF;
	private static final int PANEL = 0xC0101010;

	private enum Tab {
		CHANNELS,
		BREAKER,
		PLACER,
		SCRIPT,
		HELP
	}

	private final BlockPos pos;
	private final String status;
	private final List<String> channels = new ArrayList<>();
	private final List<Integer> breakerCounts = new ArrayList<>();
	private String script;
	private Tab tab = Tab.CHANNELS;

	private EditBox channelInput;
	private MultiLineEditBox scriptEditor;
	private List<FormattedCharSequence> helpLines = List.of();
	private int helpScroll;
	private final List<Button> dynamicButtons = new ArrayList<>();

	public CentralCommandScreen(BlockPos pos, String script, String status, String channelsCsv, String countsCsv) {
		super(Component.translatable("gui.codeworks.central_command_title"));
		this.pos = pos;
		this.script = script == null ? "" : script;
		this.status = status == null ? "" : status;
		if (channelsCsv != null && !channelsCsv.isBlank()) {
			for (String part : channelsCsv.split("\n")) {
				String id = ChannelIds.sanitize(part);
				if (!id.isEmpty() && !this.channels.contains(id)) {
					this.channels.add(id);
				}
			}
		}
		if (countsCsv != null && !countsCsv.isBlank()) {
			String[] parts = countsCsv.split("\n", -1);
			for (int i = 0; i < this.channels.size(); i++) {
				int count = 0;
				if (i < parts.length) {
					try {
						count = Integer.parseInt(parts[i].trim());
					} catch (NumberFormatException ignored) {
						count = 0;
					}
				}
				this.breakerCounts.add(count);
			}
		}
		while (this.breakerCounts.size() < this.channels.size()) {
			this.breakerCounts.add(0);
		}
	}

	@Override
	protected void init() {
		this.clearWidgets();
		this.dynamicButtons.clear();
		this.channelInput = null;
		this.scriptEditor = null;

		int tabY = 28;
		int tabW = Math.min(70, (this.width - 40) / 5);
		int tabX = (this.width - tabW * 5) / 2;
		addTab(Tab.CHANNELS, "gui.codeworks.tab_channels", tabX, tabY, tabW);
		addTab(Tab.BREAKER, "gui.codeworks.tab_breaker", tabX + tabW, tabY, tabW);
		addTab(Tab.PLACER, "gui.codeworks.tab_placer", tabX + tabW * 2, tabY, tabW);
		addTab(Tab.SCRIPT, "gui.codeworks.tab_script", tabX + tabW * 3, tabY, tabW);
		addTab(Tab.HELP, "gui.codeworks.tab_help", tabX + tabW * 4, tabY, tabW);

		int contentTop = 56;
		int contentBottom = this.height - 36;
		switch (this.tab) {
			case CHANNELS -> initChannelsTab(contentTop, contentBottom);
			case BREAKER -> { /* drawn in render */ }
			case PLACER -> { /* stub drawn in render */ }
			case SCRIPT -> initScriptTab(contentTop, contentBottom);
			case HELP -> {
				this.helpLines = buildHelpLines();
				this.helpScroll = 0;
				this.addRenderableWidget(
					Button.builder(Component.translatable("gui.codeworks.help_scroll_up"), b -> this.helpScroll = Math.max(0, this.helpScroll - 4))
						.bounds(this.width / 2 - 110, contentBottom - 4, 70, 20)
						.build()
				);
				this.addRenderableWidget(
					Button.builder(Component.translatable("gui.codeworks.help_scroll_down"), b -> this.helpScroll += 4)
						.bounds(this.width / 2 - 30, contentBottom - 4, 70, 20)
						.build()
				);
			}
		}

		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.save"), b -> this.save())
				.bounds(this.width / 2 - 154, this.height - 28, 100, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
				.bounds(this.width / 2 - 50, this.height - 28, 100, 20)
				.build()
		);
	}

	private void addTab(Tab tab, String key, int x, int y, int w) {
		Button button = Button.builder(Component.translatable(key), b -> {
			this.captureScriptIfNeeded();
			this.tab = tab;
			this.rebuildWidgets();
		}).bounds(x, y, w - 2, 20).build();
		this.addRenderableWidget(button);
	}

	private void initChannelsTab(int top, int bottom) {
		int width = Math.min(320, this.width - 40);
		int x = (this.width - width) / 2;
		this.channelInput = new EditBox(this.font, x, top, width - 80, 20, Component.translatable("gui.codeworks.channel"));
		this.channelInput.setMaxLength(ChannelIds.MAX_LENGTH);
		this.channelInput.setHint(Component.translatable("gui.codeworks.channel_hint"));
		this.addRenderableWidget(this.channelInput);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.add_channel"), b -> this.addChannel())
				.bounds(x + width - 76, top, 76, 20)
				.build()
		);

		int y = top + 28;
		for (int i = 0; i < this.channels.size(); i++) {
			if (y + 22 > bottom - 8) {
				break;
			}
			final int index = i;
			String label = this.channels.get(i) + "  (" + this.breakerCounts.get(i) + " breakers)";
			Button remove = Button.builder(Component.literal("× " + label), b -> this.removeChannel(index))
				.bounds(x, y, width, 20)
				.build();
			this.addRenderableWidget(remove);
			this.dynamicButtons.add(remove);
			y += 22;
		}
	}

	private void initScriptTab(int top, int bottom) {
		int width = Math.min(360, this.width - 40);
		int height = Math.max(80, bottom - top - 8);
		int x = (this.width - width) / 2;
		this.scriptEditor = MultiLineEditBox.builder()
			.setX(x)
			.setY(top)
			.setPlaceholder(Component.translatable("gui.codeworks.script_placeholder"))
			.build(this.font, width, height, Component.translatable("gui.codeworks.tab_script"));
		this.scriptEditor.setCharacterLimit(CentralCommandBlockEntity.MAX_SCRIPT_LENGTH);
		this.scriptEditor.setValue(this.script);
		this.addRenderableWidget(this.scriptEditor);
	}

	private void addChannel() {
		if (this.channelInput == null) {
			return;
		}
		String id = ChannelIds.sanitize(this.channelInput.getValue());
		if (id.isEmpty() || this.channels.contains(id) || this.channels.size() >= CentralCommandBlockEntity.MAX_CHANNELS) {
			return;
		}
		this.channels.add(id);
		this.breakerCounts.add(0);
		this.channelInput.setValue("");
		this.rebuildWidgets();
	}

	private void removeChannel(int index) {
		if (index < 0 || index >= this.channels.size()) {
			return;
		}
		this.channels.remove(index);
		if (index < this.breakerCounts.size()) {
			this.breakerCounts.remove(index);
		}
		this.rebuildWidgets();
	}

	private void captureScriptIfNeeded() {
		if (this.scriptEditor != null) {
			this.script = this.scriptEditor.getValue();
		}
	}

	private void save() {
		captureScriptIfNeeded();
		ClientPlayNetworking.send(new SaveCentralCommandPayload(
			this.pos,
			this.script,
			String.join("\n", this.channels)
		));
		this.onClose();
	}

	private List<FormattedCharSequence> buildHelpLines() {
		List<FormattedCharSequence> out = new ArrayList<>();
		int max = Math.min(340, this.width - 48);
		out.addAll(this.font.split(Component.literal("Central Command model").withStyle(s -> s.withColor(ACCENT)), max));
		out.add(FormattedCharSequence.EMPTY);
		for (String note : ScriptHelp.NOTES) {
			out.addAll(this.font.split(Component.literal("• " + note).withStyle(s -> s.withColor(0xFFE0E0E0)), max));
		}
		out.add(FormattedCharSequence.EMPTY);
		out.addAll(this.font.split(Component.literal("Host API").withStyle(s -> s.withColor(ACCENT)), max));
		out.add(FormattedCharSequence.EMPTY);
		for (ScriptHelp.ApiEntry entry : ScriptHelp.HOST_API) {
			out.addAll(this.font.split(Component.literal(entry.signature() + " → " + entry.returns()).withStyle(s -> s.withColor(0xFF55FF55)), max));
			out.addAll(this.font.split(Component.literal("  " + entry.description()).withStyle(s -> s.withColor(0xFFC0C0C0)), max));
			out.addAll(this.font.split(Component.literal("  ex: " + entry.example()).withStyle(s -> s.withColor(0xFF88CCFF)), max));
			out.add(FormattedCharSequence.EMPTY);
		}
		out.addAll(this.font.split(Component.literal("Math builtins").withStyle(s -> s.withColor(ACCENT)), max));
		out.add(FormattedCharSequence.EMPTY);
		for (ScriptHelp.ApiEntry entry : ScriptHelp.BUILTINS) {
			out.addAll(this.font.split(Component.literal(entry.signature() + " — " + entry.description()).withStyle(s -> s.withColor(0xFF66FFCC)), max));
		}
		return out;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.tab == Tab.HELP) {
			if (scrollY > 0) {
				this.helpScroll = Math.max(0, this.helpScroll - 2);
			} else if (scrollY < 0) {
				this.helpScroll += 2;
			}
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 10, TEXT);
		graphics.centeredText(this.font, Component.literal(this.status), this.width / 2, this.height - 42, MUTED);

		int panelW = Math.min(380, this.width - 30);
		int panelX = (this.width - panelW) / 2;
		int panelY = 52;
		int panelBottom = this.height - 52;
		graphics.fill(panelX - 4, panelY - 4, panelX + panelW + 4, panelBottom, PANEL);

		int textX = panelX + 6;
		int y = panelY + 4;
		switch (this.tab) {
			case CHANNELS -> {
				graphics.text(this.font, Component.translatable("gui.codeworks.channels_blurb"), textX, y, MUTED);
			}
			case BREAKER -> {
				graphics.text(this.font, Component.translatable("gui.codeworks.breaker_tab_title"), textX, y, ACCENT);
				y += 14;
				for (String line : Arrays.asList(
					"Breakers have no scripts — only a channel selector.",
					"Set the same channel id you added in Channels.",
					"Command scripts call Break(\"channel\") to harvest.",
					""
				)) {
					graphics.text(this.font, Component.literal(line), textX, y, MUTED);
					y += 12;
				}
				for (int i = 0; i < this.channels.size(); i++) {
					String line = this.channels.get(i) + ": " + this.breakerCounts.get(i) + " breaker(s) loaded";
					graphics.text(this.font, Component.literal(line), textX, y, TEXT);
					y += 12;
					if (y > panelBottom - 10) {
						break;
					}
				}
			}
			case PLACER -> {
				graphics.text(this.font, Component.translatable("gui.codeworks.placer_tab_title"), textX, y, ACCENT);
				y += 14;
				graphics.text(this.font, Component.translatable("gui.codeworks.placer_tab_stub"), textX, y, MUTED);
			}
			case SCRIPT -> {
				// editor widgets handle content
			}
			case HELP -> {
				int lineHeight = this.font.lineHeight + 1;
				int maxVisible = Math.max(1, (panelBottom - panelY - 28) / lineHeight);
				int maxScroll = Math.max(0, this.helpLines.size() - maxVisible);
				if (this.helpScroll > maxScroll) {
					this.helpScroll = maxScroll;
				}
				int hy = panelY + 4;
				for (int i = this.helpScroll; i < this.helpLines.size(); i++) {
					if (hy + lineHeight > panelBottom - 24) {
						break;
					}
					graphics.text(this.font, this.helpLines.get(i), textX, hy, TEXT);
					hy += lineHeight;
				}
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
