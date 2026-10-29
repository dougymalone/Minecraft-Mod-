package com.dougymalone.codeworks.client.screen;

import com.dougymalone.codeworks.block.entity.CentralCommandBlockEntity;
import com.dougymalone.codeworks.channel.ChannelIds;
import com.dougymalone.codeworks.network.SaveCentralCommandPayload;
import com.dougymalone.codeworks.script.ScriptHelp;
import com.dougymalone.codeworks.script.ScriptSections;
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
 * Central Command UI. Panel chrome is drawn BEFORE widgets so rows are not covered.
 * Opaque ARGB text for MC 26.3. Supports multiple named script sections.
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
	private final List<ScriptSections.Section> scripts = new ArrayList<>();
	private String selectedScript = ScriptSections.MAIN;
	private String scriptStatus;
	private String uiFeedback = "";
	private String selectedChannel = "";
	private Tab tab = Tab.CHANNELS;
	private int listScroll;
	private int scriptChipScroll;
	private int helpScroll;

	private EditBox channelInput;
	private EditBox scriptNameInput;
	private MultiLineEditBox scriptEditor;
	private List<HelpLine> helpLines = List.of();

	public CentralCommandScreen(
		BlockPos pos,
		String status,
		String channelsCsv,
		String countsCsv,
		String scriptsPack,
		String selectedScript
	) {
		super(Component.translatable("gui.codeworks.central_command_title"));
		this.pos = pos;
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
		this.scripts.addAll(ScriptSections.unpack(scriptsPack));
		String sel = ScriptSections.sanitizeName(selectedScript);
		if (ScriptSections.containsName(this.scripts, sel)) {
			this.selectedScript = sel;
		} else {
			this.selectedScript = this.scripts.getFirst().name;
		}
		if (!this.channels.isEmpty()) {
			this.selectedChannel = this.channels.getFirst();
			this.uiFeedback = Component.translatable("gui.codeworks.channels_loaded", this.channels.size()).getString();
		} else {
			this.uiFeedback = Component.translatable("gui.codeworks.channels_empty_hint").getString();
		}
	}

	private int contentLeft() {
		return (this.width - contentWidth()) / 2;
	}

	private int contentWidth() {
		return Math.min(380, Math.max(260, this.width - 40));
	}

	private int contentTop() {
		return 50;
	}

	private int contentBottom() {
		return this.height - 48;
	}

	private ScriptSections.Section selectedSection() {
		ScriptSections.Section found = ScriptSections.find(this.scripts, this.selectedScript);
		return found != null ? found : this.scripts.getFirst();
	}

	@Override
	protected void init() {
		this.clearWidgets();
		this.channelInput = null;
		this.scriptNameInput = null;
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
		int x = contentLeft();
		int width = contentWidth();
		int chipY = top;
		int chipH = 20;
		int chipGap = 2;
		int maxChipW = 72;
		int addW = 28;
		int available = width - addW - 4;
		int chipsFit = Math.max(1, (available + chipGap) / (maxChipW + chipGap));
		int maxScroll = Math.max(0, this.scripts.size() - chipsFit);
		if (this.scriptChipScroll > maxScroll) {
			this.scriptChipScroll = maxScroll;
		}

		int cx = x;
		for (int i = this.scriptChipScroll; i < this.scripts.size(); i++) {
			if (cx + maxChipW > x + available) {
				break;
			}
			final int index = i;
			ScriptSections.Section section = this.scripts.get(i);
			boolean selected = section.name.equals(this.selectedScript);
			String mark = section.enabled ? "" : "·";
			String label = (selected ? ">" : "") + mark + section.name;
			if (label.length() > 10) {
				label = label.substring(0, 10);
			}
			int chipW = Math.min(maxChipW, Math.max(48, this.font.width(label) + 12));
			this.addRenderableWidget(
				Button.builder(Component.literal(label), b -> this.selectScript(index))
					.bounds(cx, chipY, chipW, chipH)
					.build()
			);
			cx += chipW + chipGap;
		}
		this.addRenderableWidget(
			Button.builder(Component.literal("+"), b -> this.addScript())
				.bounds(x + width - addW, chipY, addW, chipH)
				.build()
		);

		int toolsY = chipY + chipH + 4;
		ScriptSections.Section current = selectedSection();
		this.scriptNameInput = new EditBox(
			this.font,
			x,
			toolsY,
			Math.max(80, width - 168),
			20,
			Component.translatable("gui.codeworks.script_name")
		);
		this.scriptNameInput.setMaxLength(ScriptSections.MAX_NAME_LENGTH);
		this.scriptNameInput.setValue(current.name);
		this.scriptNameInput.setHint(Component.translatable("gui.codeworks.script_name_hint"));
		this.addRenderableWidget(this.scriptNameInput);

		int bx = x + width - 164;
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.codeworks.script_rename"), b -> this.renameScript())
				.bounds(bx, toolsY, 52, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(
				Component.translatable(current.enabled ? "gui.codeworks.script_on" : "gui.codeworks.script_off"),
				b -> this.toggleScriptEnabled()
			).bounds(bx + 54, toolsY, 36, 20).build()
		);
		Button delete = Button.builder(Component.translatable("gui.codeworks.script_delete"), b -> this.deleteScript())
			.bounds(bx + 92, toolsY, 72, 20)
			.build();
		delete.active = this.scripts.size() > 1;
		this.addRenderableWidget(delete);

		int editorTop = toolsY + 24;
		int height = Math.max(60, bottom - editorTop - 16);
		this.scriptEditor = MultiLineEditBox.builder()
			.setX(x)
			.setY(editorTop)
			.setPlaceholder(Component.translatable("gui.codeworks.script_placeholder"))
			.build(this.font, width, height, Component.translatable("gui.codeworks.tab_script"));
		this.scriptEditor.setCharacterLimit(CentralCommandBlockEntity.MAX_SCRIPT_LENGTH);
		this.scriptEditor.setValue(current.source);
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
		this.listScroll = Math.max(0, this.channels.size() - 1);
		this.persistAll();
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
		this.persistAll();
		this.rebuildWidgets();
	}

	private void selectScript(int index) {
		if (index < 0 || index >= this.scripts.size()) {
			return;
		}
		captureScriptIfNeeded();
		this.selectedScript = this.scripts.get(index).name;
		this.uiFeedback = Component.translatable("gui.codeworks.script_selected", this.selectedScript).getString();
		this.rebuildWidgets();
	}

	private void addScript() {
		if (this.scripts.size() >= ScriptSections.MAX_SCRIPTS) {
			this.uiFeedback = Component.translatable("gui.codeworks.script_limit").getString();
			return;
		}
		captureScriptIfNeeded();
		String name = ScriptSections.uniqueName(this.scripts, "script");
		this.scripts.add(new ScriptSections.Section(name, "// " + name + "\nWait(20);\n", true));
		this.selectedScript = name;
		this.scriptChipScroll = Math.max(0, this.scripts.size() - 1);
		this.uiFeedback = Component.translatable("gui.codeworks.script_added", name).getString();
		this.persistAll();
		this.rebuildWidgets();
	}

	private void renameScript() {
		if (this.scriptNameInput == null) {
			return;
		}
		captureScriptIfNeeded();
		String next = ScriptSections.sanitizeName(this.scriptNameInput.getValue());
		if (next.isEmpty()) {
			this.uiFeedback = Component.translatable("gui.codeworks.script_name_invalid").getString();
			return;
		}
		ScriptSections.Section current = selectedSection();
		if (next.equals(current.name)) {
			return;
		}
		if (ScriptSections.containsName(this.scripts, next)) {
			this.uiFeedback = Component.translatable("gui.codeworks.script_name_taken", next).getString();
			return;
		}
		String old = current.name;
		current.name = next;
		this.selectedScript = next;
		this.uiFeedback = Component.translatable("gui.codeworks.script_renamed", old, next).getString();
		this.persistAll();
		this.rebuildWidgets();
	}

	private void toggleScriptEnabled() {
		captureScriptIfNeeded();
		ScriptSections.Section current = selectedSection();
		current.enabled = !current.enabled;
		this.uiFeedback = Component.translatable(
			current.enabled ? "gui.codeworks.script_enabled" : "gui.codeworks.script_disabled",
			current.name
		).getString();
		this.persistAll();
		this.rebuildWidgets();
	}

	private void deleteScript() {
		if (this.scripts.size() <= 1) {
			this.uiFeedback = Component.translatable("gui.codeworks.script_keep_one").getString();
			return;
		}
		captureScriptIfNeeded();
		ScriptSections.Section current = selectedSection();
		this.scripts.remove(current);
		this.selectedScript = this.scripts.getFirst().name;
		this.uiFeedback = Component.translatable("gui.codeworks.script_deleted", current.name).getString();
		this.persistAll();
		this.rebuildWidgets();
	}

	private void captureScriptIfNeeded() {
		if (this.scriptEditor != null) {
			ScriptSections.Section current = selectedSection();
			current.source = this.scriptEditor.getValue();
		}
	}

	private void persistAll() {
		captureScriptIfNeeded();
		ClientPlayNetworking.send(new SaveCentralCommandPayload(
			this.pos,
			String.join("\n", this.channels),
			ScriptSections.pack(this.scripts),
			this.selectedScript
		));
	}

	private void saveOnly() {
		persistAll();
		this.uiFeedback = Component.translatable(
			"gui.codeworks.saved_ok_scripts",
			this.channels.size(),
			this.scripts.size()
		).getString();
	}

	private void saveAndClose() {
		persistAll();
		this.onClose();
	}

	private List<HelpLine> buildHelpLines() {
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
		if (this.tab == Tab.SCRIPT) {
			if (scrollY > 0) {
				this.scriptChipScroll = Math.max(0, this.scriptChipScroll - 1);
			} else if (scrollY < 0) {
				this.scriptChipScroll++;
			}
			this.rebuildWidgets();
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int panelW = contentWidth() + 16;
		int panelX = (this.width - panelW) / 2;
		int panelY = contentTop() - 6;
		int panelBottom = contentBottom() + 4;
		graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelBottom + 2, PANEL_EDGE);
		graphics.fill(panelX, panelY, panelX + panelW, panelBottom, PANEL);

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
			case SCRIPT -> drawScriptOverlay(graphics, textX, y);
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

	private void drawScriptOverlay(GuiGraphicsExtractor graphics, int textX, int y) {
		// chips/tools/editor are widgets; small caption under title area if needed
		ScriptSections.Section current = selectedSection();
		String state = current.enabled ? "on" : "off";
		graphics.text(
			this.font,
			Component.translatable("gui.codeworks.script_blurb", this.scripts.size(), state),
			textX,
			contentBottom() - 12,
			MUTED
		);
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
