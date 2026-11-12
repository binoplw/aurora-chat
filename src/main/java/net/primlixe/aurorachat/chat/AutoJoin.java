package net.primlixe.aurorachat.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.primlixe.aurorachat.AuroraChatMod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Автовход: при заходе на сервер по очереди отправляются свои команды (/login пароль, /warp spawn…).
 * Хранится отдельно — config/aurorachat/autojoin.json, только на этом компьютере; в консоли пароли скрыты.
 */
public final class AutoJoin {
	public static final class Entry {
		/** Часть адреса сервера (mc.example.ru) или «*» — для всех. */
		public String server = "";
		public List<String> commands = new ArrayList<>();
		/** Пауза перед первой командой и между командами, мс. */
		public int delay = 1500;
		public boolean enabled = true;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static List<Entry> entries = new ArrayList<>();
	private static final List<Object[]> QUEUE = new ArrayList<>(); // {когда, команда}

	private static Path file() { return ChatCore.dir().resolve("autojoin.json"); }

	public static List<Entry> entries() { return entries; }

	public static void load() {
		try {
			if (Files.exists(file())) {
				List<Entry> l = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), new TypeToken<List<Entry>>() {}.getType());
				if (l != null) entries = l;
			}
		} catch (Exception e) {
			AuroraChatMod.LOG.warn("Aurora Chat: не удалось прочитать autojoin.json: {}", e.toString());
		}
		entries.removeIf(x -> x == null);
		for (Entry x : entries) {
			if (x.server == null) x.server = "";
			if (x.commands == null) x.commands = new ArrayList<>();
			x.delay = Math.max(300, Math.min(20000, x.delay));
		}
	}

	public static void save() {
		try {
			net.primlixe.auroralib.io.AsyncFiles.write(file(), GSON.toJson(entries), true); // в фоне, атомарно + .bak (там пароли)
		} catch (Exception e) {
			AuroraChatMod.LOG.warn("Aurora Chat: не удалось сохранить autojoin.json: {}", e.toString());
		}
	}

	/** Адрес текущего сервера (или «одиночная игра»). */
	public static String currentServer() {
		var mc = Minecraft.getInstance();
		return mc.getCurrentServer() != null ? mc.getCurrentServer().ip.toLowerCase(Locale.ROOT) : "одиночная игра";
	}

	/** Зашли на сервер — ставим команды в очередь. */
	public static void onJoin() {
		QUEUE.clear();
		String srv = currentServer();
		long t = System.currentTimeMillis();
		for (Entry e : entries) {
			if (!e.enabled || e.commands.isEmpty()) continue;
			String want = e.server.trim().toLowerCase(Locale.ROOT);
			if (!(want.equals("*") || (!want.isEmpty() && srv.contains(want)))) continue;
			for (String cmd : e.commands) {
				if (cmd == null || cmd.isBlank()) continue;
				t += e.delay;
				QUEUE.add(new Object[]{t, cmd.trim()});
			}
		}
		if (!QUEUE.isEmpty()) ConsoleLog.add(ConsoleLog.Level.MOD, "§dАвтовход: §f" + QUEUE.size() + " §7команд для " + srv);
	}

	public static void tick() {
		if (QUEUE.isEmpty()) return;
		var mc = Minecraft.getInstance();
		if (mc.getConnection() == null || mc.player == null) return;
		Object[] q = QUEUE.get(0);
		if ((long) q[0] > System.currentTimeMillis()) return;
		QUEUE.remove(0);
		String cmd = (String) q[1];
		if (cmd.startsWith("/")) mc.getConnection().sendCommand(cmd.substring(1));
		else mc.getConnection().sendChat(cmd);
		ConsoleLog.add(ConsoleLog.Level.CMD, "§b" + ConsoleLog.mask(cmd) + " §8(автовход)");
		net.primlixe.aurorachat.compat.Mc.chat().addClientSystemMessage(Component.literal("↪ Автовход: " + ConsoleLog.mask(cmd)).withStyle(ChatFormatting.DARK_GRAY));
	}

	private AutoJoin() {}
}
