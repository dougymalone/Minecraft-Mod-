package com.dougymalone.codeworks.client.screen;

import com.dougymalone.codeworks.block.entity.CentralCommandBlockEntity;
import com.dougymalone.codeworks.channel.ChannelIds;
import com.dougymalone.codeworks.network.SaveCentralCommandPayload;
import com.dougymalone.codeworks.script.ScriptHelp;
import java.util.ArrayList;
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

/**
 * Central Command UI. Panel chrome is drawn BEFORE widgets so channel rows are not covered.
 * All text colors are opaque ARGB — MC 26.3 skips drawing when alpha == 0.
 */
public class CentralCommandScreen extends Screen {
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFFA0A0A0;
	private static final int ACCENT = 0xFF66CCFF;
	private static final int OK = 0xFF55FF55;
	private static final int PANEL = 0xE0101418;
	private static final int PANEL_EDGE = 0xFF2A3340;
	private static final int SELECTED_BG = 0xFF1A3A4A;
	private static final int ROW_BG = 0xFF161A20;

	private enum Tab {
		CHANNELS,
		BREAKER,
		PLACER,
		SCRIPT,
		HELP
	}

	private record HelpLine(String text, int color) {
	}

	private final BlockPos pos;
	private final List<String> channels = new ArrayList<>();
	private final List<Integer> breakerCounts = new ArrayList<>();
	private String script;
	private String scriptStatus;
	private String uiFeedback = "";
	private String selectedChannel = "";
	private Tab tab = Tab.CHANNELS;
	private int listScroll;
	private int helpScroll;

	private EditBox channelInput;
	private MultiLineEditBox scriptEditor;
	private List<HelpLine> helpLines = List.of();

	public CentralCommandScreen(BlockPos pos, String script, String status, String channelsCsv, String countsCsv) {
		super(Component.translatable("gui.codeworks.central_command_title"));
		this.pos = pos;
		this.script = script == null ? "" : script;
		this.scriptStatus = status == null ? "" : status;
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
		if (!this.channels.isEmpty()) {
			this.selectedChannel = this.channels.get(0);
			this.uiFeedback = Component.translatable("gui.codeworks.channels_loaded", this.channels.size()).getString();
		} else {
			this.uiFeedback = Component.translatable("gui.codeworks.channels_empty_hint").getString();
		}
	}

	private int contentLeft() {
		return (this.width - contentWidth()) / 2;
	}

	private int contentWidth() {
		return Math.min(360, Math.max(240, this.width - 40));
	}

	private int contentTop() {
		return 50;
	}

	private int contentBottom() {
		return this.height - 48;
	}

	@Override
	protected void init() {
		this.clearWidgets();
		this.channelInput = null;
		this.scriptEditor = null;

		int tabW = Math.min(72, Math.max(52, (this.width - 36) / 5));
		int tabX = (this.width - tabW * 5) / 2;
		int tabY = 24;
		addTab(Tab.CHANNELS, "gui.codeworks.tab_channels", tabX, tabY, tabW);
		addTab(Tab.BREAKER, "gui.codeworks.tab_breaker", tabX + tabW, tabY, tabW);
		addTab(Tab.PLACER, "gui.codeworks.tab_placer", tabX + tabW * 2, tabY, tabW);
		addTab(Tab.SCRIPT, "gui.codeworks.tab_script", tabX + tabW * 3, tabY, tabW);
		addTab(Tab.HELP, "gui.codeworks.tab_help", tabX + tabW * 4, tabY, tabW);

		int top = contentTop();
		int bottom = contentBottom();
		switch (this.tab) {
			case CHANNELS -> initChannelsTab(top, bottom);
			case BREAKER -> initBreakerTab(top, bottom);
			case PLACER -> { /* copy drawn in extractRenderState */ }
			case SCRIPT -> initScriptTab(top, bottom);
			case HELP -> initHelpTab(bottom);
		}

		int footerY = this.height - 26;
		int bw = Math.min(100, (contentWidth() - 8) / 2);
		int bx = this.width / 2 - bw - 4;
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.save"), b -> this.saveOnly())
				.bounds(bx, footerY, bw, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_DONE, b -> this.saveAndClose())
				.bounds(bx + bw + 8, footerY, bw, 20)
				.build()
		);
	}

	private void addTab(Tab tab, String key, int x, int y, int w) {
		boolean active = this.tab == tab;
		Component label = active
			? Component.literal("[").append(Component.translatable(key)).append("]")
			: Component.translatable(key);
		Button button = Button.builder(label, b -> {
			this.captureScriptIfNeeded();
			this.tab = tab;
			this.listScroll = 0;
			this.helpScroll = 0;
			this.rebuildWidgets();
		}).bounds(x, y, w - 2, 20).build();
		button.active = !active;
		this.addRenderableWidget(button);
	}

	private void initChannelsTab(int top, int bottom) {
		int x = contentLeft();
		int width = contentWidth();
		// Reserve header lines (blurb + selected) drawn in extractRenderState
		int inputY = top + 28;
		this.channelInput = new EditBox(this.font, x, inputY, width - 84, 20, Component.translatable("gui.codeworks.channel"));
		this.channelInput.setMaxLength(ChannelIds.MAX_LENGTH);
		this.channelInput.setHint(Component.translatable("gui.codeworks.channel_hint"));
		this.channelInput.setEditable(true);
		this.addRenderableWidget(this.channelInput);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.add_channel"), b -> this.addChannel())
				.bounds(x + width - 80, inputY, 80, 20)
				.build()
		);

		int listTop = inputY + 26;
		int listBottom = bottom - 4;
		int rowH = 22;
		int visible = Math.max(1, (listBottom - listTop) / rowH);
		int maxScroll = Math.max(0, this.channels.size() - visible);
		if (this.listScroll > maxScroll) {
			this.listScroll = maxScroll;
		}

		if (this.channels.size() > visible) {
			this.addRenderableWidget(
				Button.builder(Component.literal("▲"), b -> this.listScroll = Math.max(0, this.listScroll - 1))
					.bounds(x + width - 20, listTop, 20, 20)
					.build()
			);
			this.addRenderableWidget(
				Button.builder(Component.literal("▼"), b -> this.listScroll = Math.min(maxScroll, this.listScroll + 1))
					.bounds(x + width - 20, listBottom - 20, 20, 20)
					.build()
			);
		}

		int rowWidth = this.channels.size() > visible ? width - 24 : width;
		int y = listTop;
		for (int i = this.listScroll; i < this.channels.size(); i++) {
			if (y + rowH > listBottom) {
				break;
			}
			final int index = i;
			String id = this.channels.get(i);
			boolean selected = id.equals(this.selectedChannel);
			int count = i < this.breakerCounts.size() ? this.breakerCounts.get(i) : 0;
			String label = (selected ? "▶ " : "  ") + id + "   · " + count + " brk";
			int selectW = rowWidth - 28;
			this.addRenderableWidget(
				Button.builder(Component.literal(label), b -> this.selectChannel(index))
					.bounds(x, y, selectW, 20)
					.build()
			);
			this.addRenderableWidget(
				Button.builder(Component.literal("×"), b -> this.removeChannel(index))
					.bounds(x + selectW + 4, y, 24, 20)
					.build()
			);
			y += rowH;
		}
	}

	private void initBreakerTab(int top, int bottom) {
		int x = contentLeft();
		int width = contentWidth();
		int y = top + 40;
		int rowH = 22;
		if (this.channels.isEmpty()) {
			return;
		}
		int visible = Math.max(1, (bottom - y - 4) / rowH);
		int maxScroll = Math.max(0, this.channels.size() - visible);
		if (this.listScroll > maxScroll) {
			this.listScroll = maxScroll;
		}
		for (int i = this.listScroll; i < this.channels.size(); i++) {
			if (y + rowH > bottom) {
				break;
			}
			final int index = i;
			String id = this.channels.get(i);
			boolean selected = id.equals(this.selectedChannel);
			int count = i < this.breakerCounts.size() ? this.breakerCounts.get(i) : 0;
			String label = (selected ? "▶ " : "  ") + id + "  (" + count + " breakers)";
			this.addRenderableWidget(
				Button.builder(Component.literal(label), b -> this.selectChannel(index))
					.bounds(x, y, width, 20)
					.build()
			);
			y += rowH;
		}
	}

	private void initScriptTab(int top, int bottom) {
		int width = contentWidth();
		int height = Math.max(80, bottom - top - 4);
		int x = contentLeft();
		this.scriptEditor = MultiLineEditBox.builder()
			.setX(x)
			.setY(top)
			.setPlaceholder(Component.translatable("gui.codeworks.script_placeholder"))
			.build(this.font, width, height, Component.translatable("gui.codeworks.tab_script"));
		this.scriptEditor.setCharacterLimit(CentralCommandBlockEntity.MAX_SCRIPT_LENGTH);
		this.scriptEditor.setValue(this.script);
		this.addRenderableWidget(this.scriptEditor);
	}

	private void initHelpTab(int bottom) {
		this.helpLines = buildHelpLines();
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.help_scroll_up"), b -> this.helpScroll = Math.max(0, this.helpScroll - 4))
				.bounds(this.width / 2 - 110, bottom - 2, 70, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.help_scroll_down"), b -> this.helpScroll += 4)
				.bounds(this.width / 2 - 30, bottom - 2, 70, 20)
				.build()
		);
	}

	private void addChannel() {
		if (this.channelInput == null) {
			return;
		}
		String id = ChannelIds.sanitize(this.channelInput.getValue());
		if (id.isEmpty()) {
			this.uiFeedback = Component.translatable("gui.codeworks.channel_invalid").getString();
			return;
		}
		if (this.channels.contains(id)) {
			this.selectedChannel = id;
			this.uiFeedback = Component.translatable("gui.codeworks.channel_already", id).getString();
			this.channelInput.setValue("");
			this.rebuildWidgets();
			return;
		}
		if (this.channels.size() >= CentralCommandBlockEntity.MAX_CHANNELS) {
			this.uiFeedback = Component.translatable("gui.codeworks.channel_limit").getString();
			return;
		}
		this.channels.add(id);
		this.breakerCounts.add(0);
		this.selectedChannel = id;
		this.channelInput.setValue("");
		this.uiFeedback = Component.translatable("gui.codeworks.channel_added", id, this.channels.size()).getString();
		// Keep new row visible
		this.listScroll = Math.max(0, this.channels.size() - 1);
		this.persistChannels();
		this.rebuildWidgets();
	}

	private void selectChannel(int index) {
		if (index < 0 || index >= this.channels.size()) {
			return;
		}
		this.selectedChannel = this.channels.get(index);
		this.uiFeedback = Component.translatable("gui.codeworks.selected_channel", this.selectedChannel).getString();
		this.rebuildWidgets();
	}

	private void removeChannel(int index) {
		if (index < 0 || index >= this.channels.size()) {
			return;
		}
		String removed = this.channels.remove(index);
		if (index < this.breakerCounts.size()) {
			this.breakerCounts.remove(index);
		}
		if (removed.equals(this.selectedChannel)) {
			this.selectedChannel = this.channels.isEmpty() ? "" : this.channels.get(Math.min(index, this.channels.size() - 1));
		}
		this.uiFeedback = Component.translatable("gui.codeworks.channel_removed", removed, this.channels.size()).getString();
		this.persistChannels();
		this.rebuildWidgets();
	}

	private void captureScriptIfNeeded() {
		if (this.scriptEditor != null) {
			this.script = this.scriptEditor.getValue();
		}
	}

	private void persistChannels() {
		captureScriptIfNeeded();
		ClientPlayNetworking.send(new SaveCentralCommandPayload(
			this.pos,
			this.script,
			String.join("\n", this.channels)
		));
	}

	private void saveOnly() {
		persistChannels();
		this.uiFeedback = Component.translatable("gui.codeworks.saved_ok", this.channels.size()).getString();
	}

	private void saveAndClose() {
		persistChannels();
		this.onClose();
	}

	private List<HelpLine> buildHelpLines() {
		// Colors passed at draw time (opaque ARGB). Do NOT put colors in Style —
		// MC 26.3 FormattedCharSequence style RGB has alpha 0 and is skipped.
		List<HelpLine> out = new ArrayList<>();
		out.add(new HelpLine("Central Command model", ACCENT));
		out.add(new HelpLine("", TEXT));
		for (String note : ScriptHelp.NOTES) {
			out.add(new HelpLine("• " + note, 0xFFE0E0E0));
		}
		out.add(new HelpLine("", TEXT));
		out.add(new HelpLine("Host API", ACCENT));
		out.add(new HelpLine("", TEXT));
		for (ScriptHelp.ApiEntry entry : ScriptHelp.HOST_API) {
			out.add(new HelpLine(entry.signature() + " → " + entry.returns(), OK));
			out.add(new HelpLine("  " + entry.description(), 0xFFC0C0C0));
			out.add(new HelpLine("  ex: " + entry.example(), 0xFF88CCFF));
			out.add(new HelpLine("", TEXT));
		}
		out.add(new HelpLine("Math builtins", ACCENT));
		out.add(new HelpLine("", TEXT));
		for (ScriptHelp.ApiEntry entry : ScriptHelp.BUILTINS) {
			out.add(new HelpLine(entry.signature() + " — " + entry.description(), 0xFF66FFCC));
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
		if (this.tab == Tab.CHANNELS || this.tab == Tab.BREAKER) {
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
		// CRITICAL: draw panel chrome BEFORE widgets. Drawing fill after super covers
		// channel rows and makes the list look empty (Add/Save still barely readable).
		int panelW = contentWidth() + 16;
		int panelX = (this.width - panelW) / 2;
		int panelY = contentTop() - 6;
		int panelBottom = contentBottom() + 4;
		graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelBottom + 2, PANEL_EDGE);
		graphics.fill(panelX, panelY, panelX + panelW, panelBottom, PANEL);

		// Selected-row highlight behind CHANNELS list buttons
		if (this.tab == Tab.CHANNELS && !this.channels.isEmpty()) {
			drawChannelRowBackgrounds(graphics, panelBottom);
		}

		super.extractRenderState(graphics, mouseX, mouseY, a);

		graphics.centeredText(this.font, this.title, this.width / 2, 8, TEXT);

		String footer = !this.uiFeedback.isEmpty() ? this.uiFeedback : this.scriptStatus;
		if (!footer.isEmpty()) {
			graphics.centeredText(this.font, Component.literal(footer), this.width / 2, this.height - 40, MUTED);
		}

		int textX = contentLeft();
		int y = contentTop();
		switch (this.tab) {
			case CHANNELS -> drawChannelsOverlay(graphics, textX, y, panelBottom);
			case BREAKER -> drawBreakerOverlay(graphics, textX, y);
			case PLACER -> drawPlacerOverlay(graphics, textX, y);
			case SCRIPT -> { /* editor widgets */ }
			case HELP -> drawHelpOverlay(graphics, textX, y, panelBottom);
		}
	}

	private void drawChannelRowBackgrounds(GuiGraphicsExtractor graphics, int panelBottom) {
		int x = contentLeft();
		int width = contentWidth();
		int inputY = contentTop() + 28;
		int listTop = inputY + 26;
		int rowH = 22;
		int visible = Math.max(1, (panelBottom - 4 - listTop) / rowH);
		boolean scrolling = this.channels.size() > visible;
		int rowWidth = scrolling ? width - 24 : width;
		int y = listTop;
		for (int i = this.listScroll; i < this.channels.size(); i++) {
			if (y + rowH > panelBottom - 4) {
				break;
			}
			String id = this.channels.get(i);
			boolean selected = id.equals(this.selectedChannel);
			graphics.fill(x - 1, y - 1, x + rowWidth + 1, y + 21, selected ? SELECTED_BG : ROW_BG);
			y += rowH;
		}
	}

	private void drawChannelsOverlay(GuiGraphicsExtractor graphics, int textX, int y, int panelBottom) {
		graphics.text(this.font, Component.translatable("gui.codeworks.channels_blurb"), textX, y, MUTED);
		if (!this.selectedChannel.isEmpty()) {
			graphics.text(
				this.font,
				Component.translatable("gui.codeworks.selected_channel", this.selectedChannel),
				textX,
				y + 12,
				ACCENT
			);
		} else {
			graphics.text(this.font, Component.translatable("gui.codeworks.channels_none_selected"), textX, y + 12, MUTED);
		}

		if (this.channels.isEmpty()) {
			int emptyY = contentTop() + 28 + 26 + 8;
			graphics.centeredText(
				this.font,
				Component.translatable("gui.codeworks.channels_empty"),
				this.width / 2,
				emptyY,
				MUTED
			);
			graphics.centeredText(
				this.font,
				Component.translatable("gui.codeworks.channels_empty_hint"),
				this.width / 2,
				emptyY + 14,
				0xFF8899AA
			);
		} else {
			// Mirror labels above buttons so a covered/missed widget still shows the list
			int inputY = contentTop() + 28;
			int listTop = inputY + 26;
			int rowH = 22;
			int drawY = listTop;
			for (int i = this.listScroll; i < this.channels.size(); i++) {
				if (drawY + rowH > panelBottom - 4) {
					break;
				}
				// Buttons carry the interactive label; keep a small count badge on the right via feedback only
				drawY += rowH;
			}
			graphics.text(
				this.font,
				Component.translatable("gui.codeworks.channels_count", this.channels.size()),
				textX,
				panelBottom - 14,
				OK
			);
		}
	}

	private void drawBreakerOverlay(GuiGraphicsExtractor graphics, int textX, int y) {
		graphics.text(this.font, Component.translatable("gui.codeworks.breaker_tab_title"), textX, y, ACCENT);
		graphics.text(this.font, Component.translatable("gui.codeworks.breaker_tab_blurb"), textX, y + 14, MUTED);
		if (this.channels.isEmpty()) {
			graphics.text(this.font, Component.translatable("gui.codeworks.breaker_tab_empty"), textX, y + 40, MUTED);
		} else if (!this.selectedChannel.isEmpty()) {
			graphics.text(
				this.font,
				Component.translatable("gui.codeworks.breaker_tab_selected", this.selectedChannel),
				textX,
				y + 28,
				OK
			);
		}
	}

	private void drawPlacerOverlay(GuiGraphicsExtractor graphics, int textX, int y) {
		graphics.text(this.font, Component.translatable("gui.codeworks.placer_tab_title"), textX, y, ACCENT);
		graphics.text(this.font, Component.translatable("gui.codeworks.placer_tab_stub"), textX, y + 16, MUTED);
		graphics.text(this.font, Component.translatable("gui.codeworks.placer_tab_reserve"), textX, y + 32, 0xFF8899AA);
	}

	private void drawHelpOverlay(GuiGraphicsExtractor graphics, int textX, int y, int panelBottom) {
		int lineHeight = this.font.lineHeight + 2;
		int maxVisible = Math.max(1, (panelBottom - y - 28) / lineHeight);
		int maxScroll = Math.max(0, this.helpLines.size() - maxVisible);
		if (this.helpScroll > maxScroll) {
			this.helpScroll = maxScroll;
		}
		int hy = y;
		for (int i = this.helpScroll; i < this.helpLines.size(); i++) {
			if (hy + lineHeight > panelBottom - 24) {
				break;
			}
			HelpLine line = this.helpLines.get(i);
			if (!line.text().isEmpty()) {
				graphics.text(this.font, Component.literal(line.text()), textX, hy, line.color());
			}
			hy += lineHeight;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
