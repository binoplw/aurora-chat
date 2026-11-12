package net.primlixe.aurorachat.chat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.ui.UI;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Палитра у строки ввода (кнопка «&»): 16 обычных цветов, стили, спектр HEX-цветов и сохранённые градиенты.
 * Клик вставляет код в строку. Режим «градиент»: два клика по спектру → &{#A-#B}.
 */
public final class ColorPicker {
	private static final String LEGACY = "0123456789abcdef";
	private static final int[] LEGACY_RGB = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
	private static final String[][] STYLES = {{"l", "§lЖ", "Жирный &l"}, {"o", "§oК", "Курсив &o"}, {"n", "§nП", "Подчёркнутый &n"},
			{"m", net.primlixe.aurorachat.ui.I18n.t("§mЗ"), net.primlixe.aurorachat.ui.I18n.t("Зачёркнутый &m")}, {"k", "§kX", net.primlixe.aurorachat.ui.I18n.t("Мигающий &k")}, {"r", "↺", net.primlixe.aurorachat.ui.I18n.t("Сброс &r")}};
	public static final int W = 168;
	private static final int SPEC_W = 156, SPEC_H = 40;

	private boolean gradientMode;
	private int first = -1;
	private final List<int[]> rects = new ArrayList<>();   // x0,y0,x1,y1, kind, value
	private final List<String> values = new ArrayList<>();
	private int[] specRect, modeRect, footRect;
	private int x, y, h;

	public void reset() { gradientMode = false; first = -1; }

	/** Сколько рядов градиентов видно сразу (остальные — колесом мыши). */
	private static final int GRAD_ROWS = 6;
	/** Готовые цветные подписи градиентов (разбор кодов — один раз, а не каждый кадр). */
	private static final java.util.Map<String, Component> NAMES = new java.util.HashMap<>();
	private int gradScroll; // первый видимый ряд
	private int[] gradArea;

	private static int gradRowsTotal() { return Math.max(1, (ChatConfig.get().gradients.size() + 2) / 3); }

	public int height() {
		int gl = Math.min(GRAD_ROWS, gradRowsTotal());
		return 14 + 2 * 13 + 4 + 14 + 4 + SPEC_H + 16 + gl * 13 + 18;
	}

	/** Колесо над градиентами — листать их. true — прокрутка съедена. */
	public boolean scroll(double mx, double my, double sy) {
		if (!in(gradArea, mx, my)) return false;
		int max = Math.max(0, gradRowsTotal() - GRAD_ROWS);
		gradScroll = Math.max(0, Math.min(max, gradScroll - (int) Math.signum(sy)));
		return true;
	}

	public boolean contains(double mx, double my) { return mx >= x && mx < x + W && my >= y && my < y + h; }

	/** Цвет спектра в точке (0..1, 0..1): сверху светлые, посередине чистые, снизу тёмные. */
	private static int spectrum(float fx, float fy) {
		float s = fy < 0.5f ? fy * 2 : 1f;
		float b = fy < 0.5f ? 1f : 1f - (fy - 0.5f) * 1.5f;
		return UI.hsb(fx, s, b, 1) & 0xFFFFFF;
	}

	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int mx, int my) {
		this.x = x;
		this.y = y;
		this.h = height();
		rects.clear();
		values.clear();
		UI.shadow(g, x, y, x + W, y + h, 6);
		UI.round(g, x, y, x + W, y + h, 6, 0xF0121424);
		UI.roundOutline(g, x, y, x + W, y + h, 6, UI.alpha(UI.accent(), 0.6f));
		g.text(font, net.primlixe.aurorachat.ui.I18n.t("Цвета · клик — вставить код"), x + 6, y + 4, UI.SUB, false);
		String tip = null;
		int cy = y + 15;
		// 16 обычных цветов
		for (int i = 0; i < 16; i++) {
			int cx = x + 6 + (i % 8) * 20, ry = cy + (i / 8) * 13;
			int[] r = {cx, ry, cx + 18, ry + 11};
			boolean hv = in(r, mx, my);
			if (hv) UI.round(g, r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, 3, 0xFFFFFFFF);
			UI.round(g, r[0], r[1], r[2], r[3], 2, 0xFF000000 | LEGACY_RGB[i]);
			add(r, "&" + LEGACY.charAt(i));
			if (hv) tip = "&" + LEGACY.charAt(i);
		}
		cy += 2 * 13 + 4;
		// стили
		for (int i = 0; i < STYLES.length; i++) {
			int cx = x + 6 + i * 26;
			int[] r = {cx, cy, cx + 24, cy + 12};
			boolean hv = in(r, mx, my);
			UI.round(g, r[0], r[1], r[2], r[3], 3, hv ? UI.alpha(UI.accent(), 0.85f) : 0x30FFFFFF);
			UI.centered(g, font, STYLES[i][1], (r[0] + r[2]) / 2, cy + 2, 0xFFFFFFFF);
			add(r, "&" + STYLES[i][0]);
			if (hv) tip = STYLES[i][2];
		}
		cy += 14 + 4;
		// спектр
		int sx = x + 6;
		specRect = new int[]{sx, cy, sx + SPEC_W, cy + SPEC_H};
		// спектр — готовая текстура (раньше 1500 квадратиков с пересчётом цвета каждый кадр)
		net.primlixe.aurorachat.ui.Baked.draw(g, "picker_spectrum", SPEC_W, SPEC_H, (px, py) -> spectrum(px / (float) SPEC_W, py / (float) SPEC_H), sx, cy, sx + SPEC_W, cy + SPEC_H);
		if (in(specRect, mx, my)) {
			int col = spectrum((mx - sx) / (float) SPEC_W, (my - cy) / (float) SPEC_H);
			g.fill(mx - 1, cy, mx, cy + SPEC_H, 0x80FFFFFF);
			tip = String.format(Locale.ROOT, "&#%06X", col) + (gradientMode ? (first < 0 ? net.primlixe.aurorachat.ui.I18n.t(" — первый цвет градиента") : net.primlixe.aurorachat.ui.I18n.t(" — второй цвет")) : "");
		}
		if (first >= 0) UI.roundOutline(g, sx - 1, cy - 1, sx + SPEC_W + 1, cy + SPEC_H + 1, 2, 0xFF000000 | first);
		cy += SPEC_H + 3;
		// режим градиента
		String mode = gradientMode ? (first < 0 ? net.primlixe.aurorachat.ui.I18n.t("Градиент: кликни 1-й цвет") : net.primlixe.aurorachat.ui.I18n.t("Градиент: кликни 2-й цвет")) : net.primlixe.aurorachat.ui.I18n.t("Градиент из двух цветов…");
		modeRect = new int[]{x + 6, cy, x + W - 6, cy + 12};
		UI.round(g, modeRect[0], modeRect[1], modeRect[2], modeRect[3], 3, gradientMode ? UI.alpha(UI.accent(), 0.7f) : in(modeRect, mx, my) ? 0x40FFFFFF : 0x24FFFFFF);
		UI.centered(g, font, mode, x + W / 2, cy + 2, 0xFFFFFFFF);
		cy += 16;
		// сохранённые градиенты
		var grads = ChatConfig.get().gradients;
		int colW = (W - 12) / 3;
		int total = gradRowsTotal(), rowsShown = Math.min(GRAD_ROWS, total);
		gradScroll = Math.max(0, Math.min(gradScroll, total - rowsShown));
		gradArea = new int[]{x + 4, cy - 1, x + W - 2, cy + rowsShown * 13};
		if (total > rowsShown) {
			// полоса прокрутки градиентов
			int bh = rowsShown * 13, th = Math.max(8, bh * rowsShown / total), ty = cy + (bh - th) * gradScroll / Math.max(1, total - rowsShown);
			g.fill(x + W - 4, cy, x + W - 2, cy + bh, 0x20FFFFFF);
			g.fill(x + W - 4, ty, x + W - 2, ty + th, UI.alpha(UI.accent(), 0.8f));
		}
		for (int i = gradScroll * 3; i < Math.min(grads.size(), (gradScroll + rowsShown) * 3); i++) {
			var gr = grads.get(i);
			int cx = x + 6 + (i % 3) * colW, ry = cy + (i / 3 - gradScroll) * 13;
			int[] r = {cx, ry, cx + colW - 2, ry + 11};
			boolean hv = in(r, mx, my);
			if (hv) UI.round(g, r[0], r[1], r[2], r[3], 3, 0x30FFFFFF);
			boolean writing = gr.name.equalsIgnoreCase(ChatConfig.get().writeGradient);
			if (writing) UI.roundOutline(g, r[0], r[1], r[2], r[3], 3, 0xFFFFFFFF);
			String nk = (writing ? "✎" : "") + gr.name + "|" + String.join(",", gr.colors) + "|" + colW;
			Component name = NAMES.computeIfAbsent(nk, k -> ColorCodes.preview("&{" + gr.name + "}" + UI.trim(font, (writing ? "✎" : "") + gr.name, colW - 6)));
			if (NAMES.size() > 400) NAMES.clear();
			g.text(font, name, cx + 3, ry + 2, 0xFFFFFFFF, true);
			add(r, "&{" + gr.name + "}");
			if (hv) tip = writing ? net.primlixe.aurorachat.ui.I18n.t("ПКМ — перестать писать этим градиентом") : net.primlixe.aurorachat.ui.I18n.t("Клик — вставить · ПКМ — писать им всегда");
		}
		cy += rowsShown * 13 + 2;
		if (tip == null && total > rowsShown && in(gradArea, mx, my)) tip = net.primlixe.aurorachat.ui.I18n.t("Колесо мыши — ещё градиенты (") + grads.size() + ")";
		String wg = ChatConfig.get().writeGradient;
		footRect = null;
		if (tip == null && wg != null && !wg.isBlank()) {
			footRect = new int[]{x + 4, cy, x + W - 4, cy + 12};
			if (in(footRect, mx, my)) UI.round(g, footRect[0], footRect[1], footRect[2], footRect[3], 3, 0x30FFFFFF);
			tip = net.primlixe.aurorachat.ui.I18n.t("✎ Пишу: ") + wg + net.primlixe.aurorachat.ui.I18n.t(" · клик — выключить");
		}
		g.text(font, UI.trim(font, tip != null ? tip : net.primlixe.aurorachat.ui.I18n.t("ПКМ по градиенту — писать им всегда"), W - 12), x + 6, cy + 2, tip != null ? 0xFFFFFFFF : UI.MUTED, false);
	}

	private void add(int[] r, String v) {
		rects.add(r);
		values.add(v);
	}

	private static boolean in(int[] r, double mx, double my) { return r != null && mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]; }

	/** Клик: строка для вставки или null (если клик сменил режим или мимо). */
	public String click(double mx, double my, int button) {
		if (in(footRect, mx, my)) {
			ChatConfig.get().writeGradient = "";
			ChatConfig.save();
			return null;
		}
		if (button == 1) {
			// ПКМ по сохранённому градиенту — писать им всегда (ещё раз — выключить)
			for (int i = 0; i < rects.size(); i++) {
				String v = values.get(i);
				if (in(rects.get(i), mx, my) && v.startsWith("&{")) {
					String name = v.substring(2, v.length() - 1);
					ChatConfig c = ChatConfig.get();
					c.writeGradient = name.equalsIgnoreCase(c.writeGradient) ? "" : name;
					ChatConfig.save();
					return null;
				}
			}
			return null;
		}
		if (in(modeRect, mx, my)) {
			gradientMode = !gradientMode;
			first = -1;
			return null;
		}
		if (in(specRect, mx, my)) {
			int col = spectrum((float) (mx - specRect[0]) / SPEC_W, (float) (my - specRect[1]) / SPEC_H);
			if (!gradientMode) return String.format(Locale.ROOT, "&#%06X", col);
			if (first < 0) { first = col; return null; }
			String out = String.format(Locale.ROOT, "&{#%06X-#%06X}", first, col);
			first = -1;
			gradientMode = false;
			return out;
		}
		for (int i = 0; i < rects.size(); i++) if (in(rects.get(i), mx, my)) return values.get(i);
		return null;
	}
}
