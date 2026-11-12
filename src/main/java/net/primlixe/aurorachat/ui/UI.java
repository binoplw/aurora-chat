package net.primlixe.aurorachat.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.primlixe.aurorachat.config.ChatConfig;

/** Рисование интерфейса Aurora Chat: скруглённые панели, тени, градиенты, акцентный цвет. */
public final class UI {
	private static final int DEF_TEXT = 0xFFF2F3FA, DEF_SUB = 0xFF9EA3BC, DEF_MUTED = 0xFF6A6F88;
	/** Цвета текста. В окне настроек — из его темы, в остальном моде — эти по умолчанию. */
	public static int TEXT = DEF_TEXT;
	public static int SUB = DEF_SUB;
	public static int MUTED = DEF_MUTED;
	/** «Чернила» строк и рамок: белые на тёмных темах, чёрные на светлых. */
	public static int INK = 0xFFFFFF;
	/** Панели окна настроек: цвет с прозрачностью и скругление. */
	public static int PANEL = 0xB00E1015;
	public static int RADIUS = 3;

	/** Цвет «чернил» с прозрачностью a (0…255). */
	public static int ink(int a) { return (Math.max(0, Math.min(255, a)) << 24) | (INK & 0xFFFFFF); }
	public static final int CARD = 0xD0181A2C;
	public static final int GOOD = 0xFF5BE39A;
	public static final int BAD = 0xFFFF6B7A;
	public static final int GOLD = 0xFFFFD166;

	public static float time() { return (System.nanoTime() % 1_000_000_000_000L) / 1e9f; }

	// ------------------------------------------------ окно настроек: свой шрифт и цвет выделения

	private static boolean settings;
	private static net.minecraft.network.chat.FontDescription uiFont;

	/** Включить оформление окна настроек (шрифт и цвет из раздела «Окно настроек»). */
	public static void beginSettings() {
		settings = true;
		ChatConfig c = ChatConfig.get();
		uiFont = Fonts.of(c.uiFont);
		// тема окна настроек
		TEXT = 0xFF000000 | c.uiText;
		SUB = 0xFF000000 | c.uiSub;
		MUTED = 0xFF000000 | c.uiMuted;
		INK = c.uiInk;
		PANEL = rgba(c.uiPanel, c.uiPanelAlpha);
		RADIUS = c.uiRadius;
	}

	public static void endSettings() {
		settings = false;
		uiFont = null;
		TEXT = DEF_TEXT; SUB = DEF_SUB; MUTED = DEF_MUTED;
		INK = 0xFFFFFF;
		PANEL = 0xB00E1015;
		RADIUS = 3;
	}

	public static boolean inSettings() { return settings; }

	public static net.minecraft.network.chat.Component lit(String s) {
		var c = net.minecraft.network.chat.Component.literal(s);
		return uiFont == null ? c : c.withStyle(st -> st.withFont(uiFont));
	}

	/** Текст шрифтом интерфейса (в настройках — выбранным, иначе обычным). */
	public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, boolean shadow) {
		if (uiFont == null) g.text(font, s, x, y, color, shadow);
		else g.text(font, lit(s), x, y, color, shadow);
	}

	/** Ширина текста: обычный шрифт — из кэша Aurora Lib (одни и те же строки меряются каждый кадр). */
	public static int width(Font font, String s) { return uiFont == null ? net.primlixe.auroralib.text.TextCache.width(font, s) : font.width(lit(s)); }

	/** Подсказка с учётом шрифта. */
	public static net.minecraft.client.gui.components.Tooltip tip(String s) { return net.minecraft.client.gui.components.Tooltip.create(lit(I18n.t(s))); }

	/** Акцент: свой цвет из настроек или медленно переливающееся «сияние». */
	public static int accent(float shift) {
		ChatConfig cfg = ChatConfig.get();
		if (settings && !cfg.uiFollowAccent) return 0xFF000000 | cfg.uiAccent;
		int fixed = cfg.accentColor;
		if (fixed >= 0) return 0xFF000000 | fixed;
		return hsb(0.72f + 0.12f * (float) Math.sin(time() * 0.25f) + shift, 0.62f, 1f, 1f);
	}

	public static int accent() { return accent(0); }

	public static int alpha(int c, float a) { return rgba(c, Math.max(0, Math.min(1, a)) * ((c >>> 24) / 255f)); }

	public static int rgba(int rgb, float a) { return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (rgb & 0xFFFFFF); }

	public static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
		int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}

	public static int hsb(float h, float s, float b, float a) {
		h = (h - (float) Math.floor(h)) * 6f;
		s = Math.max(0, Math.min(1, s));
		b = Math.max(0, Math.min(1, b));
		int i = (int) h;
		float f = h - i, p = b * (1 - s), q = b * (1 - s * f), t = b * (1 - s * (1 - f));
		float r, g, bl;
		switch (i) {
			case 0 -> { r = b; g = t; bl = p; }
			case 1 -> { r = q; g = b; bl = p; }
			case 2 -> { r = p; g = b; bl = t; }
			case 3 -> { r = p; g = q; bl = b; }
			case 4 -> { r = t; g = p; bl = b; }
			default -> { r = b; g = p; bl = q; }
		}
		return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (bl * 255);
	}

	/** RGB → {hue, sat, bri}. */
	public static float[] toHsb(int rgb) {
		float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
		float h = 0;
		if (d > 1e-6f) {
			if (max == r) h = ((g - b) / d) % 6;
			else if (max == g) h = (b - r) / d + 2;
			else h = (r - g) / d + 4;
			h /= 6;
			if (h < 0) h += 1;
		}
		return new float[]{h, max <= 0 ? 0 : d / max, max};
	}

	public static float ease(float t) { t = Math.max(0, Math.min(1, t)); return 1 - (float) Math.pow(1 - t, 3); }

	public static float approach(float cur, float target, float speed) { return cur + (target - cur) * Math.min(1f, speed); }

	/** Экспоненциальное приближение, не зависящее от FPS. */
	public static float damp(float cur, float target, float rate, float dt) {
		return net.primlixe.auroralib.anim.Anim.damp(cur, target, rate, dt);
	}

	// фигуры — из Aurora Lib (таблица вырезов, слитые строки, градиент одной заливкой)
	public static void round(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int color) {
		net.primlixe.auroralib.render.Shapes.round(g, x0, y0, x1, y1, r, color);
	}

	public static void roundGradient(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int top, int bottom) {
		net.primlixe.auroralib.render.Shapes.roundGradient(g, x0, y0, x1, y1, r, top, bottom);
	}

	public static void hGradient(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int left, int right) {
		int steps = Math.max(1, (x1 - x0) / 3);
		for (int i = 0; i < steps; i++) {
			int a = x0 + (x1 - x0) * i / steps, b = x0 + (x1 - x0) * (i + 1) / steps;
			g.fill(a, y0, b, y1, mix(left, right, i / (float) steps));
		}
	}

	public static void roundOutline(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int color) {
		net.primlixe.auroralib.render.Shapes.roundOutline(g, x0, y0, x1, y1, r, color);
	}

	public static void shadow(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, float a) {
		net.primlixe.auroralib.render.Shapes.shadow(g, x0, y0, x1, y1, r, a);
	}

	public static void shadow(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r) { shadow(g, x0, y0, x1, y1, r, 1f); }

	/** «Стеклянная» панель. */
	public static void glass(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r) {
		shadow(g, x0, y0, x1, y1, r);
		roundGradient(g, x0, y0, x1, y1, r, 0xE0161829, 0xE00D0F1A);
		roundOutline(g, x0, y0, x1, y1, r, 0x1EFFFFFF);
		g.fill(x0 + r, y0, x1 - r, y0 + 1, 0x30FFFFFF);
	}

	/** Фон окон мода: тёмная вуаль с мягким акцентным градиентом. */
	public static void backdrop(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float a) {
		g.fill(x0, y0, x1, y1, alpha(0xE806070F, a));
		hGradient(g, x0, y0, x1, y0 + 2, alpha(accent(0), 0.0f), alpha(accent(0.2f), 0.6f * a));
	}

	/** Надпись с переливом по буквам. */
	public static void logo(GuiGraphicsExtractor g, Font font, String text, int x, int y, float scale) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		// буквы ставятся по настоящей ширине строки — ровно, как обычный текст
		for (int i = 0; i < text.length(); i++) {
			String ch = text.substring(i, i + 1);
			int col = ChatConfig.get().accentColor >= 0 ? accent() : hsb(0.72f + i * 0.025f + time() * 0.06f, 0.5f, 1f, 1f);
			g.text(font, ch, font.width(text.substring(0, i)), 0, col, true);
		}
		g.pose().popMatrix();
	}

	public static int pill(GuiGraphicsExtractor g, Font font, int x, int y, String text, int bg, int fg) {
		int w = font.width(text) + 8;
		round(g, x, y, x + w, y + 11, 5, bg);
		g.text(font, text, x + 4, y + 2, fg, false);
		return w;
	}

	public static String trim(Font font, String s, int w) {
		if (uiFont == null) return net.primlixe.auroralib.text.TextCache.trim(font, s, w); // бинарный поиск вместо посимвольного
		if (width(font, s) <= w) return s;
		while (s.length() > 1 && width(font, s + "…") > w) s = s.substring(0, s.length() - 1);
		return s + "…";
	}

	public static void centered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int color) {
		text(g, font, s, cx - width(font, s) / 2, y, color, false);
	}

	// ------------------------------------------------ строгий стиль строк настроек

	/** Высота строки настроек. */
	public static int rowH() { return ChatConfig.get().uiCompact ? 16 : 20; }

	/** Плоская строка: еле заметный фон, при наведении — светлее, слева — полоска выделения. */
	public static void row(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float hover) {
		g.fill(x0, y0, x1, y1, mix(ink(0x14), ink(0x26), hover));
		if (hover > 0.02f) g.fill(x0, y0, x0 + 1, y1, alpha(accent(), hover));
	}

	public static String hex(int rgb) { return String.format("#%06X", rgb & 0xFFFFFF); }

	private UI() {}
}
