package net.primlixe.aurorachat.chat;

import net.minecraft.network.chat.Component;
import net.primlixe.aurorachat.config.ChatConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Журнал «консоли»: всё, что происходит с чатом и сервером, в одном месте —
 * входящие (в том числе скрытые фильтрами, с причиной), твои сообщения и команды,
 * входы/выходы из таба, персонал, события мода, подключения.
 */
public final class ConsoleLog {
	public enum Level {
		CHAT("ЧАТ", 0xFF8FB8FF), SYS("СИС", 0xFFE0C36A), OUT("Я", 0xFF6CE38A), CMD("КМД", 0xFF5BE3D6),
		HIDE("СКРЫТО", 0xFF8A8FA6), JOIN("ВХОД", 0xFF5BE39A), LEAVE("ВЫХОД", 0xFFFF8A8A),
		STAFF("ПЕРСОНАЛ", 0xFFFF5470), MOD("МОД", 0xFFB18CFF), NET("СЕТЬ", 0xFFFFB347);
		public final String tag;
		public final int color;
		Level(String tag, int color) { this.tag = tag; this.color = color; }
	}

	/** legacy — текст с §-кодами (цвета сообщения сохраняются). */
	public record Line(long time, Level level, String legacy, String plain) {}

	private static final ArrayDeque<Line> LINES = new ArrayDeque<>();
	private static int version;

	public static void add(Level level, Component c) {
		if (c == null) return;
		add(level, ChatText.legacy(c), c.getString());
	}

	public static void add(Level level, String text) { add(level, text, ChatText.strip(text)); }

	private static synchronized void add(Level level, String legacy, String plain) {
		if (!ChatConfig.get().consoleLog) return;
		LINES.addLast(new Line(System.currentTimeMillis(), level, legacy.replace("\n", " ⏎ "), plain.replace("\n", " ⏎ ")));
		int limit = ChatConfig.get().consoleLimit;
		while (LINES.size() > limit) LINES.pollFirst();
		version++;
	}

	public static synchronized List<Line> snapshot() { return new ArrayList<>(LINES); }

	/** Растёт при каждой новой строке — экран консоли понимает, что пора обновиться. */
	public static int version() { return version; }

	public static synchronized void clear() { LINES.clear(); version++; }

	/** Скрыть пароли в командах входа: /login, /l, /register, /reg, /changepassword. */
	public static String mask(String cmd) {
		String low = cmd.toLowerCase(java.util.Locale.ROOT);
		// тот же список, что не пишется в историю ввода (InputHistory.SECRET)
		for (String k : new String[]{"/login ", "/l ", "/log ", "/register ", "/reg ", "/changepassword ", "/changepass ", "/cp ", "/unregister ", "/2fa ", "/totp "}) {
			if (low.startsWith(k)) return cmd.substring(0, k.length()) + "******";
		}
		return cmd;
	}

	private ConsoleLog() {}
}
