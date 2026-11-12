package net.primlixe.aurorachat.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.primlixe.aurorachat.config.ChatConfig;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Антиспам: прячет повтор одного и того же сообщения от игрока за короткое время и флуд
 * (слишком много сообщений подряд). По желанию «гасит» капс. Считает, сколько скрыто.
 */
public final class AntiSpam {
	private record Entry(long time, String text) {}

	private static final Map<String, ArrayDeque<Entry>> HISTORY = new HashMap<>();
	private static int hidden;
	private static String lastHiddenFrom;

	public static int hiddenCount() { return hidden; }

	public static String lastHiddenFrom() { return lastHiddenFrom; }

	/** true — сообщение скрыть. body — текст без ника. */
	public static boolean blocked(String sender, String body) {
		ChatConfig c = ChatConfig.get();
		if (!c.antiSpam || sender == null) return false;
		long now = System.currentTimeMillis();
		String key = sender.toLowerCase(Locale.ROOT);
		String norm = body.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
		ArrayDeque<Entry> q = HISTORY.computeIfAbsent(key, k -> new ArrayDeque<>());
		long keep = Math.max(c.spamRepeatSeconds, c.floodSeconds) * 1000L;
		while (!q.isEmpty() && now - q.peekFirst().time() > keep) q.pollFirst();
		boolean block = false;
		// повтор (не подряд — подряд склеивается в ×N)
		if (c.spamRepeatSeconds > 0) {
			for (Iterator<Entry> it = q.descendingIterator(); it.hasNext(); ) {
				Entry e = it.next();
				if (now - e.time() > c.spamRepeatSeconds * 1000L) break;
				if (e.text().equals(norm)) { block = true; break; }
			}
		}
		// флуд
		int recent = 0;
		for (Entry e : q) if (now - e.time() <= c.floodSeconds * 1000L) recent++;
		if (recent >= c.floodLimit) block = true;
		q.addLast(new Entry(now, norm));
		if (HISTORY.size() > 500) HISTORY.clear();
		if (block) {
			hidden++;
			lastHiddenFrom = sender;
		}
		return block;
	}

	/** Сообщение КАПСОМ (больше 70% заглавных из 8+ букв) — в нижний регистр. */
	public static Component calmCaps(Component c) {
		if (!ChatConfig.get().antiCaps) return c;
		String s = c.getString();
		int letters = 0, upper = 0;
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (Character.isLetter(ch)) { letters++; if (Character.isUpperCase(ch)) upper++; }
		}
		if (letters < 8 || upper < letters * 0.7f) return c;
		MutableComponent out = Component.empty();
		c.visit((style, text) -> {
			out.append(Component.literal(text.toLowerCase(Locale.ROOT)).withStyle(style));
			return Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		return out;
	}

	private AntiSpam() {}
}
