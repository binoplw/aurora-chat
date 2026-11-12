package net.primlixe.aurorachat.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.primlixe.aurorachat.ui.UI;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Разбор и украшение сообщений: категории, каналы G/L, ЛС, входы/выходы, ссылки, ники, эмодзи. */
public final class ChatFormat {
	public enum Category {
		PLAYER(0xFF6CB4FF, "Игрок"), DM(0xFFFF7EC8, "ЛС"), MENTION(0xFFFFD166, "Упоминание"),
		SYSTEM(0xFFE0C36A, "Система"), JOIN(0xFF5BE39A, "Вход"), LEAVE(0xFFFF6B7A, "Выход"),
		SESSION(0xFFB18CFF, "Сессия"), CLIENT(0xFF8A90A8, "Клиент");
		public final int color;
		public final String ru;
		Category(int c, String ru) { color = c; this.ru = ru; }
	}

	/** Метаданные показанного сообщения. time = 0 — время неизвестно; channel: 'G', 'L' или 0. */
	public record Meta(long time, Category cat, String sender, int count, char channel) {}

	private static final Pattern URL = Pattern.compile("(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern DM_IN = Pattern.compile("^\\s*\\[\\s*([A-Za-z0-9_]{2,16})\\s*(?:->|→|»)\\s*(?:me|я|вы|you|мне)\\s*]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	private static final Pattern DM_OUT = Pattern.compile("^\\s*\\[\\s*(?:me|я|вы|you)\\s*(?:->|→|»)\\s*([A-Za-z0-9_]{2,16})\\s*]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	private static final Pattern DM_WORDS = Pattern.compile("(whispers to you|шепчет вам|\\[лс]|\\[pm]|\\(лс\\)|личное сообщение от)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	public static final Map<String, String> EMOJI = new LinkedHashMap<>();

	static {
		String[][] e = {
				{"heart", "❤"}, {"star", "★"}, {"smile", "☺"}, {"sad", "☹"}, {"sun", "☀"}, {"moon", "☾"}, {"snow", "❄"},
				{"cloud", "☁"}, {"umbrella", "☂"}, {"zap", "⚡"}, {"note", "♪"}, {"music", "♫"}, {"check", "✔"}, {"x", "✖"},
				{"arrow", "➜"}, {"sword", "⚔"}, {"skull", "☠"}, {"flower", "✿"}, {"peace", "☮"}, {"yin", "☯"}, {"crown", "♔"},
				{"diamond", "♦"}, {"spade", "♠"}, {"club", "♣"}, {"phone", "☎"}, {"mail", "✉"}, {"coffee", "☕"}, {"warn", "⚠"},
				{"infinity", "∞"}, {"sparkle", "✦"}, {"cross", "✚"}, {"hot", "♨"}, {"recycle", "♻"}, {"anchor", "⚓"},
				{"flag", "⚑"}, {"scales", "⚖"}, {"gear", "⚙"}, {"atom", "⚛"}, {"pen", "✎"}, {"scissors", "✂"}, {"plane", "✈"},
				{"hourglass", "⌛"}, {"watch", "⌚"}, {"chess", "♚"}, {"dice", "⚄"}, {"male", "♂"}, {"female", "♀"},
				{"shrug", "¯\\_(ツ)_/¯"}, {"tableflip", "(╯°□°)╯︵ ┻━┻"}, {"unflip", "┬─┬ ノ( ゜-゜ノ)"}, {"lenny", "( ͡° ͜ʖ ͡°)"},
				{"love", "(♥ω♥)"}, {"cool", "(⌐■_■)"}, {"wave", "(・ω・)ノ"}, {"cry", "(╥﹏╥)"}, {"angry", "(╬ Ò﹏Ó)"}, {"bear", "ʕ•ᴥ•ʔ"},
		};
		for (String[] p : e) EMOJI.put(p[0], p[1]);
	}

	/** :коды: → символы (только при отправке, команды не трогаем). */
	public static String emojify(String msg) {
		if (msg.startsWith("/") || msg.indexOf(':') < 0) return msg;
		StringBuilder sb = new StringBuilder();
		Matcher m = net.primlixe.auroralib.text.Regex.of(":([a-z]{1,12}):").matcher(msg);
		int last = 0;
		while (m.find()) {
			String rep = EMOJI.get(m.group(1));
			if (rep == null) continue;
			sb.append(msg, last, m.start()).append(rep);
			last = m.end();
		}
		sb.append(msg.substring(last));
		return sb.toString();
	}

	public static String translationKey(Component c) {
		return c.getContents() instanceof TranslatableContents tc ? tc.getKey() : "";
	}

	private static String argString(Component c, int i) {
		if (c.getContents() instanceof TranslatableContents tc && tc.getArgs().length > i) {
			Object a = tc.getArgs()[i];
			return a instanceof Component ac ? ac.getString() : String.valueOf(a);
		}
		return null;
	}

	/** Категория + отправитель + канал. */
	public static Meta classify(Component content, String plain, boolean playerSource, String selfName, boolean mention) {
		char ch = Channels.detect(plain);
		String key = translationKey(content);
		if (key.equals("multiplayer.player.joined") || key.equals("multiplayer.player.joined.renamed")) return new Meta(0, Category.JOIN, argString(content, 0), 1, ch);
		if (key.equals("multiplayer.player.left")) return new Meta(0, Category.LEAVE, argString(content, 0), 1, ch);
		if (key.equals("commands.message.display.incoming")) return new Meta(0, Category.DM, argString(content, 0), 1, ch);
		if (key.equals("commands.message.display.outgoing")) return new Meta(0, Category.DM, selfName, 1, ch);
		String body = Channels.stripTag(plain);
		Matcher m = DM_IN.matcher(body);
		if (m.find()) return new Meta(0, Category.DM, m.group(1), 1, ch);
		m = DM_OUT.matcher(body);
		if (m.find()) return new Meta(0, Category.DM, selfName, 1, ch);
		if (DM_WORDS.matcher(body).find()) return new Meta(0, Category.DM, ChatCore.senderOf(body), 1, ch);
		String sender = ChatCore.senderOf(body);
		if (mention) return new Meta(0, Category.MENTION, sender, 1, ch);
		if (playerSource || sender != null) return new Meta(0, sender == null ? Category.SYSTEM : Category.PLAYER, sender, 1, ch);
		return new Meta(0, Category.SYSTEM, null, 1, ch);
	}

	/** Собеседник в своём исходящем ЛС («[я -> Steve]», ванильное «Вы шепчете Steve») или null. */
	public static String dmPeer(Component content) {
		if (translationKey(content).equals("commands.message.display.outgoing")) return argString(content, 0);
		Matcher m = DM_OUT.matcher(Channels.stripTag(ChatCore.stripTime(content.getString())));
		return m.find() ? m.group(1) : null;
	}

	/** Компактный вход/выход. */
	public static Component compactJoin(Meta m, boolean fancy) {
		boolean join = m.cat() == Category.JOIN;
		if (fancy) {
			return Component.literal(m.sender() == null ? "?" : m.sender()).withStyle(join ? ChatFormatting.GREEN : ChatFormatting.RED)
					.append(Component.literal(join ? " зашёл" : " вышел").withStyle(ChatFormatting.GRAY));
		}
		return Component.literal(join ? "+ " : "− ").withStyle(join ? ChatFormatting.GREEN : ChatFormatting.RED)
				.append(Component.literal(m.sender() == null ? "?" : m.sender()).withStyle(ChatFormatting.GRAY));
	}

	/** Перестроить компонент, меняя стиль совпадений (стили остальных кусков сохраняются). */
	public static Component restyle(Component src, Pattern p, UnaryOperator<Style> fn, boolean onlyFirst) {
		MutableComponent out = Component.empty();
		boolean[] done = {false};
		src.visit((style, text) -> {
			if (text.isEmpty()) return Optional.empty();
			if (done[0] && onlyFirst) { out.append(Component.literal(text).withStyle(style)); return Optional.empty(); }
			Matcher m = p.matcher(text);
			int last = 0;
			while (m.find()) {
				if (m.start() > last) out.append(Component.literal(text.substring(last, m.start())).withStyle(style));
				out.append(Component.literal(m.group()).withStyle(fn.apply(style)));
				last = m.end();
				if (onlyFirst) { done[0] = true; break; }
			}
			if (last < text.length()) out.append(Component.literal(text.substring(last)).withStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** Убрать первые n символов текста (со стилями остального). */
	public static Component dropChars(Component src, int n) {
		if (n <= 0) return src;
		MutableComponent out = Component.empty();
		int[] left = {n};
		src.visit((style, text) -> {
			if (text.isEmpty()) return Optional.empty();
			if (left[0] >= text.length()) { left[0] -= text.length(); return Optional.empty(); }
			out.append(Component.literal(text.substring(left[0])).withStyle(style));
			left[0] = 0;
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** Ссылки становятся кликабельными. */
	public static Component linkifyClick(Component c) {
		if (!c.getString().toLowerCase(Locale.ROOT).contains("http")) return c;
		MutableComponent out = Component.empty();
		c.visit((style, text) -> {
			Matcher m = URL.matcher(text);
			int last = 0;
			while (m.find()) {
				String url = trimUrl(m.group());
				int end = m.start() + url.length();
				if (m.start() > last) out.append(Component.literal(text.substring(last, m.start())).withStyle(style));
				Style s = style;
				try {
					if (s.getClickEvent() == null) s = s.withClickEvent(new ClickEvent.OpenUrl(new URI(url)));
				} catch (Exception ignored) {
				}
				out.append(Component.literal(url).withStyle(s.withUnderlined(true).withColor(0x6CB4FF)));
				last = end; // хвостовая точка/скобка остаётся обычным текстом
			}
			if (last < text.length()) out.append(Component.literal(text.substring(last)).withStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** Без знаков препинания в конце: «site.ru.», «(site.ru)», «site.ru!» — точка/скобка не часть адреса. */
	public static String trimUrl(String url) {
		int e = url.length();
		while (e > 8) {
			char c = url.charAt(e - 1);
			if (".,;:!?'\"".indexOf(c) >= 0) { e--; continue; }
			// закрывающая скобка без открывающей внутри адреса — от текста вокруг
			if (c == ')' && url.lastIndexOf('(', e - 1) < 0) { e--; continue; }
			if (c == ']' && url.lastIndexOf('[', e - 1) < 0) { e--; continue; }
			break;
		}
		return url.substring(0, e);
	}

	/** Цвет ника по хешу — у каждого игрока свой, стабильный. */
	public static int nameColor(String name) {
		int h = name.toLowerCase(Locale.ROOT).hashCode();
		float hue = ((h * 0x9E3779B1) >>> 8 & 0xFFFF) / 65535f;
		return UI.hsb(hue, 0.55f, 1f, 1f) & 0xFFFFFF;
	}

	public static Component colorName(Component c, String name) {
		if (name == null) return c;
		var cfg = net.primlixe.aurorachat.config.ChatConfig.get();
		int col = nameColor(name);
		Pattern p = net.primlixe.auroralib.text.Regex.of("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])");
		UnaryOperator<Style> fn = st -> {
			if (cfg.colorNames) st = st.withColor(col);
			if (cfg.boldNames) st = st.withBold(true);
			if (cfg.nameItalic) st = st.withItalic(true);
			if (cfg.nameUnderline) st = st.withUnderlined(true);
			return st;
		};
		if (!cfg.nameCaps) return restyle(c, p, fn, true);
		// КАПС: меняем и текст ника
		MutableComponent out = Component.empty();
		boolean[] done = {false};
		c.visit((style, text) -> {
			if (text.isEmpty()) return Optional.empty();
			Matcher m = done[0] ? null : p.matcher(text);
			if (m != null && m.find()) {
				out.append(Component.literal(text.substring(0, m.start())).withStyle(style));
				out.append(Component.literal(m.group().toUpperCase(Locale.ROOT)).withStyle(fn.apply(style)));
				out.append(Component.literal(text.substring(m.end())).withStyle(style));
				done[0] = true;
			} else out.append(Component.literal(text).withStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	public static Component highlight(Component c, String word, int color) {
		if (word == null || word.isBlank()) return c;
		return restyle(c, net.primlixe.auroralib.text.Regex.of("(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}_])"),
				st -> st.withColor(color).withBold(true), false);
	}

	private ChatFormat() {}
}
