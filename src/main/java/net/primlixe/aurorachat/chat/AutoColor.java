package net.primlixe.aurorachat.chat;

import net.primlixe.aurorachat.config.ChatConfig;

import java.util.Locale;

/**
 * Автоцвет исходящих сообщений: к тексту сам добавляется код цвета («&a» и т.п.).
 * ЛС (/m, /msg, /w, /tell, /r …) — свой цвет, глобальный и локальный чат — свои.
 * Если игрок уже начал текст с & или § — ничего не трогаем.
 */
public final class AutoColor {
	/** Готовые варианты для настроек: код → подпись (подпись уже окрашена). */
	public static final String[][] PRESETS = {
			{"", "Без цвета"}, {"&a", "§aЗелёный &a"}, {"&2", "§2Тёмно-зелёный &2"}, {"&b", "§bГолубой &b"}, {"&3", "§3Бирюзовый &3"},
			{"&9", "§9Синий &9"}, {"&d", "§dРозовый &d"}, {"&5", "§5Фиолетовый &5"}, {"&c", "§cКрасный &c"}, {"&4", "§4Тёмно-красный &4"},
			{"&6", "§6Золотой &6"}, {"&e", "§eЖёлтый &e"}, {"&f", "§fБелый &f"}, {"&7", "§7Серый &7"}, {"&8", "§8Тёмно-серый &8"},
			{"&a&l", "§a§lЗелёный жирный"}, {"&e&l", "§e§lЖёлтый жирный"}, {"&b&o", "§b§oГолубой курсив"}, {"&d&l", "§d§lРозовый жирный"},
	};

	public static int indexOf(String code) {
		for (int i = 0; i < PRESETS.length; i++) if (PRESETS[i][0].equalsIgnoreCase(code == null ? "" : code)) return i;
		return 0;
	}

	/** Код градиента, которым сейчас пишет игрок, или null. */
	public static String writeGradient() {
		ChatConfig c = ChatConfig.get();
		if (!c.hexColors || c.writeGradient == null || c.writeGradient.isBlank()) return null;
		return ColorCodes.colors(c.writeGradient) != null ? "&{" + c.writeGradient + "}" : null;
	}

	public static String apply(String msg) {
		ChatConfig c = ChatConfig.get();
		String grad = writeGradient();
		if (!c.enabled || msg.isBlank() || (!c.autoColor && grad == null)) return msg;
		if (msg.startsWith("/")) return command(c, msg, grad);
		String code = grad != null ? grad : c.channels && c.channel.equals("G") ? c.colorGlobal : c.colorLocal;
		// игрок сам указал префикс канала — красим текст после него
		for (String p : new String[]{c.globalPrefix, c.localPrefix}) {
			if (p != null && !p.isEmpty() && msg.startsWith(p)) {
				String code2 = grad != null ? grad : p.equals(c.globalPrefix) ? c.colorGlobal : c.colorLocal;
				return p + paint(msg.substring(p.length()), code2);
			}
		}
		return paint(msg, code);
	}

	private static String command(ChatConfig c, String msg, String grad) {
		String dm = grad != null && c.writeGradientDm ? grad : c.autoColor ? c.colorDm : null;
		if (dm == null || dm.isEmpty()) return msg;
		String body = msg.substring(1);
		int sp = body.indexOf(' ');
		if (sp <= 0) return msg;
		String cmd = body.substring(0, sp).toLowerCase(Locale.ROOT);
		if (!c.dmCommands.contains(cmd)) return msg;
		String rest = body.substring(sp + 1);
		if (c.replyCommands.contains(cmd)) return "/" + body.substring(0, sp) + " " + paint(rest, dm);
		int sp2 = rest.indexOf(' ');
		if (sp2 <= 0) return msg; // только ник, без текста
		return "/" + body.substring(0, sp) + " " + rest.substring(0, sp2) + " " + paint(rest.substring(sp2 + 1), dm);
	}

	private static String paint(String text, String code) {
		if (code == null || code.isEmpty() || text.isEmpty()) return text;
		if (text.startsWith("&") || text.startsWith("§")) return text;
		return code + text;
	}

	private AutoColor() {}
}
