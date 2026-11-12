package net.primlixe.aurorachat.chat;

import net.minecraft.client.Minecraft;
import net.primlixe.aurorachat.config.ChatConfig;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Шаблоны в сообщениях: {xyz}, {ник}, {время} и т.п. заменяются при отправке.
 * Работает и в командах: «/m Steve я на {xyz}».
 */
public final class Templates {
	/** Подсказка для настроек: переменная → что подставится. */
	public static final String[][] HELP = {
			{"{xyz}", "координаты «x y z»"}, {"{x} {y} {z}", "по отдельности"}, {"{ник}", "твой ник"},
			{"{время}", "часы:минуты"}, {"{дата}", "дд.мм.гггг"}, {"{сервер}", "адрес сервера"},
			{"{мир}", "overworld / the_nether / the_end"}, {"{hp}", "здоровье"}, {"{пинг}", "пинг, мс"}, {"{fps}", "кадры в секунду"},
	};

	private static Map<String, Supplier<String>> vars() {
		var mc = Minecraft.getInstance();
		var p = mc.player;
		Map<String, Supplier<String>> v = new LinkedHashMap<>();
		Supplier<String> name = () -> p == null ? "" : p.getName().getString();
		Supplier<String> xyz = () -> p == null ? "" : p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ();
		Supplier<String> time = () -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
		Supplier<String> date = () -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
		Supplier<String> server = () -> mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "одиночная игра";
		Supplier<String> world = () -> mc.level == null ? "" : mc.level.dimension().identifier().getPath();
		Supplier<String> hp = () -> p == null ? "" : String.valueOf(Math.round(p.getHealth()));
		Supplier<String> ping = () -> {
			if (p == null || mc.getConnection() == null) return "0";
			var info = mc.getConnection().getPlayerInfo(p.getUUID());
			return info == null ? "0" : String.valueOf(info.getLatency());
		};
		Supplier<String> fps = () -> String.valueOf(mc.getFps());
		for (String k : new String[]{"ник", "name", "nick"}) v.put(k, name);
		for (String k : new String[]{"xyz", "коорды", "координаты", "pos"}) v.put(k, xyz);
		v.put("x", () -> p == null ? "" : String.valueOf(p.getBlockX()));
		v.put("y", () -> p == null ? "" : String.valueOf(p.getBlockY()));
		v.put("z", () -> p == null ? "" : String.valueOf(p.getBlockZ()));
		for (String k : new String[]{"время", "time"}) v.put(k, time);
		for (String k : new String[]{"дата", "date"}) v.put(k, date);
		for (String k : new String[]{"сервер", "server"}) v.put(k, server);
		for (String k : new String[]{"мир", "world"}) v.put(k, world);
		for (String k : new String[]{"hp", "здоровье"}) v.put(k, hp);
		for (String k : new String[]{"пинг", "ping"}) v.put(k, ping);
		v.put("fps", fps);
		return v;
	}

	public static String apply(String msg) {
		if (!ChatConfig.get().templates || msg.indexOf('{') < 0) return msg;
		Map<String, Supplier<String>> v = vars();
		StringBuilder sb = new StringBuilder();
		int i = 0;
		while (i < msg.length()) {
			char ch = msg.charAt(i);
			if (ch == '{') {
				int end = msg.indexOf('}', i);
				if (end > i) {
					Supplier<String> s = v.get(msg.substring(i + 1, end).toLowerCase(java.util.Locale.ROOT));
					if (s != null) {
						sb.append(s.get());
						i = end + 1;
						continue;
					}
				}
			}
			sb.append(ch);
			i++;
		}
		return sb.toString();
	}

	private Templates() {}
}
