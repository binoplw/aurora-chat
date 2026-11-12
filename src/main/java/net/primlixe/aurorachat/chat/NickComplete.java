package net.primlixe.aurorachat.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.primlixe.aurorachat.config.ChatConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Автодополнение ников по Tab: сначала те, кто недавно писал в чат, потом все из таба и список персонала.
 * Работает в обычном тексте (в том числе после «@») и в первом аргументе команд ЛС (/m Ник …).
 */
public final class NickComplete {
	private static final Pattern NICK = Pattern.compile("[\\p{L}\\p{N}_]{2,16}");
	private static List<String> cache = List.of();
	private static long cacheAt;

	/** Все известные ники (кэш на полсекунды). */
	public static List<String> names() {
		long now = System.currentTimeMillis();
		if (now - cacheAt < 500) return cache;
		cacheAt = now;
		LinkedHashSet<String> out = new LinkedHashSet<>();
		Minecraft mc = Minecraft.getInstance();
		try {
			int n = 0;
			for (GuiMessage m : ((net.primlixe.aurorachat.mixin.ChatComponentAccessor) net.primlixe.aurorachat.compat.Mc.chat()).aurorachat$all()) {
				if (n++ > 400) break;
				String s = ChatCore.meta(m.content()).sender();
				if (s != null && NICK.matcher(s).matches()) out.add(s);
			}
		} catch (Exception ignored) { }
		if (mc.getConnection() != null) {
			for (PlayerInfo p : mc.getConnection().getOnlinePlayers()) out.add(p.getProfile().name());
		}
		for (ChatConfig.Staff s : ChatConfig.get().staff) if (NICK.matcher(s.nick).matches()) out.add(s.nick);
		cache = new ArrayList<>(out);
		return cache;
	}

	public static void invalidate() { cacheAt = 0; }

	/** Начало слова под курсором. */
	public static int wordStart(String text, int cursor) {
		int i = Math.min(cursor, text.length());
		while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) i--;
		return i;
	}

	/** Можно ли дописывать ник в этом месте строки. */
	public static boolean applicable(String text, int start) {
		if (!text.startsWith("/")) return true;
		int sp = text.indexOf(' ');
		if (sp < 0 || start != sp + 1) return false; // только первый аргумент
		String cmd = text.substring(1, sp).toLowerCase(Locale.ROOT);
		ChatConfig c = ChatConfig.get();
		return c.dmCommands.contains(cmd) && !c.replyCommands.contains(cmd);
	}

	/** Ники, начинающиеся с prefix (без учёта регистра). */
	public static List<String> matches(String prefix) {
		List<String> out = new ArrayList<>();
		if (prefix.isEmpty()) return out;
		String p = prefix.toLowerCase(Locale.ROOT);
		for (String n : names()) if (n.toLowerCase(Locale.ROOT).startsWith(p)) out.add(n);
		return out;
	}

	/** Серая подсказка: что дописать к слову перед курсором; null — нечего. */
	public static String ghost(String text, int cursor) {
		if (cursor != text.length()) return null;
		int start = wordStart(text, cursor);
		if (!applicable(text, start)) return null;
		String word = text.substring(start);
		String pre = word.startsWith("@") ? word.substring(1) : word;
		if (pre.length() < 2) return null;
		List<String> m = matches(pre);
		if (m.isEmpty() || m.get(0).length() <= pre.length()) return null;
		return m.get(0).substring(pre.length());
	}

	private NickComplete() {}
}
