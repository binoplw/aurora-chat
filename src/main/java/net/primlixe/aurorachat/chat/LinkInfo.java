package net.primlixe.aurorachat.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.primlixe.aurorachat.config.ChatConfig;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Предпросмотр ссылки при наведении: домен, полный адрес и предупреждения — без открытия сайта
 * (никаких запросов в сеть: всё по самому адресу). Ловит типичные ловушки из чатов:
 * сокращатели, голый IP, «steam»/«discord» в чужом домене, смешанные алфавиты, ссылки на .exe и т.п.
 */
public final class LinkInfo {
	private static final Set<String> SHORTENERS = Set.of("bit.ly", "tinyurl.com", "t.ly", "cutt.ly", "clck.ru", "goo.su", "vk.cc", "is.gd",
			"rb.gy", "shorturl.at", "tiny.cc", "ow.ly", "u.to", "qps.ru", "lnkd.in", "t.co", "rebrand.ly", "s.id");
	/** Бренд → настоящие домены. Бренд в любом другом домене — признак фишинга. */
	private static final String[][] BRANDS = {
			{"discord", "discord.com", "discord.gg", "discordapp.com", "discordapp.net", "discord.media"},
			{"steam", "steampowered.com", "steamcommunity.com", "steamstatic.com", "steamgames.com"},
			{"telegram", "telegram.org", "t.me", "telegram.me"},
			{"vk", "vk.com", "vk.ru", "vk.me", "vk.cc"},
			{"youtube", "youtube.com", "youtu.be"},
			{"google", "google.com", "google.ru", "googleusercontent.com", "googleapis.com", "googlevideo.com"},
	};
	private static final Set<String> RISKY_EXT = Set.of("exe", "bat", "cmd", "scr", "msi", "jar", "vbs", "ps1", "apk", "com", "pif", "lnk", "dll");

	public static List<Component> describe(String url) {
		List<Component> out = new ArrayList<>();
		URI uri;
		try { uri = new URI(url); } catch (Exception e) { uri = null; }
		String host = uri != null && uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
		if (host.startsWith("www.")) host = host.substring(4);
		out.add(Component.literal("🔗 " + (host.isEmpty() ? net.primlixe.aurorachat.ui.I18n.t("ссылка") : host)).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		out.add(Component.literal(url.length() > 90 ? url.substring(0, 87) + "…" : url).withStyle(ChatFormatting.GRAY));
		List<String> warn = warnings(url, uri, host);
		if (trusted(host)) out.add(Component.literal(net.primlixe.aurorachat.ui.I18n.t("✔ сайт из твоего списка разрешённых / адрес сервера")).withStyle(ChatFormatting.GREEN));
		else if (warn.isEmpty()) out.add(Component.literal(net.primlixe.aurorachat.ui.I18n.t("Явных признаков обмана не видно — но открывай, только если доверяешь")).withStyle(ChatFormatting.DARK_GRAY));
		for (String w : warn) out.add(Component.literal("⚠ " + w).withStyle(ChatFormatting.RED));
		out.add(Component.literal(net.primlixe.aurorachat.ui.I18n.t("Клик — открыть (игра спросит подтверждение)")).withStyle(ChatFormatting.DARK_GRAY));
		return out;
	}

	/** Предупреждения по адресу; пусто — ничего подозрительного. */
	public static List<String> warnings(String url, URI uri, String host) {
		List<String> w = new ArrayList<>();
		if (host.isEmpty()) { w.add(net.primlixe.aurorachat.ui.I18n.t("адрес не разобрать")); return w; }
		if (trusted(host)) return w;
		if (url.toLowerCase(Locale.ROOT).startsWith("http://")) w.add(net.primlixe.aurorachat.ui.I18n.t("без шифрования (http, не https)"));
		if (host.matches("\\d{1,3}(\\.\\d{1,3}){3}") || host.startsWith("[")) w.add(net.primlixe.aurorachat.ui.I18n.t("вместо названия сайта — голый IP-адрес"));
		if (host.contains("xn--") || mixedScripts(host)) w.add(net.primlixe.aurorachat.ui.I18n.t("в адресе смешаны алфавиты — частая подделка известного сайта"));
		if (SHORTENERS.contains(host)) w.add(net.primlixe.aurorachat.ui.I18n.t("сокращённая ссылка — куда она ведёт, не видно"));
		for (String[] b : BRANDS) {
			if (!hasBrand(host, b[0])) continue;
			boolean official = false;
			for (int i = 1; i < b.length; i++) if (host.equals(b[i]) || host.endsWith("." + b[i])) official = true;
			if (!official) w.add("«" + b[0] + net.primlixe.aurorachat.ui.I18n.t("» в чужом домене — похоже на фишинг (настоящий: ") + b[1] + ")");
		}
		if (uri != null && uri.getUserInfo() != null) w.add("в адресе спрятан «@» — открывается совсем другой сайт");
		if (uri != null && uri.getPath() != null) {
			String p = uri.getPath().toLowerCase(Locale.ROOT);
			int dot = p.lastIndexOf('.');
			if (dot > p.lastIndexOf('/') && RISKY_EXT.contains(p.substring(dot + 1))) w.add(net.primlixe.aurorachat.ui.I18n.t("ссылка на файл .") + p.substring(dot + 1) + net.primlixe.aurorachat.ui.I18n.t(" — не запускай незнакомые программы"));
		}
		if (host.chars().filter(ch -> ch == '-').count() >= 3) w.add(net.primlixe.aurorachat.ui.I18n.t("много дефисов в домене — так часто маскируют подделки"));
		return w;
	}

	/** Бренд в домене: короткие (vk) — только отдельным словом («vk-gift.ru»), длинные — и внутри («steamcommunlty»). */
	private static boolean hasBrand(String host, String brand) {
		for (String part : host.split("[.\\-]")) {
			if (brand.length() <= 3 ? part.equals(brand) : part.contains(brand)) return true;
		}
		return false;
	}

	private static boolean mixedScripts(String host) {
		boolean lat = false, cyr = false;
		for (char ch : host.toCharArray()) {
			if (ch >= 'a' && ch <= 'z') lat = true;
			else if (Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CYRILLIC) cyr = true;
		}
		return lat && cyr;
	}

	/** Домен из «Разрешённых сайтов» модерации или адрес текущего сервера. */
	private static boolean trusted(String host) {
		if (host.isEmpty()) return false;
		for (String d : ChatConfig.get().modAllowedDomains) {
			String a = d.trim().toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("/.*$", "");
			if (!a.isEmpty() && (host.equals(a) || host.endsWith("." + a))) return true;
		}
		var srv = Minecraft.getInstance().getCurrentServer();
		if (srv != null) {
			String ip = srv.ip.toLowerCase(Locale.ROOT).replaceAll(":\\d+$", "");
			int dot = ip.indexOf('.');
			String base = dot > 0 && ip.indexOf('.', dot + 1) > 0 ? ip.substring(dot + 1) : ip; // play.server.ru → server.ru
			if (host.equals(ip) || host.equals(base) || host.endsWith("." + base)) return true;
		}
		return false;
	}

	private LinkInfo() {}
}
