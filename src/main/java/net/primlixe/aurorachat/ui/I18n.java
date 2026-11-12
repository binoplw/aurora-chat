package net.primlixe.aurorachat.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.primlixe.aurorachat.AuroraChatMod;
import net.primlixe.aurorachat.config.ChatConfig;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Язык интерфейса. Тексты мода написаны по-русски прямо в коде; для других языков игры
 * они подменяются в момент показа по словарю assets/aurorachat/i18n/en.json (ключ — русская строка).
 * Нет перевода — остаётся русский. Чужие сообщения чата сюда не попадают — только подписи мода.
 */
public final class I18n {
	private static final Set<String> RU_LIKE = Set.of("ru", "uk", "be", "kk");
	private static Map<String, String> en;
	private static String langCache;
	private static boolean englishCache;

	/** true — показывать по-английски (настройка uiLanguage или язык игры). */
	public static boolean english() {
		String pref = ChatConfig.get().uiLanguage;
		if ("ru".equals(pref)) return false;
		if ("en".equals(pref)) return true;
		var mc = Minecraft.getInstance();
		String code = mc != null && mc.options != null ? mc.options.languageCode : "ru_ru";
		if (!code.equals(langCache)) {
			langCache = code;
			String base = code.toLowerCase(Locale.ROOT);
			int us = base.indexOf('_');
			englishCache = !RU_LIKE.contains(us > 0 ? base.substring(0, us) : base);
		}
		return englishCache;
	}

	/** Подпись мода на языке интерфейса. Ведущие §-коды и пробелы сохраняются. */
	public static String t(String ru) {
		if (ru == null || ru.isEmpty() || !english()) return ru;
		Map<String, String> m = dict();
		String hit = m.get(ru);
		if (hit != null) return hit;
		int i = 0, n = ru.length();
		while (i < n) {
			char c = ru.charAt(i);
			if (c == '§' && i + 1 < n) i += 2;
			else if (c == ' ') i++;
			else break;
		}
		int j = n;
		while (j > i && ru.charAt(j - 1) == ' ') j--;
		if (i == 0 && j == n) return ru;
		hit = m.get(ru.substring(i, j));
		return hit != null ? ru.substring(0, i) + hit + ru.substring(j) : ru;
	}

	/** Шаблон с подстановкой: f("Перевести (%s)", "ru"). Ключ словаря — сам шаблон. */
	public static String f(String ruPattern, Object... args) {
		return String.format(Locale.ROOT, t(ruPattern), args);
	}

	public static String[] t(String[] ru) {
		if (ru == null || !english()) return ru;
		String[] out = new String[ru.length];
		for (int i = 0; i < ru.length; i++) out[i] = t(ru[i]);
		return out;
	}

	private static Map<String, String> dict() {
		if (en == null) {
			Map<String, String> m = new HashMap<>();
			try (InputStream in = I18n.class.getResourceAsStream("/assets/aurorachat/i18n/en.json")) {
				if (in != null) {
					Map<String, String> loaded = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), new TypeToken<Map<String, String>>() {}.getType());
					if (loaded != null) m.putAll(loaded);
				}
			} catch (Exception e) {
				AuroraChatMod.LOG.warn("Aurora Chat: словарь интерфейса не загружен: {}", e.toString());
			}
			en = m;
		}
		return en;
	}

	/** Сколько строк в словаре (для теста). */
	public static int size() { return dict().size(); }

	private I18n() {}
}
