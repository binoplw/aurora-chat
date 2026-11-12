package net.primlixe.aurorachat.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.primlixe.aurorachat.config.ChatConfig;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Свои имена для ников — видны только тебе: «Steve» в чате становится «Стёпа», при наведении — настоящий ник. */
public final class Nicknames {

	/** Как показывать ник (свой вариант или сам ник). */
	public static String display(String nick) {
		if (nick == null) return null;
		ChatConfig c = ChatConfig.get();
		if (!c.nicknamesEnabled) return nick;
		String a = c.nicknames.get(nick.toLowerCase(Locale.ROOT));
		return a == null || a.isBlank() ? nick : a;
	}

	public static void set(String nick, String alias) {
		ChatConfig c = ChatConfig.get();
		if (alias == null || alias.isBlank() || alias.equals(nick)) c.nicknames.remove(nick.toLowerCase(Locale.ROOT));
		else c.nicknames.put(nick.toLowerCase(Locale.ROOT), alias.trim());
		ChatConfig.save();
	}

	/** Заменить все ники из списка в тексте сообщения (стили сохраняются, при наведении — настоящий ник). */
	public static Component apply(Component src) {
		ChatConfig c = ChatConfig.get();
		if (!c.nicknamesEnabled || c.nicknames.isEmpty()) return src;
		String plain = src.getString().toLowerCase(Locale.ROOT);
		Component out = src;
		for (Map.Entry<String, String> e : c.nicknames.entrySet()) {
			if (!plain.contains(e.getKey())) continue;
			out = replaceWord(out, e.getKey(), e.getValue());
		}
		return out;
	}

	private static Component replaceWord(Component src, String word, String repl) {
		Pattern p = net.primlixe.auroralib.text.Regex.of("(?<![A-Za-z0-9_])" + Pattern.quote(word) + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
		MutableComponent out = Component.empty();
		src.visit((style, text) -> {
			if (text.isEmpty()) return Optional.empty();
			Matcher m = p.matcher(text);
			int last = 0;
			while (m.find()) {
				if (m.start() > last) out.append(Component.literal(text.substring(last, m.start())).withStyle(style));
				var st = style.getHoverEvent() == null ? style.withHoverEvent(new HoverEvent.ShowText(Component.literal("Настоящий ник: " + m.group()))) : style;
				out.append(Component.literal(repl).withStyle(st));
				last = m.end();
			}
			if (last < text.length()) out.append(Component.literal(text.substring(last)).withStyle(style));
			return Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		return out;
	}

	private Nicknames() {}
}
