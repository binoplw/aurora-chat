package net.primlixe.aurorachat.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Темы в одну строку: «AURORA-THEME:…» — весь вид чата (фон, цвета, раскладка, ники, время, вкладки).
 * Строку можно скинуть другу: он вставит её в «Темы → Вставить тему из буфера».
 * Внутри — только поля вида из списка ниже, поэтому чужая тема не может поменять что-то кроме внешнего вида.
 */
public final class ThemeCodec {
	public static final String PREFIX = "AURORA-THEME:";
	private static final Gson GSON = new Gson();

	/** Что входит в тему. Картинка фона — только имя файла (сам файл в код не помещается). */
	public static final List<String> FIELDS = List.of(
			"bgStyle", "bgColor", "bgColor2", "bgGradient", "bgOpacity", "bgOpacityClosed", "radius", "outline", "outlineOpacity",
			"shadow", "accentColor", "textShadow", "textColor", "textOpacity", "layout", "stripes", "stripeWidth", "zebra",
			"separators", "rowPadding", "messageGap", "bubbleRadius", "headSize", "heads", "icons", "boldNames", "nameItalic",
			"nameUnderline", "nameCaps", "colorNames", "timePos", "timestampColor", "timeScale", "timeBold", "badgeStyle", "tabStyle",
			"titleBar", "hoverHighlight", "globalColor", "localColor", "mentionColor", "chatFont", "lineSpacing", "textScale",
			"bgImage", "bgImageOpacity", "bgImageMode",
			"gLabel", "lLabel", "badgeShape", "badgeTextColor", "channelStripe", "channelTextTint", "channelTint", "channelHighlight");

	/** Текущий вид → код. */
	public static String export(String name) {
		ChatConfig c = ChatConfig.get();
		JsonObject vals = new JsonObject();
		for (String f : FIELDS) {
			try {
				Field fl = ChatConfig.class.getField(f);
				vals.add(f, GSON.toJsonTree(fl.get(c), fl.getGenericType()));
			} catch (ReflectiveOperationException ignored) { }
		}
		JsonObject root = new JsonObject();
		root.addProperty("name", name == null || name.isBlank() ? "Тема" : name.trim());
		root.add("look", vals);
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(root.toString().getBytes(StandardCharsets.UTF_8));
	}

	/** Разобранная тема: название и поля вида. null — это не код темы. */
	public record Decoded(String name, JsonObject look) {}

	public static Decoded decode(String code) {
		if (code == null) return null;
		String s = code.trim();
		int at = s.indexOf(PREFIX);
		if (at < 0) return null;
		s = s.substring(at + PREFIX.length()).split("\\s", 2)[0]; // код мог прийти внутри сообщения
		try {
			String json = new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			JsonObject look = root.has("look") && root.get("look").isJsonObject() ? root.getAsJsonObject("look") : null;
			if (look == null) return null;
			String name = root.has("name") && root.get("name").isJsonPrimitive() ? root.get("name").getAsString() : "Тема";
			return new Decoded(name.length() > 40 ? name.substring(0, 40) : name, look);
		} catch (Exception e) {
			return null;
		}
	}

	/** Применить код; сколько параметров применилось (0 — ничего, код битый). */
	public static int apply(String code) {
		Decoded d = decode(code);
		if (d == null) return 0;
		ChatConfig c = ChatConfig.get();
		int n = 0;
		for (String f : FIELDS) {
			JsonElement el = d.look().get(f);
			if (el == null || el.isJsonNull()) continue;
			try {
				Field fl = ChatConfig.class.getField(f);
				Object v = GSON.fromJson(el, fl.getGenericType());
				if (v != null) { fl.set(c, v); n++; }
			} catch (Exception ignored) { } // битое поле — пропускаем, остальные применяются
		}
		if (n > 0) {
			c.sanitize();
			c.theme = d.name();
			ChatConfig.save();
		}
		return n;
	}

	private ThemeCodec() {}
}
