package net.primlixe.aurorachat.config;

import java.util.List;

/** Готовые темы окна настроек Aurora Chat: фон, панели, текст, акцент, скругление. */
public final class UiThemes {
	/** light — светлая тема (тёмный текст, чёрные «чернила» строк). */
	public record Theme(String name, String desc, int bg, int panel, float panelAlpha, int text, int sub, int muted, int accent, int radius, boolean light) {}

	private static Theme dark(String n, String d, int bg, int panel, float a, int text, int sub, int muted, int accent, int r) {
		return new Theme(n, d, bg, panel, a, text, sub, muted, accent, r, false);
	}

	private static Theme light(String n, String d, int bg, int panel, int text, int sub, int muted, int accent, int r) {
		return new Theme(n, d, bg, panel, 0.97f, text, sub, muted, accent, r, true);
	}

	public static final List<Theme> ALL = List.of(
			dark("Графит", "строгий тёмный, синий акцент", 0x08090C, 0x0E1015, 0.69f, 0xF2F3FA, 0x9EA3BC, 0x6A6F88, 0x4F8EF7, 3),
			dark("Полночь", "глубокий синий", 0x050816, 0x0B1230, 0.82f, 0xE8EEFF, 0x8FA0D0, 0x5A6890, 0x6CB4FF, 5),
			dark("Аврора", "фиолетовое стекло", 0x0B0C16, 0x1A1030, 0.8f, 0xF5EEFF, 0xB5A3D9, 0x7A6A9A, 0xB18CFF, 6),
			dark("Discord", "как в Discord", 0x1E1F22, 0x2B2D31, 0.96f, 0xF2F3F5, 0xB5BAC1, 0x80848E, 0x5865F2, 4),
			dark("Telegram", "сине-серый, мягкие углы", 0x0E1621, 0x17212B, 0.96f, 0xF5F5F5, 0x8B9AA8, 0x6D7F8F, 0x3E9BFF, 6),
			dark("Spotify", "чёрный и зелёный", 0x000000, 0x121212, 0.96f, 0xFFFFFF, 0xB3B3B3, 0x727272, 0x1DB954, 6),
			dark("Twitch", "тёмный с фиолетовым", 0x0E0E10, 0x18181B, 0.96f, 0xEFEFF1, 0xADADB8, 0x7A7A85, 0x9147FF, 4),
			dark("Steam", "сине-стальной", 0x171A21, 0x1B2838, 0.96f, 0xC7D5E0, 0x8F98A0, 0x67707B, 0x66C0F4, 2),
			dark("Терминал", "зелёный на чёрном, без углов", 0x000000, 0x000000, 0.92f, 0x33FF66, 0x22BB44, 0x167A2C, 0x33FF66, 0),
			dark("Неон", "чёрный и розовый неон", 0x05050A, 0x0A0A14, 0.92f, 0xFFFFFF, 0xFF9AE8, 0x9A5A8F, 0xFF4FD8, 4),
			dark("Киберпанк", "жёлтый и бирюзовый", 0x0A0014, 0x12002A, 0.92f, 0xF8F000, 0x00F0FF, 0x9A70C0, 0xFF00A0, 0),
			dark("Закат", "тёплый тёмно-красный", 0x1A0A0A, 0x2A1414, 0.88f, 0xFFF1E6, 0xF2B999, 0xA8786A, 0xFF8A4A, 6),
			dark("Вишня", "тёмный с вишнёвым", 0x14060B, 0x250C16, 0.88f, 0xFFEAF1, 0xE3A3B8, 0x9A6676, 0xFF5A8A, 6),
			dark("Лес", "изумрудный", 0x06120C, 0x0C1F16, 0.88f, 0xEAFFF2, 0x9CC9AD, 0x628A72, 0x5BE39A, 5),
			dark("Океан", "морской бирюзовый", 0x03121A, 0x082733, 0.88f, 0xE6FBFF, 0x8CC6D6, 0x5A8C9A, 0x33D6FF, 6),
			dark("Шоколад", "коричневый и карамель", 0x120C08, 0x22170F, 0.92f, 0xFFF4E6, 0xD9BFA3, 0x99806A, 0xE0A060, 4),
			dark("Золото", "чёрный с золотом", 0x0A0804, 0x17130A, 0.92f, 0xFFF6DA, 0xE0C98A, 0x9A8A5A, 0xFFC53D, 3),
			dark("Монохром", "чёрно-белый, без цвета", 0x000000, 0x111111, 0.94f, 0xFFFFFF, 0xAAAAAA, 0x666666, 0xFFFFFF, 0),
			dark("Стекло", "прозрачные панели поверх игры", 0x0A0C14, 0x1A1E2E, 0.45f, 0xFFFFFF, 0xC8CCDD, 0x8A90A6, 0x9CC4FF, 8),
			light("Светлая", "белые панели, синий акцент", 0xF2F3F7, 0xFFFFFF, 0x1B1E28, 0x4A5068, 0x8A90A6, 0x4F8EF7, 6),
			light("Крем", "тёплый светлый", 0xF6F0E6, 0xFFFBF4, 0x2B2118, 0x6B5A48, 0xA08E7A, 0xD9822B, 6),
			light("Мята", "светло-зелёный", 0xEEF7F2, 0xFFFFFF, 0x14261C, 0x3F6A54, 0x7FA08C, 0x2EAD6B, 6),
			light("Розовая", "светлая с розовым", 0xFFF0F5, 0xFFFFFF, 0x3A1424, 0x8A4A66, 0xB88A9E, 0xFF4F8A, 8),
			light("Небо", "светло-голубая", 0xEAF4FF, 0xFFFFFF, 0x0E2238, 0x3E5E80, 0x7F9AB8, 0x2F80ED, 6));

	public static void apply(Theme t) {
		ChatConfig c = ChatConfig.get();
		c.uiBg = t.bg(); c.uiPanel = t.panel(); c.uiPanelAlpha = t.panelAlpha();
		c.uiText = t.text(); c.uiSub = t.sub(); c.uiMuted = t.muted();
		c.uiAccent = t.accent(); c.uiFollowAccent = false; c.uiRadius = t.radius();
		c.uiInk = t.light() ? 0x000000 : 0xFFFFFF;
		// светлая тема на почти чёрной вуали смотрится плохо — вуаль слабее
		if (t.light()) c.uiDim = Math.min(c.uiDim, 0.6f);
		c.uiTheme = t.name();
		ChatConfig.save();
	}

	private UiThemes() {}
}
