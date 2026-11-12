package net.primlixe.aurorachat.chat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.primlixe.aurorachat.AuroraChatMod;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Журнал чата: config/aurora/chatlogs/&lt;сервер&gt;/&lt;yyyy-MM-dd&gt;.log
 * Строка: ЧЧ:ММ:СС \t ТИП \t текст с §-кодами. Запись — в фоновом потоке.
 */
public final class ChatLog {
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

	public record Entry(String time, String type, String legacy, String plain, String server, String day) {}

	public static Path root() { return FabricLoader.getInstance().getConfigDir().resolve("aurorachat").resolve("chatlogs"); }

	/** Короткое имя текущего сервера для папки логов. */
	public static String serverKey() {
		var mc = Minecraft.getInstance();
		String key;
		if (mc.getSingleplayerServer() != null) key = "singleplayer_" + mc.getSingleplayerServer().getWorldData().getLevelName();
		else if (mc.getCurrentServer() != null) key = mc.getCurrentServer().ip;
		else key = "menu";
		return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._а-яё-]", "_");
	}

	public static void write(String type, String legacy) {
		LocalDateTime now = LocalDateTime.now();
		String server = serverKey();
		String line = now.format(TIME) + "\t" + type + "\t" + legacy.replace("\n", " ⏎ ") + System.lineSeparator();
		// общий фоновый поток записи Aurora Lib (по порядку с остальными файлами, дописывается при выходе)
		net.primlixe.auroralib.io.AsyncFiles.append(root().resolve(server).resolve(now.format(DAY) + ".log"), line);
	}

	public static List<String> servers() {
		List<String> out = new ArrayList<>();
		try (Stream<Path> s = Files.list(root())) {
			s.filter(Files::isDirectory)
					.sorted(Comparator.comparingLong(ChatLog::lastModified).reversed())
					.forEach(p -> out.add(p.getFileName().toString()));
		} catch (Exception ignored) {
		}
		return out;
	}

	private static long lastModified(Path p) {
		try { return Files.getLastModifiedTime(p).toMillis(); } catch (Exception e) { return 0; }
	}

	public static List<String> days(String server) {
		List<String> out = new ArrayList<>();
		try (Stream<Path> s = Files.list(root().resolve(server))) {
			s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".log"))
					.map(n -> n.substring(0, n.length() - 4)).sorted(Comparator.reverseOrder()).forEach(out::add);
		} catch (Exception ignored) {
		}
		return out;
	}

	public static List<Entry> read(String server, String day) {
		List<Entry> out = new ArrayList<>();
		try {
			for (String l : Files.readAllLines(root().resolve(server).resolve(day + ".log"), StandardCharsets.UTF_8)) {
				String[] p = l.split("\t", 3);
				if (p.length < 3) continue;
				out.add(new Entry(p[0], p[1], p[2], ChatText.strip(p[2]), server, day));
			}
		} catch (Exception ignored) {
		}
		return out;
	}

	public static boolean deleteDay(String server, String day) {
		try { return Files.deleteIfExists(root().resolve(server).resolve(day + ".log")); } catch (Exception e) { return false; }
	}

	public static String today() { return LocalDate.now().format(DAY); }

	private ChatLog() {}
}
