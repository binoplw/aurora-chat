package net.primlixe.aurorachat.config;

import net.primlixe.aurorachat.config.ChatConfig.BgStyle;
import net.primlixe.aurorachat.config.ChatConfig.TabStyle;

import java.util.List;
import java.util.function.Consumer;

/** Готовые темы чата: применяются в один клик, потом любую деталь можно подправить. */
public final class ChatThemes {
	public record Theme(String name, String desc, int bg, int bg2, int accent, Consumer<ChatConfig> apply) {}

	private static Theme t(String name, String desc, int bg, int bg2, int accent, Consumer<ChatConfig> extra) {
		return new Theme(name, desc, bg, bg2, accent, c -> {
			ChatConfig d = new ChatConfig();
			// общая основа — значения по умолчанию для вида
			c.bgStyle = d.bgStyle; c.bgOpacity = d.bgOpacity; c.bgOpacityClosed = d.bgOpacityClosed; c.radius = d.radius;
			c.outline = d.outline; c.outlineOpacity = d.outlineOpacity; c.shadow = d.shadow; c.textShadow = d.textShadow;
			c.bgGradient = false; c.textColor = 0xFFFFFF; c.textOpacity = 1f; c.stripes = true; c.stripeWidth = 2;
			c.zebra = false; c.separators = false; c.rowPadding = 1; c.messageGap = 1; c.bubbleRadius = 5;
			c.tabStyle = TabStyle.PILLS; c.timePos = d.timePos; c.timestampColor = d.timestampColor; c.titleBar = d.titleBar;
			c.layout = ChatConfig.Layout.COMPACT; c.heads = true; c.headSize = 8; c.boldNames = false; c.icons = true;
			c.bgColor = bg; c.bgColor2 = bg2; c.accentColor = accent;
			extra.accept(c);
			c.theme = name;
		});
	}

	public static final List<Theme> ALL = List.of(
			t("Деловой", "графит, строгие линии, время слева, заголовок окна", 0x14161B, 0x1D2028, 0x7FA7D9, c -> {
				c.bgGradient = true; c.bgOpacity = 0.9f; c.bgOpacityClosed = 0.5f; c.radius = 4;
				c.outlineOpacity = 0.28f; c.stripes = true; c.stripeWidth = 1; c.separators = true;
				c.rowPadding = 2; c.messageGap = 0; c.textShadow = false; c.textColor = 0xE4E7EE;
				c.tabStyle = TabStyle.UNDERLINE; c.timePos = ChatConfig.TimePos.LEFT; c.timestampColor = 0x6F7787;
				c.titleBar = true; c.hoverHighlight = true;
			}),
			// ---- в стиле соцсетей и мессенджеров
			t("Discord", "ник и время над сообщением, аватарки, группы сообщений", 0x313338, 0x2B2D31, 0x5865F2, c -> {
				c.layout = ChatConfig.Layout.COZY; c.bgGradient = true; c.bgOpacity = 0.95f; c.bgOpacityClosed = 0.6f; c.radius = 6;
				c.outline = false; c.stripes = false; c.textShadow = false; c.textColor = 0xDBDEE1; c.timestampColor = 0x949BA4;
				c.tabStyle = TabStyle.UNDERLINE; c.rowPadding = 2; c.messageGap = 1;
			}),
			t("Telegram", "пузыри: свои справа синим, чужие слева с аватаркой", 0x0E1621, 0x17212B, 0x3E6A9C, c -> {
				c.layout = ChatConfig.Layout.MESSENGER; c.bgGradient = true; c.bgOpacity = 0.92f; c.bgOpacityClosed = 0.45f;
				c.outline = false; c.bubbleRadius = 7; c.textShadow = false; c.textColor = 0xF5F5F5; c.timestampColor = 0x6D7F8F;
				c.tabStyle = TabStyle.UNDERLINE;
			}),
			t("WhatsApp", "тёмно-зелёные пузыри, свои — изумрудные", 0x0B141A, 0x111B21, 0x00A884, c -> {
				c.layout = ChatConfig.Layout.MESSENGER; c.bgGradient = true; c.bgOpacity = 0.92f; c.bgOpacityClosed = 0.45f;
				c.outline = false; c.bubbleRadius = 6; c.textShadow = false; c.textColor = 0xE9EDEF; c.timestampColor = 0x8696A0;
				c.tabStyle = TabStyle.UNDERLINE;
			}),
			t("iMessage", "чёрный фон, круглые синие пузыри", 0x000000, 0x111111, 0x0A84FF, c -> {
				c.layout = ChatConfig.Layout.MESSENGER; c.bgOpacity = 0.85f; c.bgOpacityClosed = 0.4f; c.outline = false;
				c.bubbleRadius = 10; c.radius = 10; c.textShadow = false; c.timestampColor = 0x8E8E93; c.tabStyle = TabStyle.TEXT;
			}),
			t("Twitch", "плотный стрим-чат, жирные цветные ники", 0x18181B, 0x0E0E10, 0x9147FF, c -> {
				c.bgGradient = true; c.bgOpacity = 0.9f; c.radius = 4; c.outline = false; c.stripes = false; c.heads = false;
				c.boldNames = true; c.timePos = ChatConfig.TimePos.LEFT; c.timestampColor = 0xADADB8; c.textColor = 0xEFEFF1;
				c.textShadow = false; c.rowPadding = 1; c.messageGap = 0; c.tabStyle = TabStyle.UNDERLINE;
			}),
			t("VK", "спокойный тёмный, голубой акцент, ник над текстом", 0x19191A, 0x222222, 0x71AAEB, c -> {
				c.layout = ChatConfig.Layout.COZY; c.bgGradient = true; c.bgOpacity = 0.92f; c.radius = 8; c.outline = false;
				c.stripes = false; c.textShadow = false; c.textColor = 0xE1E3E6; c.timestampColor = 0x76787A; c.tabStyle = TabStyle.UNDERLINE;
			}),
			t("Slack", "рабочий: ник над текстом, строгие линии", 0x1A1D21, 0x19171D, 0x1D9BD1, c -> {
				c.layout = ChatConfig.Layout.COZY; c.bgOpacity = 0.94f; c.radius = 4; c.outlineOpacity = 0.2f; c.stripes = false;
				c.separators = false; c.textShadow = false; c.textColor = 0xD1D2D3; c.timestampColor = 0x9A9B9E; c.tabStyle = TabStyle.UNDERLINE;
			}),
			t("YouTube", "как чат трансляции: время слева, аватарки", 0x0F0F0F, 0x0F0F0F, 0xFF0033, c -> {
				c.bgOpacity = 0.9f; c.radius = 8; c.outline = false; c.stripes = false; c.headSize = 10; c.rowPadding = 2;
				c.timePos = ChatConfig.TimePos.LEFT; c.timestampColor = 0x717171; c.textShadow = false; c.textColor = 0xF1F1F1; c.tabStyle = TabStyle.TEXT;
			}),
			t("Steam", "сине-стальной, разделители", 0x1B2838, 0x171A21, 0x66C0F4, c -> {
				c.bgGradient = true; c.bgOpacity = 0.9f; c.radius = 2; c.outlineOpacity = 0.3f; c.separators = true; c.stripes = false;
				c.textShadow = false; c.textColor = 0xC7D5E0; c.timestampColor = 0x8F98A0; c.tabStyle = TabStyle.UNDERLINE;
			}),
			t("Spotify", "графит и зелёный, ник над сообщением", 0x121212, 0x181818, 0x1DB954, c -> {
				c.layout = ChatConfig.Layout.COZY; c.bgGradient = true; c.bgOpacity = 0.93f; c.radius = 8; c.outline = false;
				c.stripes = false; c.textShadow = false; c.textColor = 0xFFFFFF; c.timestampColor = 0xA7A7A7; c.tabStyle = TabStyle.PILLS;
			}),
			t("Терминал", "зелёный текст на чёрном, без лишнего", 0x000000, 0x000000, 0x33FF66, c -> {
				c.bgOpacity = 0.8f; c.bgOpacityClosed = 0.5f; c.radius = 0; c.outlineOpacity = 0.5f; c.stripes = false; c.heads = false;
				c.textColor = 0x33FF66; c.timestampColor = 0x1E9E40; c.timePos = ChatConfig.TimePos.LEFT; c.textShadow = false;
				c.tabStyle = TabStyle.TEXT; c.rowPadding = 0; c.messageGap = 0;
			}),
			// ---- свои
			t("Aurora", "тёмное стекло и переливающийся акцент", 0x0B0C16, 0x1E0F2E, -1, c -> {}),
			t("Полночь", "глубокий синий с мягким градиентом", 0x070B18, 0x13254A, 0x6CB4FF, c -> { c.bgGradient = true; c.bgOpacity = 0.85f; }),
			t("Неон", "чёрный фон, яркая розовая обводка", 0x05050A, 0x05050A, 0xFF4FD8, c -> { c.outlineOpacity = 0.9f; c.radius = 4; c.stripeWidth = 3; }),
			t("Закат", "тёплый градиент от сливы к янтарю", 0x2A1010, 0x2B1430, 0xFFB347, c -> { c.bgGradient = true; c.bgOpacity = 0.8f; }),
			t("Лес", "изумрудный, спокойный", 0x0C1F16, 0x0F2A1C, 0x5BE39A, c -> c.bgGradient = true),
			t("Пузыри", "у каждого сообщения свой фон", 0x141626, 0x141626, 0xB18CFF, c -> {
				c.bgStyle = BgStyle.BUBBLES; c.bgOpacity = 0.75f; c.bgOpacityClosed = 0.55f; c.messageGap = 2; c.bubbleRadius = 7; c.rowPadding = 2;
			}),
			t("Минимал", "без фона, только текст с тенью", 0x000000, 0x000000, 0xFFFFFF, c -> {
				c.bgStyle = BgStyle.MINIMAL; c.outline = false; c.stripes = false; c.tabStyle = TabStyle.UNDERLINE;
			}),
			t("Классика", "почти как ванильный чат", 0x000000, 0x000000, 0xAAAAAA, c -> {
				c.radius = 0; c.outline = false; c.shadow = false; c.stripes = false; c.bgOpacity = 0.5f; c.bgOpacityClosed = 0.4f;
				c.tabStyle = TabStyle.TEXT; c.rowPadding = 0; c.messageGap = 0;
			}),
			t("Светлое стекло", "светлая полупрозрачная панель", 0xD8DCEB, 0xF2F3FA, 0x5A6CFF, c -> {
				c.bgOpacity = 0.35f; c.bgOpacityClosed = 0.2f; c.bgGradient = true; c.textShadow = true; c.zebra = true;
			}),
			// ---- ещё темы
			t("Сакура", "нежно-розовый градиент", 0x1F0E16, 0x3A1828, 0xFF8FB1, c -> { c.bgGradient = true; c.bgOpacity = 0.85f; c.radius = 8; c.textColor = 0xFFEAF1; }),
			t("Океан", "бирюзовая глубина", 0x03121A, 0x0A3344, 0x33D6FF, c -> { c.bgGradient = true; c.bgOpacity = 0.85f; c.radius = 7; c.textColor = 0xE6FBFF; }),
			t("Кофе", "тёплый коричневый, ник над текстом", 0x1A120C, 0x2A1E14, 0xE0A060, c -> {
				c.layout = ChatConfig.Layout.COZY; c.bgGradient = true; c.bgOpacity = 0.9f; c.textColor = 0xFFF4E6; c.timestampColor = 0xA08E7A; c.outline = false; c.stripes = false;
			}),
			t("Вапорвейв", "розово-голубой ретро", 0x1A0A2E, 0x0A2A3E, 0xFF71CE, c -> { c.bgGradient = true; c.bgOpacity = 0.85f; c.outlineOpacity = 0.8f; c.radius = 2; c.textColor = 0xF0F0FF; }),
			t("Золото", "чёрный с золотой обводкой", 0x0A0804, 0x1A140A, 0xFFC53D, c -> { c.bgGradient = true; c.bgOpacity = 0.9f; c.outlineOpacity = 0.7f; c.radius = 3; c.textColor = 0xFFF6DA; c.boldNames = true; }),
			t("Рубин", "тёмно-красный, пузыри", 0x1A0508, 0x2E0A10, 0xFF4F6A, c -> { c.bgStyle = BgStyle.BUBBLES; c.bgOpacity = 0.8f; c.bubbleRadius = 7; c.messageGap = 2; c.textColor = 0xFFE8EC; }),
			t("Арктика", "холодный голубой, мессенджер", 0x0E1A26, 0x1A2E40, 0x9CE0FF, c -> {
				c.layout = ChatConfig.Layout.MESSENGER; c.bgGradient = true; c.bgOpacity = 0.88f; c.outline = false; c.bubbleRadius = 8; c.textColor = 0xF0FAFF; c.textShadow = false;
			}),
			t("Матрица", "зелёные строки, без углов", 0x000A02, 0x001A06, 0x00FF41, c -> {
				c.bgOpacity = 0.85f; c.radius = 0; c.textColor = 0x7CFF9E; c.timestampColor = 0x0A8A2A; c.heads = false; c.stripes = false; c.textShadow = false; c.tabStyle = TabStyle.TEXT;
			}),
			t("Лаванда", "сиреневое стекло", 0x14101F, 0x2A2140, 0xC7A6FF, c -> { c.bgGradient = true; c.bgOpacity = 0.8f; c.radius = 9; c.textColor = 0xF3EEFF; c.zebra = true; }),
			t("Уголь", "плотный чёрный, оранжевый акцент", 0x0A0A0A, 0x141414, 0xFF7A1F, c -> { c.bgOpacity = 0.95f; c.radius = 2; c.outlineOpacity = 0.35f; c.separators = true; c.textShadow = false; c.stripes = false; })
	);

	public static void apply(Theme t) {
		t.apply().accept(ChatConfig.get());
		ChatConfig.save();
	}

	private ChatThemes() {}
}
