package net.primlixe.aurorachat.chat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.primlixe.aurorachat.AuroraChatMod;
import net.primlixe.aurorachat.config.ChatConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * История того, что ты писал (↑ / ↓ в чате): без повторов, до тысяч строк, помнится после перезапуска.
 * Хранится то, что ты набрал, а не то, что ушло серверу (без «!», автоцвета и развёрнутых градиентов).
 * Команды с паролями (/login, /reg…) в файл не пишутся — только до выхода из игры.
 */
public final class InputHistory {
	private static final Gson GSON = new Gson();
	private static final List<String> LIST = new ArrayList<>();
	private static final Pattern SECRET = Pattern.compile("^/(l|log|login|reg|register|cp|changepass|changepassword|unregister|2fa|totp|email|premium)(\\s.*)?$",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	/** Набранный текст, пока сообщение отправляется (его и кладём в историю). */
	private static String pending;

	private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("aurorachat").resolve("input_history.json"); }

	/** От старых к новым. */
	public static List<String> list() { return LIST; }

	public static void load() {
		LIST.clear();
		if (!ChatConfig.get().persistInput) return;
		try {
			Path f = file();
			if (!Files.exists(f)) return;
			List<String> l = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), new TypeToken<List<String>>() {}.getType());
			if (l != null) for (String s : l) if (s != null && !s.isBlank()) LIST.add(s);
			trim();
		} catch (Exception e) {
			AuroraChatMod.LOG.warn("Aurora Chat: не удалось прочитать историю ввода: {}", e.toString());
		}
	}

	/** Первый запуск: забираем то, что уже помнит игра. */
	public static void importVanilla(Collection<String> recent) {
		if (!LIST.isEmpty() || recent == null) return;
		for (String s : recent) if (s != null && !s.isBlank()) LIST.add(s);
		trim();
	}

	public static void setPending(String typed) { pending = typed; }

	public static void clearPending() { pending = null; }

	/** Что положить в историю: набранный текст, если сейчас отправляется сообщение, иначе как есть. */
	public static String takePending(String sent) {
		String p = pending;
		pending = null;
		return p != null && !p.isBlank() ? p.strip() : sent;
	}

	public static void add(String s) {
		if (s == null || s.isBlank()) return;
		LIST.remove(s);
		LIST.add(s);
		trim();
		save();
	}

	private static void trim() {
		int max = ChatConfig.get().inputHistorySize;
		while (LIST.size() > max) LIST.remove(0);
	}

	public static void clear() {
		LIST.clear();
		save();
	}

	public static boolean secret(String s) { return SECRET.matcher(s.trim()).matches(); }

	/** Снимок списка — сейчас, запись на диск — в фоне (Aurora Lib); частые отправки склеиваются в одну запись. */
	public static void save() {
		if (!ChatConfig.get().persistInput) return;
		try {
			List<String> out = new ArrayList<>();
			for (String s : LIST) if (!secret(s)) out.add(s);
			net.primlixe.auroralib.io.AsyncFiles.write(file(), GSON.toJson(out));
		} catch (Exception e) {
			AuroraChatMod.LOG.warn("Aurora Chat: не удалось сохранить историю ввода: {}", e.toString());
		}
	}

	/**
	 * Следующая строка истории от позиции pos (pos == size — «сейчас пишу») в сторону dir (-1 — старее, +1 — новее),
	 * начинающаяся с prefix и не равная current. -1 — ничего нет, size — вернуться к набранному.
	 */
	public static int find(int pos, int dir, String prefix, String current) {
		String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
		int n = LIST.size();
		for (int i = pos + dir; i >= 0 && i < n; i += dir) {
			String s = LIST.get(i);
			if (s.toLowerCase(Locale.ROOT).startsWith(p) && !s.equals(current)) return i;
		}
		return dir > 0 ? n : -1;
	}

	private InputHistory() {}
}
