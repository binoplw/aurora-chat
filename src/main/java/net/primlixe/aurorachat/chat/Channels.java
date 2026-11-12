package net.primlixe.aurorachat.chat;

import net.primlixe.aurorachat.config.ChatConfig;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Каналы чата «как на серверах»: G — глобальный, L — локальный.
 * Входящие: канал узнаётся по тегу в начале строки ([G], [Г], (L), Ⓛ, «G |» …) — шаблоны настраиваются.
 * Исходящие: к сообщению добавляется префикс канала (по умолчанию «!» для глобального).
 */
public final class Channels {
	private static String gSrc, lSrc;
	private static Pattern g, l;

	private static void compile() {
		ChatConfig c = ChatConfig.get();
		if (!c.globalPattern.equals(gSrc)) { g = safe(c.globalPattern); gSrc = c.globalPattern; }
		if (!c.localPattern.equals(lSrc)) { l = safe(c.localPattern); lSrc = c.localPattern; }
	}

	private static Pattern safe(String p) {
		try { return Pattern.compile(p, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE); } catch (Exception e) { return null; }
	}

	/** 'G', 'L' или 0. */
	public static char detect(String plain) {
		if (!ChatConfig.get().channels) return 0;
		compile();
		if (g != null && g.matcher(plain).find()) return 'G';
		if (l != null && l.matcher(plain).find()) return 'L';
		return 0;
	}

	/** Длина тега канала в начале строки (вместе с пробелами после него), 0 — тега нет. */
	public static int tagLength(String plain) {
		if (!ChatConfig.get().channels) return 0;
		compile();
		for (Pattern p : new Pattern[]{g, l}) {
			if (p == null) continue;
			Matcher m = p.matcher(plain);
			if (m.find() && m.start() <= 2) {
				int end = m.end();
				while (end < plain.length() && Character.isWhitespace(plain.charAt(end))) end++;
				return end;
			}
		}
		return 0;
	}

	public static String stripTag(String plain) { return plain.substring(tagLength(plain)); }

	public static int color(char ch) {
		ChatConfig c = ChatConfig.get();
		return 0xFF000000 | (ch == 'G' ? c.globalColor : c.localColor);
	}

	public static String name(char ch) { return ch == 'G' ? "Глобальный" : "Локальный"; }

	/** Своя надпись канала на метках («G» или то, что задал игрок). */
	public static String label(char ch) {
		ChatConfig c = ChatConfig.get();
		String s = ch == 'G' ? c.gLabel : c.lLabel;
		return s == null || s.isBlank() ? String.valueOf(ch) : s;
	}

	/** Цвет надписи на метке: свой или авто (тёмный на светлом фоне, белый на тёмном). */
	public static int labelColor(int bg) {
		int c = ChatConfig.get().badgeTextColor;
		if (c >= 0) return 0xFF000000 | c;
		int r = (bg >> 16) & 255, g = (bg >> 8) & 255, b = bg & 255;
		return (r * 299 + g * 587 + b * 114) / 1000 > 140 ? 0xFF0B0C10 : 0xFFFFFFFF;
	}

	/** Префикс для исходящего сообщения в текущем канале (команды не трогаем). */
	public static String applyOutgoing(String msg) {
		ChatConfig c = ChatConfig.get();
		if (!c.enabled || !c.channels || msg.isEmpty() || msg.startsWith("/")) return msg;
		String prefix = c.channel.equals("G") ? c.globalPrefix : c.localPrefix;
		String other = c.channel.equals("G") ? c.localPrefix : c.globalPrefix;
		if (prefix == null || prefix.isEmpty()) return msg;
		if (msg.startsWith(prefix)) return msg;
		// игрок явно написал префикс другого канала — уважаем его выбор
		if (other != null && !other.isEmpty() && msg.startsWith(other)) return msg;
		return prefix + msg;
	}

	public static void toggle() {
		ChatConfig c = ChatConfig.get();
		c.channel = c.channel.equals("G") ? "L" : "G";
		ChatConfig.save();
	}

	private Channels() {}
}
