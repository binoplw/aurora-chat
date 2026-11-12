package net.primlixe.aurorachat.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.primlixe.aurorachat.chat.AutoColor;
import net.primlixe.aurorachat.chat.Channels;
import net.primlixe.aurorachat.chat.ChatFormat;
import net.primlixe.aurorachat.chat.ChatLog;
import net.primlixe.aurorachat.chat.ConsoleLog;
import net.primlixe.aurorachat.chat.ConsoleLog.Level;
import net.primlixe.aurorachat.chat.StaffWatch;
import net.primlixe.aurorachat.chat.Templates;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.ui.UI;
import net.primlixe.aurorachat.ui.Widgets.Field;
import net.primlixe.aurorachat.ui.Widgets.Pill;
import net.primlixe.aurorachat.ui.Widgets.Style;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Консоль в стиле серверной: полный журнал всего, что происходит (чат, система, твои команды,
 * скрытое фильтрами, входы/выходы из таба, персонал, мод, сеть), фильтры по типам, поиск,
 * пауза, копирование и строка ввода внизу — как в консоли сервера.
 */
public class ConsoleScreen extends Screen {
	private static final Set<Level> SHOW = EnumSet.allOf(Level.class);
	private static final List<String> HISTORY = new ArrayList<>();
	private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final int LH = 10;

	private final Screen parent;
	private EditBox input;
	private Field search;
	private boolean paused;
	private float scroll, smooth; // строк от низа
	private List<ConsoleLog.Line> shown = new ArrayList<>();
	private int seenVersion = -1;
	private String lastQuery = "";
	private int histPos = -1;
	private int x0, y0, x1, y1, tagW;
	private final List<int[]> chipRects = new ArrayList<>();
	private long lastFrame = System.nanoTime();
	private String toast;
	private long toastUntil;

	public ConsoleScreen(Screen parent) {
		super(Component.literal(net.primlixe.aurorachat.ui.I18n.t("Консоль")));
		this.parent = parent;
	}

	@Override
	protected void init() {
		x0 = 6; x1 = width - 6;
		y0 = 58; y1 = height - 24;
		for (Level l : Level.values()) tagW = Math.max(tagW, font.width(l.tag));
		search = new Field(font, width - 196, 3, 124, 16, net.primlixe.aurorachat.ui.I18n.t("⌕ поиск по журналу"));
		search.setValue(lastQuery);
		search.setResponder(s -> { lastQuery = s; seenVersion = -1; });
		addRenderableWidget(search);
		addRenderableWidget(new Pill(width - 66, 4, 60, 16, net.primlixe.aurorachat.ui.I18n.t("Закрыть"), Style.GHOST, p -> onClose()));
		int bx = 6;
		bx += addButton(bx, () -> paused ? net.primlixe.aurorachat.ui.I18n.t("▶ Дальше") : net.primlixe.aurorachat.ui.I18n.t("⏸ Пауза"), () -> paused = !paused) + 4;
		bx += addButton(bx, () -> net.primlixe.aurorachat.ui.I18n.t("⧉ Копировать"), this::copyAll) + 4;
		bx += addButton(bx, () -> net.primlixe.aurorachat.ui.I18n.t("✕ Очистить"), () -> { ConsoleLog.clear(); scroll = smooth = 0; }) + 4;
		addButton(bx, () -> net.primlixe.aurorachat.ui.I18n.t("⌂ Логи"), () -> {
			try { java.nio.file.Files.createDirectories(ChatLog.root()); } catch (Exception ignored) { }
			net.minecraft.util.Util.getPlatform().openPath(ChatLog.root());
		});
		input = new EditBox(font, x0 + 14, height - 18, x1 - x0 - 16, 12, Component.literal(net.primlixe.aurorachat.ui.I18n.t("Ввод")));
		input.setBordered(false);
		input.setMaxLength(256);
		input.setHint(Component.literal(net.primlixe.aurorachat.ui.I18n.t("§8сообщение или /команда — Enter отправить, ↑↓ история")));
		addRenderableWidget(input);
		setInitialFocus(input);
	}

	private int addButton(int x, java.util.function.Supplier<String> label, Runnable r) {
		int w = font.width(label.get()) + 22;
		addRenderableWidget(new Pill(x, 24, w, 15, label.get(), Style.SECONDARY, p -> r.run()).label(label));
		return w;
	}

	private void copyAll() {
		StringBuilder sb = new StringBuilder();
		for (ConsoleLog.Line l : shown) sb.append(line(l)).append('\n');
		minecraft.keyboardHandler.setClipboard(sb.toString());
		toast(net.primlixe.aurorachat.ui.I18n.t("Скопировано строк: ") + shown.size());
	}

	private String line(ConsoleLog.Line l) {
		return "[" + LocalDateTime.ofInstant(Instant.ofEpochMilli(l.time()), ZoneId.systemDefault()).format(TF) + "] [" + l.level().tag + "] " + l.plain();
	}

	private void toast(String s) { toast = s; toastUntil = System.currentTimeMillis() + 1500; }

	private void refresh() {
		if (paused && seenVersion >= 0) return;
		if (seenVersion == ConsoleLog.version()) return;
		int before = shown.size();
		String q = lastQuery.trim().toLowerCase(Locale.ROOT);
		List<ConsoleLog.Line> out = new ArrayList<>();
		for (ConsoleLog.Line l : ConsoleLog.snapshot()) {
			if (!SHOW.contains(l.level())) continue;
			if (!q.isEmpty() && !l.plain().toLowerCase(Locale.ROOT).contains(q)) continue;
			out.add(l);
		}
		// если пользователь листает вверх — держим вид на месте
		if (scroll > 0 && out.size() > before && seenVersion >= 0) { scroll += out.size() - before; smooth += out.size() - before; }
		shown = out;
		seenVersion = ConsoleLog.version();
	}

	private int rows() { return Math.max(1, (y1 - y0 - 4) / LH); }

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
		g.fill(0, 0, width, height, 0xF2070910);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
		refresh();
		long t = System.nanoTime();
		float dt = Math.min(0.1f, (t - lastFrame) / 1e9f);
		lastFrame = t;
		float max = Math.max(0, shown.size() - rows());
		scroll = Math.max(0, Math.min(max, scroll));
		smooth = UI.damp(smooth, scroll, 18, dt);

		// шапка
		g.fill(0, 0, width, 56, 0xFF0B0E16);
		g.fill(0, 56, width, 57, UI.alpha(UI.accent(), 0.6f));
		g.text(font, "▌", 6, 8, UI.accent(), false);
		g.text(font, net.primlixe.aurorachat.ui.I18n.t("КОНСОЛЬ"), 14, 8, 0xFFE8EAF2, false);
		var mc = Minecraft.getInstance();
		String srv = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : (mc.getSingleplayerServer() != null ? net.primlixe.aurorachat.ui.I18n.t("одиночная игра") : net.primlixe.aurorachat.ui.I18n.t("меню"));
		int online = mc.getConnection() == null ? 0 : mc.getConnection().getOnlinePlayers().size();
		String info = srv + net.primlixe.aurorachat.ui.I18n.t("  ·  онлайн ") + online + (StaffWatch.onlineCount() > 0 ? net.primlixe.aurorachat.ui.I18n.t("  ·  §c⚑ персонал ") + StaffWatch.onlineCount() : "") + net.primlixe.aurorachat.ui.I18n.t("§7  ·  строк ") + shown.size();
		g.text(font, "§7" + UI.trim(font, info, width - 196 - 66), 60, 8, 0xFFFFFFFF, false);
		// фильтры-чипы — третья строка шапки (ЛКМ — вкл/выкл, ПКМ — только этот тип)
		chipRects.clear();
		int cx = x0;
		Level[] lv = Level.values();
		g.text(font, net.primlixe.aurorachat.ui.I18n.t("§8типы:"), cx, 44, 0xFFFFFFFF, false);
		cx += font.width(net.primlixe.aurorachat.ui.I18n.t("типы:")) + 6;
		for (int i = 0; i < lv.length; i++) {
			Level l = lv[i];
			int w = font.width(l.tag) + 8;
			if (cx + w > x1) break;
			boolean on = SHOW.contains(l);
			UI.round(g, cx, 42, cx + w, 53, 4, on ? UI.alpha(l.color, 0.85f) : 0xFF1A1E2A);
			g.text(font, l.tag, cx + 4, 44, on ? 0xFF0A0A0A : UI.MUTED, false);
			chipRects.add(new int[]{cx, 42, cx + w, 53, i});
			cx += w + 3;
		}

		// строки
		g.enableScissor(x0, y0, x1, y1);
		int n = rows();
		float base = shown.size() - n - smooth;
		int first = Math.max(0, (int) Math.floor(base));
		float frac = base - (float) Math.floor(base);
		ConsoleLog.Line hovered = null;
		for (int i = 0; i <= n + 1; i++) {
			int idx = first + i;
			if (idx < 0 || idx >= shown.size()) continue;
			ConsoleLog.Line l = shown.get(idx);
			int y = Math.round(y0 + 2 + (i - frac) * LH) + (base < 0 ? Math.round(-base * LH) : 0);
			if (y < y0 - LH || y > y1) continue;
			boolean hov = mx >= x0 && mx < x1 && my >= y && my < y + LH && my >= y0 && my < y1;
			if (hov) { g.fill(x0, y, x1, y + LH, 0x18FFFFFF); hovered = l; }
			else if (idx % 2 == 1) g.fill(x0, y, x1, y + LH, 0x08FFFFFF);
			String time = LocalDateTime.ofInstant(Instant.ofEpochMilli(l.time()), ZoneId.systemDefault()).format(TF);
			int x = x0 + 2;
			g.text(font, time, x, y + 1, 0xFF5A6074, false);
			x += font.width("00:00:00") + 6;
			g.fill(x, y + 1, x + 2, y + LH - 1, l.level().color);
			g.text(font, l.level().tag, x + 5, y + 1, l.level().color, false);
			x += tagW + 12;
			g.text(font, UI.trim(font, l.legacy(), x1 - x - 4), x, y + 1, 0xFFDDE1EA, false);
		}
		g.disableScissor();
		if (shown.isEmpty()) UI.centered(g, font, net.primlixe.aurorachat.ui.I18n.t("Пока пусто — здесь появится всё, что происходит в чате и на сервере"), width / 2, (y0 + y1) / 2, UI.MUTED);
		// полоса прокрутки
		if (max > 0) {
			int h = y1 - y0, bar = Math.max(12, (int) (h * (float) n / shown.size()));
			int by = y0 + (int) ((h - bar) * (1 - smooth / max));
			UI.round(g, x1 - 3, by, x1 - 1, by + bar, 1, UI.alpha(UI.accent(), 0.7f));
		}
		if (scroll > 0.5f) {
			String s = net.primlixe.aurorachat.ui.I18n.t("↓ к новым (") + Math.round(scroll) + ")";
			int w = font.width(s) + 10;
			UI.round(g, x1 - w - 8, y1 - 16, x1 - 8, y1 - 3, 6, UI.alpha(UI.accent(), 0.95f));
			g.text(font, s, x1 - w - 3, y1 - 13, 0xFFFFFFFF, false);
		}
		if (paused) g.text(font, net.primlixe.aurorachat.ui.I18n.t("§e⏸ пауза — новые строки не добавляются"), x0 + 2, y1 + 1, 0xFFFFFFFF, false);

		// строка ввода
		g.fill(x0, height - 21, x1, height - 3, 0xFF0F131D);
		g.fill(x0, height - 21, x1, height - 20, UI.alpha(UI.accent(), 0.5f));
		g.text(font, ">", x0 + 4, height - 17, UI.accent(), false);

		super.extractRenderState(g, mx, my, partial);
		if (hovered != null) {
			List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>(font.split(Component.literal(line(hovered)), Math.min(360, width - 20)));
			lines.add(Component.literal(net.primlixe.aurorachat.ui.I18n.t("§8ЛКМ — копировать строку")).getVisualOrderText());
			g.setTooltipForNextFrame(font, lines, mx, my);
		}
		if (toast != null && System.currentTimeMillis() < toastUntil) {
			int tw = font.width(toast) + 12, tx = width / 2 - tw / 2;
			UI.round(g, tx, y0 + 6, tx + tw, y0 + 20, 6, 0xF0141420);
			g.text(font, "§a" + toast, tx + 6, y0 + 9, 0xFFFFFFFF, false);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		for (int[] r : chipRects) {
			if (e.x() >= r[0] && e.x() < r[2] && e.y() >= r[1] && e.y() < r[3]) {
				Level l = Level.values()[r[4]];
				if (e.button() == 1) { SHOW.clear(); SHOW.add(l); } // ПКМ — только этот тип
				else if (!SHOW.remove(l)) SHOW.add(l);
				if (SHOW.isEmpty()) SHOW.addAll(EnumSet.allOf(Level.class));
				seenVersion = -1;
				return true;
			}
		}
		if (super.mouseClicked(e, dbl)) return true;
		if (scroll > 0.5f && e.x() > x1 - 140 && e.y() > y1 - 18 && e.y() < y1) { scroll = 0; return true; }
		if (e.x() >= x0 && e.x() < x1 && e.y() >= y0 && e.y() < y1) {
			int n = rows();
			float base = shown.size() - n - smooth;
			int idx = (int) Math.floor(base + (e.y() - y0 - 2) / LH);
			if (idx >= 0 && idx < shown.size()) {
				minecraft.keyboardHandler.setClipboard(line(shown.get(idx)));
				toast(net.primlixe.aurorachat.ui.I18n.t("Строка скопирована"));
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		scroll = Math.max(0, scroll + (float) sy * 3);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (input.isFocused()) {
			if (e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) { send(); return true; }
			if (e.key() == GLFW.GLFW_KEY_UP && !HISTORY.isEmpty()) {
				histPos = histPos < 0 ? HISTORY.size() - 1 : Math.max(0, histPos - 1);
				input.setValue(HISTORY.get(histPos));
				return true;
			}
			if (e.key() == GLFW.GLFW_KEY_DOWN && histPos >= 0) {
				histPos++;
				input.setValue(histPos >= HISTORY.size() ? "" : HISTORY.get(histPos));
				if (histPos >= HISTORY.size()) histPos = -1;
				return true;
			}
		}
		if (e.key() == GLFW.GLFW_KEY_PAGE_UP) { scroll += rows() - 1; return true; }
		if (e.key() == GLFW.GLFW_KEY_PAGE_DOWN) { scroll = Math.max(0, scroll - rows() + 1); return true; }
		if (e.key() == GLFW.GLFW_KEY_END && !input.isFocused()) { scroll = 0; return true; }
		return super.keyPressed(e);
	}

	/** Отправка из консоли — тем же путём, что из чата: эмодзи, шаблоны, автоцвет, канал. */
	private void send() {
		String raw = input.getValue().trim();
		if (raw.isEmpty()) return;
		var mc = Minecraft.getInstance();
		HISTORY.add(raw);
		histPos = -1;
		input.setValue("");
		scroll = 0;
		if (mc.getConnection() == null) { ConsoleLog.add(Level.SYS, "§cНет подключения к серверу"); return; }
		ChatConfig c = ChatConfig.get();
		String msg = raw;
		if (c.emojiShortcodes) msg = ChatFormat.emojify(msg);
		msg = Templates.apply(msg);
		msg = AutoColor.apply(msg);
		msg = net.primlixe.aurorachat.chat.ColorCodes.expand(msg); // &#HEX и &{градиент} — как в обычном чате
		msg = Channels.applyOutgoing(msg);
		net.primlixe.aurorachat.compat.Mc.chat().addRecentChat(raw);
		if (msg.startsWith("/")) {
			ConsoleLog.add(Level.CMD, "§b" + ConsoleLog.mask(msg));
			mc.getConnection().sendCommand(msg.substring(1));
		} else {
			ConsoleLog.add(Level.OUT, msg);
			mc.getConnection().sendChat(msg);
		}
	}

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public void onClose() { net.primlixe.aurorachat.compat.Mc.setScreen(parent); }
}
