package net.primlixe.aurorachat.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.primlixe.aurorachat.AuroraChatMod;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.mixin.ChatComponentAccessor;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Переводчик сообщений (Google Translate, без ключа).
 * Сеть — только в фоне (HttpClient.sendAsync), результат возвращается в игровой поток через Minecraft.execute.
 * Перевод дописывается второй строкой в то же сообщение — фильтры вкладок, каналы, клан и ник остаются как у оригинала.
 */
public final class Translator {
	public record Result(String text, String from) {}

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private static final int CACHE_MAX = 300;
	/** Кэш «язык|текст» → перевод: повтор того же сообщения не идёт в сеть. Доступ только из игрового потока и колбэков под замком. */
	private static final Map<String, Result> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
		@Override protected boolean removeEldestEntry(Map.Entry<String, Result> e) { return size() > CACHE_MAX; }
	});
	/** Показанное (с переводом) → исходное содержимое. */
	private static final Map<Component, Component> ORIGINAL = new com.google.common.collect.MapMaker().weakKeys().makeMap();
	/** Сообщения, которые сейчас переводятся (чтобы двойной клик не слал два запроса). Только игровой поток. */
	private static final Set<Component> BUSY = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Время автопереводов за последнюю минуту — не больше AUTO_PER_MIN, чтобы не завалить сервис. */
	private static final ArrayDeque<Long> AUTO_TIMES = new ArrayDeque<>();
	private static final int AUTO_PER_MIN = 20;
	private static final int MAX_LEN = 600;

	private static final Set<String> CYRILLIC_LANGS = Set.of("ru", "uk", "be", "bg", "kk", "sr", "mk", "ky", "tg", "mn");

	/** Язык перевода чужих сообщений: из настройки, «auto» — язык игры (ru_ru → ru, zh_cn → zh-CN). */
	public static String target() {
		String t = ChatCore.cfg().translateTo;
		if (!"auto".equals(t)) return t;
		var mc = Minecraft.getInstance();
		String code = mc != null && mc.options != null ? mc.options.languageCode : "ru_ru";
		if (code.startsWith("zh_")) return normLang("zh-" + code.substring(3), "en");
		int us = code.indexOf('_');
		return normLang(us > 0 ? code.substring(0, us) : code, "en");
	}

	/** Код языка: 2–3 латинские буквы (или zh-CN / zh-TW); иначе def. */
	public static String normLang(String s, String def) {
		if (s == null) return def;
		String v = s.trim();
		if (v.matches("(?i)zh-(cn|tw)")) return "zh-" + v.substring(3).toUpperCase(Locale.ROOT);
		v = v.toLowerCase(Locale.ROOT);
		return v.matches("[a-z]{2,3}") && !v.equals("auto") ? v : def;
	}

	/** Перевести текст. from = код исходного языка, который определил сервис. */
	public static CompletableFuture<Result> translate(String text, String to) {
		String src = text.length() > MAX_LEN ? text.substring(0, MAX_LEN) : text;
		String key = to + "|" + src;
		Result hit = CACHE.get(key);
		if (hit != null) return CompletableFuture.completedFuture(hit);
		String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&dt=t&tl="
				+ URLEncoder.encode(to, StandardCharsets.UTF_8) + "&q=" + URLEncoder.encode(src, StandardCharsets.UTF_8);
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(8))
				.header("User-Agent", "Mozilla/5.0")
				.GET().build();
		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(r -> {
			if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
			Result res = parse(r.body());
			CACHE.put(key, res);
			return res;
		});
	}

	/** Ответ gtx: [[["перевод","оригинал",…],…], null, "en", …]. */
	public static Result parse(String body) {
		JsonElement root = JsonParser.parseString(body);
		JsonArray arr = root.getAsJsonArray();
		StringBuilder sb = new StringBuilder();
		if (arr.size() > 0 && arr.get(0).isJsonArray()) {
			for (JsonElement part : arr.get(0).getAsJsonArray()) {
				if (part.isJsonArray() && !part.getAsJsonArray().isEmpty() && part.getAsJsonArray().get(0).isJsonPrimitive())
					sb.append(part.getAsJsonArray().get(0).getAsString());
			}
		}
		String from = arr.size() > 2 && arr.get(2).isJsonPrimitive() ? arr.get(2).getAsString() : "?";
		String text = sb.toString().strip();
		if (text.isEmpty()) throw new IllegalStateException(net.primlixe.aurorachat.ui.I18n.t("пустой ответ"));
		return new Result(text, from);
	}

	// ------------------------------------------------------------ сообщения в чате

	public static boolean isTranslated(Component shown) { return ORIGINAL.containsKey(shown); }

	/** Исходное содержимое (без строки перевода). */
	public static Component originalOf(Component shown) {
		Component o = ORIGINAL.get(shown);
		return o != null ? o : shown;
	}

	/** Текст для перевода: без времени, тега канала, ранга и ника — только то, что написал игрок. */
	public static String bodyOf(String plain, String sender) {
		String p = Channels.stripTag(ChatCore.stripTime(plain)).strip();
		if (sender != null) {
			int arrow = p.indexOf('→');
			int at = p.indexOf(sender);
			if (arrow > 0 && at >= 0 && at < arrow) return p.substring(arrow + 1).strip();
			if (at >= 0) {
				int end = at + sender.length();
				while (end < p.length() && " >»:|✪~".indexOf(p.charAt(end)) >= 0) end++;
				if (end > at + sender.length()) return p.substring(end).strip();
			}
		}
		return p;
	}

	/** ПКМ → «Перевести». */
	public static void translateMessage(GuiMessage m) {
		Component shown = m.content();
		if (isTranslated(shown) || BUSY.contains(shown)) return;
		ChatFormat.Meta meta = ChatCore.meta(shown);
		String text = bodyOf(shown.getString(), meta.sender());
		if (letters(text) == 0) { ChatToasts.push(net.primlixe.aurorachat.ui.I18n.t("⇄ Перевод"), net.primlixe.aurorachat.ui.I18n.t("Нечего переводить"), null, 0xFF6CB4FF); return; }
		run(m, text, true);
	}

	/** ПКМ → «Показать оригинал». */
	public static void showOriginal(GuiMessage m) {
		Component orig = ORIGINAL.get(m.content());
		if (orig != null) replace(m, orig);
	}

	/** Автоперевод входящего (вызывается из ChatHud.onIncoming уже после добавления в чат — на следующем тике). */
	public static void maybeAuto(Component shown, ChatFormat.Meta meta) {
		ChatConfig s = ChatCore.cfg();
		if (!s.translate || !s.translateAuto || meta.sender() == null) return;
		if (meta.cat() != ChatFormat.Category.PLAYER && meta.cat() != ChatFormat.Category.DM && meta.cat() != ChatFormat.Category.MENTION) return;
		var mc = Minecraft.getInstance();
		if (mc.player != null && meta.sender().equalsIgnoreCase(mc.player.getName().getString())) return;
		String text = bodyOf(shown.getString(), meta.sender());
		if (!looksForeign(text, target())) return;
		long now = System.currentTimeMillis();
		while (!AUTO_TIMES.isEmpty() && now - AUTO_TIMES.peekFirst() > 60_000) AUTO_TIMES.pollFirst();
		if (AUTO_TIMES.size() >= AUTO_PER_MIN) return;
		AUTO_TIMES.addLast(now);
		mc.execute(() -> {
			GuiMessage gm = find(shown);
			if (gm != null) run(gm, text, false);
		});
	}

	/** Похоже на чужой язык: хотя бы 8 букв и 2 слова, и почти все буквы — не из письменности языка перевода. */
	public static boolean looksForeign(String text, String to) {
		int cyr = 0, lat = 0, words = 0;
		boolean inWord = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			boolean letter = Character.isLetter(c);
			if (letter) {
				if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC) cyr++;
				else if (c < 0x250) lat++;
			}
			if (letter && !inWord) words++;
			inWord = letter;
		}
		int all = cyr + lat;
		if (all < 8 || words < 2) return false;
		boolean cyrTarget = CYRILLIC_LANGS.contains(to);
		return cyrTarget ? cyr * 10 < all : lat * 10 < all;
	}

	private static int letters(String s) {
		int n = 0;
		for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) n++;
		return n;
	}

	private static void run(GuiMessage m, String text, boolean manual) {
		Component shown = m.content();
		String to = target();
		BUSY.add(shown);
		translate(text, to).whenComplete((res, err) -> Minecraft.getInstance().execute(() -> {
			BUSY.remove(shown);
			if (err != null) {
				AuroraChatMod.LOG.warn("Aurora Chat: перевод не удался: {}", err.toString());
				if (manual) ChatToasts.push(net.primlixe.aurorachat.ui.I18n.t("⇄ Перевод"), net.primlixe.aurorachat.ui.I18n.t("Не удалось: нет связи с Google Translate"), null, 0xFFFF6B7A);
				return;
			}
			if (!apply(shown, text, res, to) && manual) ChatToasts.push(net.primlixe.aurorachat.ui.I18n.t("⇄ Перевод"), net.primlixe.aurorachat.ui.I18n.t("Сообщение уже на этом языке (") + to + ")", null, 0xFF6CB4FF);
		}));
	}

	/** Дописать перевод в сообщение (игровой поток). false — перевод не нужен (тот же язык) или сообщения уже нет. */
	public static boolean apply(Component shown, String text, Result res, String to) {
		if (res.from().equalsIgnoreCase(to) || res.text().equalsIgnoreCase(text)) return false;
		GuiMessage now = find(shown); // за время запроса сообщение могло уйти из истории
		if (now == null) return true;
		if (isTranslated(now.content())) return true;
		MutableComponent line = Component.literal("\n⇄ ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x6CB4FF)).withBold(true))
				.append(Component.literal(res.text()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xC8D2E6)).withBold(false).withItalic(true)
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(net.primlixe.aurorachat.ui.I18n.t("Перевод ") + res.from() + " → " + to + net.primlixe.aurorachat.ui.I18n.t(" · Google\n§7ПКМ — показать оригинал"))))));
		Component withTr = Component.empty().append(shown).append(line);
		ORIGINAL.put(withTr, shown);
		replace(now, withTr);
		return true;
	}

	/** Сообщение в истории по содержимому (по ссылке). */
	private static GuiMessage find(Component c) {
		List<GuiMessage> all = ((ChatComponentAccessor) net.primlixe.aurorachat.compat.Mc.chat()).aurorachat$all();
		for (GuiMessage g : all) if (g.content() == c) return g;
		return null;
	}

	/** Подменить содержимое сообщения на месте: время, метаданные и положение в истории сохраняются. */
	private static void replace(GuiMessage m, Component content) {
		var chat = net.primlixe.aurorachat.compat.Mc.chat();
		ChatComponentAccessor a = (ChatComponentAccessor) chat;
		List<GuiMessage> all = a.aurorachat$all();
		int at = -1;
		for (int i = 0; i < all.size(); i++) if (all.get(i) == m) { at = i; break; }
		if (at < 0) return;
		GuiMessage n = new GuiMessage(m.addedTime(), content, m.signature(), m.source(), m.tag());
		ChatCore.carry(m.content(), content);
		all.set(at, n);
		ChatHud.replaced(m, n);
		ChatCore.relayout(m, n, at);
	}

	/**
	 * Кнопка ⇄ у строки ввода: перевести набранное в translateOut.
	 * Префикс канала «!» и начало команды ЛС («/m Ник ») не переводятся; прочие команды — не трогаем.
	 * null — переводить нечего (тогда done не вызывается).
	 */
	public static String translateInput(String value, Consumer<String> done) {
		String v = value == null ? "" : value;
		String head = "";
		if (v.startsWith("/")) {
			String[] p = v.split(" ", 3);
			String cmd = p[0].substring(1).toLowerCase(Locale.ROOT);
			if (p.length < 3 || !ChatCore.cfg().dmCommands.contains(cmd)) return net.primlixe.aurorachat.ui.I18n.t("Команды не переводятся — только ЛС (/") + ChatMenu.firstDm() + net.primlixe.aurorachat.ui.I18n.t(" ник текст)");
			head = p[0] + " " + p[1] + " ";
			v = p[2];
		} else if (v.startsWith("!")) {
			head = "!";
			v = v.substring(1);
		}
		if (letters(v) == 0) return net.primlixe.aurorachat.ui.I18n.t("Сначала напиши текст");
		String h = head;
		translateOutgoing(v.strip(), tr -> done.accept(tr == null ? null : h + tr));
		return null;
	}

	/** «Перевести и вставить»: перевод своего текста в translateOut. done(null) — не удалось (уведомление уже показано). */
	public static void translateOutgoing(String text, Consumer<String> done) {
		String to = ChatCore.cfg().translateOut;
		translate(text, to).whenComplete((res, err) -> Minecraft.getInstance().execute(() -> {
			if (err != null) {
				AuroraChatMod.LOG.warn("Aurora Chat: перевод не удался: {}", err.toString());
				ChatToasts.push(net.primlixe.aurorachat.ui.I18n.t("⇄ Перевод"), net.primlixe.aurorachat.ui.I18n.t("Не удалось: нет связи с Google Translate"), null, 0xFFFF6B7A);
				done.accept(null);
				return;
			}
			done.accept(res.text());
		}));
	}

	/** Языки в настройках и по ПКМ на кнопке ⇄. */
	public static final String[] LANGS = {"ru", "en", "uk", "be", "kk", "de", "fr", "es", "pl", "tr", "zh-CN", "ja"};
	public static final String[] LANG_NAMES = {"Русский", "English", "Українська", "Беларуская", "Қазақша", "Deutsch", "Français", "Español", "Polski", "Türkçe", "中文", "日本語"};

	public static String nextLang(String cur) {
		int i = java.util.Arrays.asList(LANGS).indexOf(cur);
		return LANGS[(i + 1) % LANGS.length];
	}

	private Translator() {}
}
