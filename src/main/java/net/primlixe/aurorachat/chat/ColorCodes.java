package net.primlixe.aurorachat.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.primlixe.aurorachat.config.ChatConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * HEX-цвета и градиенты в исходящих сообщениях.
 * <ul>
 *   <li>{@code &#F80} или {@code &#FF8800} — HEX-цвет;</li>
 *   <li>{@code &{#FF0000-#0000FF}текст} — градиент из любых цветов (через - > , или пробел), можно больше двух;</li>
 *   <li>{@code &{огонь}текст} — сохранённый градиент по названию;</li>
 *   <li>градиент идёт до {@code &{/}}, {@code &r}, следующего цвета или конца сообщения; {@code &l &o &n &m &k} внутри работают.</li>
 * </ul>
 * Серверу уходит обычный текст с кодами в выбранном формате (&#RRGGBB, &x&R&R…, &lt;#RRGGBB&gt;).
 * Если сообщение не влезает в 256 символов, цвет ставится через каждые 2, 3… буквы.
 */
public final class ColorCodes {
	/** Чат пропускает 256 символов; запас — под префикс канала и автоцвет. */
	public static final int LIMIT = 250;
	private static final String LEGACY = "0123456789abcdef";
	private static final int[] LEGACY_RGB = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	public static boolean hasCodes(String s) { return s != null && (s.contains("&#") || s.contains("&{")); }

	/** Преобразование перед отправкой (с учётом настроек). */
	public static String expand(String msg) {
		ChatConfig c = ChatConfig.get();
		if (!c.enabled || !c.hexColors || !hasCodes(msg)) return msg;
		return expand(msg, c.hexFormat);
	}

	public static String expand(String msg, ChatConfig.HexFormat fmt) {
		String out = render(msg, 1, fmt);
		for (int step = 2; out.length() > LIMIT && step <= 64; step++) out = render(msg, step, fmt);
		return out;
	}

	private static String render(String s, int step, ChatConfig.HexFormat fmt) {
		StringBuilder out = new StringBuilder(s.length() * 2);
		int i = 0, n = s.length();
		while (i < n) {
			char ch = s.charAt(i);
			if (ch == '&' && i + 1 < n) {
				char nx = s.charAt(i + 1);
				if (nx == '#') {
					int len = hexRun(s, i + 2);
					if (len > 0) {
						out.append(code(parseHex(s.substring(i + 2, i + 2 + len)), fmt));
						i += 2 + len;
						continue;
					}
				} else if (nx == '{') {
					int close = s.indexOf('}', i + 2);
					if (close > 0) {
						String spec = s.substring(i + 2, close).trim();
						if (spec.equals("/")) { i = close + 1; continue; }
						int[] cols = colors(spec);
						if (cols != null) {
							int end = gradientEnd(s, close + 1);
							out.append(gradient(s.substring(close + 1, end), cols, step, fmt));
							i = end;
							if (s.startsWith("&{/}", i)) i += 4;
							continue;
						}
					}
				}
			}
			out.append(ch);
			i++;
		}
		return out.toString();
	}

	private static boolean isHex(char c) { return Character.digit(c, 16) >= 0; }

	private static boolean isStyle(char c) { return "klmnoKLMNO".indexOf(c) >= 0; }

	/** Сколько символов HEX-цвета идёт с позиции: 6, 3 или 0. */
	private static int hexRun(String s, int from) {
		int k = 0;
		while (k < 6 && from + k < s.length() && isHex(s.charAt(from + k))) k++;
		return k == 6 ? 6 : k >= 3 ? 3 : 0;
	}

	private static int parseHex(String h) {
		if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
		return Integer.parseInt(h, 16);
	}

	private static int gradientEnd(String s, int from) {
		for (int j = from; j + 1 < s.length(); j++) {
			if (s.charAt(j) != '&') continue;
			char c = Character.toLowerCase(s.charAt(j + 1));
			if (c == '{' || c == '#' || c == 'r' || LEGACY.indexOf(c) >= 0) return j;
		}
		return s.length();
	}

	/** Цвета из «#F00-#0F0», «a > c», «FF0000, 00FF00» или названия сохранённого градиента; null — не цвета. */
	public static int[] colors(String spec) {
		if (spec == null || spec.isBlank()) return null;
		for (ChatConfig.Gradient g : ChatConfig.get().gradients) {
			if (g.name.trim().equalsIgnoreCase(spec.trim())) return colors(String.join(">", g.colors));
		}
		List<Integer> out = new ArrayList<>();
		for (String p : spec.trim().split("[\\s>,;\\-–]+")) {
			if (p.isEmpty()) continue;
			String v = p.startsWith("#") || p.startsWith("&") ? p.substring(1) : p;
			if (v.length() == 1 && LEGACY.indexOf(Character.toLowerCase(v.charAt(0))) >= 0) {
				out.add(LEGACY_RGB[LEGACY.indexOf(Character.toLowerCase(v.charAt(0)))]);
			} else if ((v.length() == 3 || v.length() == 6) && v.chars().allMatch(c -> isHex((char) c))) {
				out.add(parseHex(v));
			} else return null;
		}
		return out.isEmpty() ? null : out.stream().mapToInt(Integer::intValue).toArray();
	}

	private static String gradient(String body, int[] cols, int step, ChatConfig.HexFormat fmt) {
		int total = 0;
		for (int j = 0; j < body.length(); ) {
			int cp = body.codePointAt(j);
			if (cp == '&' && j + 1 < body.length() && isStyle(body.charAt(j + 1))) { j += 2; continue; }
			if (!Character.isWhitespace(cp)) total++;
			j += Character.charCount(cp);
		}
		StringBuilder out = new StringBuilder(body.length() * 9);
		StringBuilder styles = new StringBuilder();
		int vi = 0;
		for (int j = 0; j < body.length(); ) {
			int cp = body.codePointAt(j);
			if (cp == '&' && j + 1 < body.length() && isStyle(body.charAt(j + 1))) {
				String st = "&" + Character.toLowerCase(body.charAt(j + 1));
				styles.append(st);
				out.append(st);
				j += 2;
				continue;
			}
			if (Character.isWhitespace(cp)) out.appendCodePoint(cp);
			else {
				if (vi % step == 0) {
					float t = total <= 1 ? 0 : (float) vi / (total - 1);
					out.append(code(at(cols, t), fmt)).append(styles);
				}
				out.appendCodePoint(cp);
				vi++;
			}
			j += Character.charCount(cp);
		}
		return out.toString();
	}

	/** Цвет градиента в точке t (0..1). */
	public static int at(int[] cols, float t) {
		if (cols.length == 1) return cols[0];
		float p = Math.max(0, Math.min(1, t)) * (cols.length - 1);
		int a = Math.min((int) p, cols.length - 2);
		float f = p - a;
		int x = cols[a], y = cols[a + 1];
		int r = Math.round(((x >> 16) & 255) + (((y >> 16) & 255) - ((x >> 16) & 255)) * f);
		int g = Math.round(((x >> 8) & 255) + (((y >> 8) & 255) - ((x >> 8) & 255)) * f);
		int b = Math.round((x & 255) + ((y & 255) - (x & 255)) * f);
		return (r << 16) | (g << 8) | b;
	}

	private static String code(int rgb, ChatConfig.HexFormat fmt) {
		String hex = String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
		return switch (fmt) {
			case AMP_X -> {
				StringBuilder b = new StringBuilder("&x");
				for (char c : hex.toCharArray()) b.append('&').append(c);
				yield b.toString();
			}
			case MINI -> "<#" + hex + ">";
			default -> "&#" + hex;
		};
	}

	/** Есть ли в тексте хоть один &-код (обычный, HEX или градиент). */
	public static boolean hasAnyCode(String s) {
		if (s == null) return false;
		for (int i = 0; i + 1 < s.length(); i++) {
			if (s.charAt(i) != '&') continue;
			char c = Character.toLowerCase(s.charAt(i + 1));
			if (c == '#' || c == '{' || "0123456789abcdefklmnor".indexOf(c) >= 0) return true;
		}
		return false;
	}

	/** Как увидят сообщение (если сервер понимает &-коды): для предпросмотра над строкой ввода. */
	public static Component preview(String msg) {
		String s = render(msg, 1, ChatConfig.HexFormat.AMP_HASH);
		MutableComponent root = Component.empty();
		Style st = Style.EMPTY;
		StringBuilder buf = new StringBuilder();
		int n = s.length();
		for (int i = 0; i < n; i++) {
			char ch = s.charAt(i);
			if (ch == '&' && i + 1 < n) {
				char nx = s.charAt(i + 1);
				if (nx == '#' && hexRun(s, i + 2) == 6) {
					flush(root, buf, st);
					st = Style.EMPTY.withColor(TextColor.fromRgb(parseHex(s.substring(i + 2, i + 8))));
					i += 7;
					continue;
				}
				// свои коды (без ChatFormatting — в 26.2+ у него нет цветов): цвет сбрасывает стили, k-o добавляют
				char k = Character.toLowerCase(nx);
				int ci = LEGACY.indexOf(k);
				if (ci >= 0 || "klmnor".indexOf(k) >= 0) {
					flush(root, buf, st);
					if (k == 'r') st = Style.EMPTY;
					else if (ci >= 0) st = Style.EMPTY.withColor(TextColor.fromRgb(LEGACY_RGB[ci]));
					else st = switch (k) {
						case 'l' -> st.withBold(true);
						case 'o' -> st.withItalic(true);
						case 'n' -> st.withUnderlined(true);
						case 'm' -> st.withStrikethrough(true);
						default -> st.withObfuscated(true);
					};
					i++;
					continue;
				}
			}
			buf.append(ch);
		}
		flush(root, buf, st);
		return root;
	}

	private static void flush(MutableComponent root, StringBuilder buf, Style st) {
		if (buf.isEmpty()) return;
		root.append(Component.literal(buf.toString()).setStyle(st));
		buf.setLength(0);
	}

	private ColorCodes() {}
}
