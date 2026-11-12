package net.primlixe.aurorachat.chat;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;

/** Конвертация текста чата: в «§»-коды, JSON и обратно. */
public final class ChatText {
	/**
	 * 16 цветов Minecraft (&0…&f) — своя таблица: в 26.2+ у ChatFormatting убрали цвет и код,
	 * а так одинаково работает на всех версиях.
	 */
	public static final String CODES = "0123456789abcdef";
	public static final int[] RGB = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	/**
	 * Текст с цветами для вставки в строку чата: «&»-коды (символ § игра в поле ввода не пропускает — вместо цветов
	 * оставались бы буквы «rf», «rc»…). Коды ставятся только там, где оформление меняется; цвет, которого нет среди 16,
	 * пишется точно — &#RRGGBB (Aurora Chat при отправке переведёт его в формат сервера).
	 */
	public static String amp(Component c) {
		StringBuilder sb = new StringBuilder();
		String[] cur = {""};
		c.visit((style, text) -> {
			if (text.isEmpty()) return Optional.empty();
			String want = ampCodes(style);
			if (!want.equals(cur[0])) {
				sb.append(want.isEmpty() ? "&r" : want);
				cur[0] = want;
			}
			sb.append(text);
			return Optional.empty();
		}, Style.EMPTY);
		String out = sb.toString();
		return out.startsWith("&r") ? out.substring(2) : out;
	}

	private static String ampCodes(Style s) {
		StringBuilder sb = new StringBuilder();
		TextColor tc = s.getColor();
		boolean fmt = s.isBold() || s.isItalic() || s.isUnderlined() || s.isStrikethrough() || s.isObfuscated();
		if (tc != null) {
			char named = exact(tc.getValue());
			if (named == 'f' && !fmt) return ""; // белый без оформления — просто обычный текст
			sb.append(named != 0 ? "&" + named : String.format("&#%06X", tc.getValue() & 0xFFFFFF));
		} else if (fmt) sb.append("&r");
		if (s.isBold()) sb.append("&l");
		if (s.isItalic()) sb.append("&o");
		if (s.isUnderlined()) sb.append("&n");
		if (s.isStrikethrough()) sb.append("&m");
		if (s.isObfuscated()) sb.append("&k");
		return sb.toString();
	}

	/** Код цвета Minecraft, если rgb — точно один из 16; иначе 0. */
	private static char exact(int rgb) {
		for (int i = 0; i < RGB.length; i++) if (RGB[i] == (rgb & 0xFFFFFF)) return CODES.charAt(i);
		return 0;
	}

	/** Строка с §-кодами (журнал) → «&»-коды без лишних повторов — для вставки в чат. */
	public static String ampFromLegacy(String legacy) {
		StringBuilder sb = new StringBuilder();
		Character color = null;
		StringBuilder fmts = new StringBuilder();
		String cur = "";
		for (int i = 0; i < legacy.length(); i++) {
			char ch = legacy.charAt(i);
			if (ch == '§' && i + 1 < legacy.length()) {
				char k = Character.toLowerCase(legacy.charAt(++i));
				if ("0123456789abcdef".indexOf(k) >= 0) { color = k; fmts.setLength(0); }
				else if (k == 'r') { color = null; fmts.setLength(0); }
				else if ("klmno".indexOf(k) >= 0 && fmts.indexOf("&" + k) < 0) fmts.append('&').append(k);
				continue;
			}
			String want = (color == null || color == 'f') && fmts.isEmpty() ? ""
					: (color != null ? "&" + color : "&r") + fmts;
			if (!want.equals(cur)) {
				sb.append(want.isEmpty() ? "&r" : want);
				cur = want;
			}
			sb.append(ch);
		}
		String out = sb.toString();
		return out.startsWith("&r") ? out.substring(2) : out;
	}

	/** Текст с цветами в виде §-кодов (hex-цвета приводятся к ближайшему из 16). */
	public static String legacy(Component c) {
		StringBuilder sb = new StringBuilder();
		c.visit((style, text) -> {
			if (!text.isEmpty()) {
				sb.append(codes(style)).append(text);
			}
			return Optional.empty();
		}, Style.EMPTY);
		return sb.toString();
	}

	private static String codes(Style s) {
		StringBuilder sb = new StringBuilder("§r");
		TextColor tc = s.getColor();
		if (tc != null) sb.append('§').append(nearest(tc.getValue()));
		if (s.isBold()) sb.append("§l");
		if (s.isItalic()) sb.append("§o");
		if (s.isUnderlined()) sb.append("§n");
		if (s.isStrikethrough()) sb.append("§m");
		if (s.isObfuscated()) sb.append("§k");
		return sb.toString();
	}

	/** Ближайший из 16 цветов Minecraft — его код (0-9, a-f). */
	static char nearest(int rgb) {
		char best = 'f';
		long bestD = Long.MAX_VALUE;
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		for (int i = 0; i < RGB.length; i++) {
			int c = RGB[i];
			int dr = r - ((c >> 16) & 255), dg = g - ((c >> 8) & 255), db = b - (c & 255);
			long d = (long) dr * dr * 3 + (long) dg * dg * 4 + (long) db * db * 2;
			if (d < bestD) { bestD = d; best = CODES.charAt(i); }
		}
		return best;
	}

	public static String strip(String legacy) { return legacy.replaceAll("§.", ""); }

	public static String json(Component c) {
		try {
			var mc = Minecraft.getInstance();
			var ops = mc.level != null ? mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE) : JsonOps.INSTANCE;
			JsonElement el = ComponentSerialization.CODEC.encodeStart(ops, c).getOrThrow();
			return el.toString();
		} catch (Exception e) {
			// правильное JSON-экранирование (переносы строк, управляющие символы) — иначе запись не читалась обратно
			return new com.google.gson.JsonPrimitive(c.getString()).toString();
		}
	}

	public static Component fromJson(String json) {
		try {
			var el = com.google.gson.JsonParser.parseString(json);
			return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, el).getOrThrow();
		} catch (Exception e) {
			return Component.literal(json);
		}
	}

	private ChatText() {}
}
