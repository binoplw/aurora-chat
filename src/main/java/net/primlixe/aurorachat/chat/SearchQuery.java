package net.primlixe.aurorachat.chat;

import net.primlixe.aurorachat.chat.ChatFormat.Category;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Умный поиск: обычные слова + фильтры. Примеры:
 *  «ник:Steve алмазы» · «канал:G» · «тип:лс» · «дата:вчера после:18:00» · «ивент -реклама».
 * Ключи: ник/от/from, канал/ch, тип/type, дата/date, после/after, до/before; «-слово» — исключить.
 */
public final class SearchQuery {
	public final List<String> words = new ArrayList<>();
	public final List<String> excludes = new ArrayList<>();
	public String sender;
	public char channel;
	public Set<Category> cats;
	public LocalDate date;
	public LocalTime after, before;

	public static final String HELP = "ник:Steve · канал:G · тип:лс/система/игрок/упом · дата:сегодня/вчера/05.11 · после:18:00 · до:20:00 · -слово";

	public boolean isEmpty() {
		return words.isEmpty() && excludes.isEmpty() && sender == null && channel == 0 && cats == null && date == null && after == null && before == null;
	}

	/** Обычный текст запроса (для подсветки найденного). */
	public String text() { return String.join(" ", words); }

	public static SearchQuery parse(String q) {
		SearchQuery s = new SearchQuery();
		if (q == null) return s;
		for (String tok : q.trim().split("\\s+")) {
			if (tok.isEmpty()) continue;
			String low = tok.toLowerCase(Locale.ROOT);
			int c = low.indexOf(':');
			if (c > 0 && c < low.length() - 1 && !low.startsWith("http")) {
				String k = low.substring(0, c), v = low.substring(c + 1);
				switch (k) {
					case "ник", "от", "from", "nick", "player" -> { s.sender = v; continue; }
					case "канал", "ch", "channel" -> {
						s.channel = v.startsWith("g") || v.startsWith("г") ? 'G' : v.startsWith("l") || v.startsWith("л") ? 'L' : 0;
						continue;
					}
					case "тип", "type" -> { s.cats = cats(v); continue; }
					case "дата", "date", "день" -> { s.date = date(v); continue; }
					case "после", "after", "с" -> { s.after = time(v); continue; }
					case "до", "before" -> { s.before = time(v); continue; }
					default -> { }
				}
				// «после:18:00» разбивается на ключ и «18:00» — обработано выше; иначе это обычное слово
			}
			if (low.startsWith("-") && low.length() > 1) s.excludes.add(low.substring(1));
			else s.words.add(low);
		}
		return s;
	}

	private static Set<Category> cats(String v) {
		if (v.startsWith("лс") || v.startsWith("dm") || v.startsWith("личн") || v.startsWith("pm")) return EnumSet.of(Category.DM);
		if (v.startsWith("сис") || v.startsWith("sys")) return EnumSet.of(Category.SYSTEM, Category.JOIN, Category.LEAVE, Category.SESSION, Category.CLIENT);
		if (v.startsWith("упом") || v.startsWith("@") || v.startsWith("ment")) return EnumSet.of(Category.MENTION);
		if (v.startsWith("вход") || v.startsWith("join")) return EnumSet.of(Category.JOIN, Category.LEAVE);
		return EnumSet.of(Category.PLAYER, Category.MENTION);
	}

	private static LocalDate date(String v) {
		LocalDate today = LocalDate.now();
		if (v.startsWith("сегодня") || v.equals("today")) return today;
		if (v.startsWith("вчера") || v.equals("yesterday")) return today.minusDays(1);
		try { return LocalDate.parse(v); } catch (Exception ignored) { }
		try { return LocalDate.parse(v, DateTimeFormatter.ofPattern("dd.MM.yyyy")); } catch (Exception ignored) { }
		try {
			String[] p = v.split("[.\\-/]");
			return LocalDate.of(today.getYear(), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
		} catch (Exception ignored) { }
		return null;
	}

	private static LocalTime time(String v) {
		try {
			String[] p = v.split("[:.]");
			return LocalTime.of(Integer.parseInt(p[0]), p.length > 1 ? Integer.parseInt(p[1]) : 0);
		} catch (Exception e) { return null; }
	}

	/** Совпадение для сообщения в окне чата. */
	public boolean matches(String plainLower, ChatFormat.Meta m) {
		if (!text(plainLower)) return false;
		if (sender != null && (m.sender() == null || !m.sender().toLowerCase(Locale.ROOT).contains(sender)
				&& !Nicknames.display(m.sender()).toLowerCase(Locale.ROOT).contains(sender))) return false;
		if (channel != 0 && m.channel() != channel) return false;
		if (cats != null && !cats.contains(m.cat())) return false;
		if ((date != null || after != null || before != null)) {
			if (m.time() <= 0) return false;
			LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(m.time()), ZoneId.systemDefault());
			if (!when(t.toLocalDate(), t.toLocalTime())) return false;
		}
		return true;
	}

	/** Совпадение для строки журнала (дата и время известны из файла). */
	public boolean matchesLog(String plainLower, String day, String time, ChatFormat.Meta m) {
		if (!text(plainLower)) return false;
		if (sender != null && (m.sender() == null || !m.sender().toLowerCase(Locale.ROOT).contains(sender))) return false;
		if (channel != 0 && m.channel() != channel) return false;
		if (cats != null && !cats.contains(m.cat())) return false;
		try {
			return when(LocalDate.parse(day), LocalTime.parse(time));
		} catch (Exception e) {
			return date == null && after == null && before == null;
		}
	}

	private boolean text(String plainLower) {
		for (String w : words) if (!plainLower.contains(w)) return false;
		for (String x : excludes) if (plainLower.contains(x)) return false;
		return true;
	}

	private boolean when(LocalDate d, LocalTime t) {
		if (date != null && !date.equals(d)) return false;
		if (after != null && t.isBefore(after)) return false;
		if (before != null && t.isAfter(before)) return false;
		return true;
	}
}
