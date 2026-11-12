package net.primlixe.aurorachat.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.primlixe.aurorachat.chat.ChatFormat.Category;
import net.primlixe.aurorachat.chat.ChatFormat.Meta;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.config.ChatConfig.ExtraWindow;
import net.primlixe.aurorachat.mixin.ChatComponentAccessor;
import net.primlixe.aurorachat.ui.UI;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Дополнительные окна чата: у каждого свой фильтр (ЛС, G, L, упоминания, система, свои слова),
 * положение и размер. В открытом чате окно тянется за шапку, размер — за уголок, колесо — прокрутка.
 */
public final class ExtraWindows {
	public static final String[] KINDS = {"DM", "GLOBAL", "LOCAL", "CLAN", "STAFF", "MENTIONS", "SYSTEM", "CUSTOM"};
	public static final String[] KINDS_RU = {"Личные", "Глобальный G", "Локальный L", "Клан", "Персонал", "Упоминания", "Система", "Свои слова"};

	private static final Map<GuiMessage, Object[]> LINES = new com.google.common.collect.MapMaker().weakKeys().makeMap();
	private static final Map<GuiMessage, String> LOW = new com.google.common.collect.MapMaker().weakKeys().makeMap();
	private static final Map<Integer, float[]> SCROLL = new HashMap<>(); // {цель, плавное, максимум}
	private static final List<int[]> RECTS = new ArrayList<>();           // {индекс, x0,y0,x1,y1, шапкаY1}
	private static long lastFrame = System.nanoTime();
	private static int dragWin = -1, dragMode;
	private static float dA, dB, sW, sH, sMx, sMy;

	static boolean matches(ExtraWindow w, Meta m, GuiMessage msg) {
		return switch (w.kind) {
			case "DM" -> m.cat() == Category.DM;
			case "GLOBAL" -> m.channel() == 'G';
			case "LOCAL" -> m.channel() == 'L';
			case "MENTIONS" -> m.cat() == Category.MENTION;
			case "CLAN" -> ChatHud.isClanText(LOW.computeIfAbsent(msg, k -> k.content().getString().toLowerCase(Locale.ROOT)));
			case "STAFF" -> ChatHud.isStaff(m, LOW.computeIfAbsent(msg, k -> k.content().getString().toLowerCase(Locale.ROOT)));
			case "SYSTEM" -> m.cat() != Category.PLAYER && m.cat() != Category.MENTION && m.cat() != Category.DM;
			default -> {
				String low = LOW.computeIfAbsent(msg, k -> k.content().getString().toLowerCase(Locale.ROOT));
				for (String k : w.keywords) if (!k.isBlank() && low.contains(k.toLowerCase(Locale.ROOT))) yield true;
				yield false;
			}
		};
	}

	/** Сообщение «забрало» окно с флагом «только здесь» — в основном чате его не показываем. */
	public static boolean takesExclusive(GuiMessage msg, Meta m) {
		for (ExtraWindow w : ChatConfig.get().windows) if (w.exclusive && matches(w, m, msg)) return true;
		return false;
	}

	private static float[] rect(ExtraWindow w, int guiW, int guiH) {
		float W = Math.min(w.w, guiW - 4), H = Math.min(w.h, guiH - 30);
		float left = w.left < 0 ? guiW - W - 4 : Math.max(0, Math.min(w.left, guiW - W));
		float bottom = guiH - Math.max(0, Math.min(w.bottom, guiH - 30));
		bottom = Math.max(bottom, H + 2);
		return new float[]{left, bottom - H, left + W, bottom};
	}

	/** Видимые строки окон в этом кадре: {msg, x0, y0, x1, y1} — для ПКМ-меню. */
	private static final List<Object[]> HITS = new ArrayList<>();

	/** Сообщение под курсором в доп. окне (верхнее окно главнее); null — не над сообщением окна. */
	public static GuiMessage messageAt(double mx, double my) {
		for (int k = HITS.size() - 1; k >= 0; k--) {
			Object[] h = HITS.get(k);
			if (mx >= (float) h[1] && mx < (float) h[3] && my >= (float) h[2] && my < (float) h[4]) return (GuiMessage) h[0];
		}
		return null;
	}

	/** Курсор над каким-либо доп. окном (тогда сообщения основного чата под ним не кликаются). */
	public static boolean over(double mx, double my) { return at(mx, my) != null; }

	public static void render(GuiGraphicsExtractor g, Font font, boolean open, int mx, int my) {
		RECTS.clear();
		HITS.clear();
		ChatConfig s = ChatConfig.get();
		if (!s.enabled || s.windows.isEmpty()) return;
		var mc = Minecraft.getInstance();
		long nowNs = System.nanoTime();
		float dt = Math.min(0.1f, (nowNs - lastFrame) / 1e9f);
		lastFrame = nowNs;
		List<GuiMessage> all = ((ChatComponentAccessor) net.primlixe.aurorachat.compat.Mc.chat()).aurorachat$all();
		int gt = net.primlixe.aurorachat.compat.Mc.guiTicks();
		int bg = 0xFF000000 | s.bgColor;
		DateTimeFormatter tf = ChatCore.timeFormatter();
		for (int i = 0; i < s.windows.size(); i++) {
			ExtraWindow w = s.windows.get(i);
			if (!open && !w.showClosed) continue;
			float[] r = rect(w, g.guiWidth(), g.guiHeight());
			int x0 = Math.round(r[0]), y0 = Math.round(r[1]), x1 = Math.round(r[2]), y1 = Math.round(r[3]);
			int headH = 13;
			int bodyTop = y0 + headH;
			float[] sc = SCROLL.computeIfAbsent(i, k -> new float[3]);
			if (!open) sc[0] = 0;
			sc[1] = UI.damp(sc[1], sc[0], 16, dt);
			int textW = (x1 - x0) - 22;
			// раскладка снизу вверх
			List<Object[]> rows = new ArrayList<>();
			float y = y1 - 3 + (open ? sc[1] : 0);
			float total = 0, maxA = 0;
			for (GuiMessage msg : all) {
				Meta m = ChatCore.meta(msg.content());
				if (!matches(w, m, msg)) continue;
				float a = 1f;
				if (!open) {
					int age = gt - msg.addedTime();
					a = age < 0 ? 1 : Math.max(0, Math.min(1, (s.fadeSeconds * 20 - age) / (s.fadeOutTime * 20f)));
					if (a <= 0.01f) break;
				}
				Object[] c = LINES.get(msg);
				int key = textW * 31 + s.chatFont.hashCode();
				if (c == null || (int) c[0] != key) {
					c = new Object[]{key, font.split(net.primlixe.aurorachat.ui.Fonts.wrap(msg.content(), s.chatFont), Math.max(20, textW))};
					LINES.put(msg, c);
				}
				@SuppressWarnings("unchecked") List<FormattedCharSequence> ls = (List<FormattedCharSequence>) c[1];
				float h = ls.size() * 9 + 3;
				y -= h;
				total += h;
				if (y + h >= bodyTop - 2 && y <= y1) rows.add(new Object[]{msg, m, y, ls, a});
				maxA = Math.max(maxA, a);
				if (!open && y < bodyTop) break;
				if (open && total > (y1 - bodyTop) + sc[2] + 2000) break;
			}
			sc[2] = Math.max(0, total - (y1 - bodyTop) + 4);
			if (sc[0] > sc[2]) sc[0] = sc[2];
			if (!open && rows.isEmpty()) continue;
			float pa = open ? s.bgOpacity : s.bgOpacityClosed * maxA;
			// панель
			if (s.shadow && open && !net.primlixe.aurorachat.ui.Perf.lite()) UI.shadow(g, x0, y0, x1, y1, s.radius, 0.8f);
			UI.roundGradient(g, x0, y0, x1, y1, s.radius, UI.alpha(s.bgGradient ? 0xFF000000 | s.bgColor2 : UI.mix(bg, 0xFFFFFFFF, 0.04f), pa), UI.alpha(bg, pa));
			if (s.outline) UI.roundOutline(g, x0, y0, x1, y1, s.radius, UI.alpha(UI.accent(), s.outlineOpacity * (open ? 1 : maxA)));
			// шапка
			float ha = open ? 1 : maxA * 0.8f;
			String icon = switch (w.kind) { case "DM" -> "✉"; case "GLOBAL" -> "G"; case "LOCAL" -> "L"; case "MENTIONS" -> "@"; case "CLAN" -> "⚑"; case "STAFF" -> "★"; case "SYSTEM" -> "✦"; default -> "☰"; };
			int ic = w.kind.equals("GLOBAL") ? Channels.color('G') : w.kind.equals("LOCAL") ? Channels.color('L') : w.kind.equals("CLAN") ? 0xFFFFAA00 : UI.accent();
			g.fill(x0 + 4, y0 + headH, x1 - 4, y0 + headH + 1, UI.alpha(UI.accent(), 0.3f * ha));
			g.text(font, icon, x0 + 5, y0 + 3, UI.alpha(ic, ha), false);
			g.text(font, UI.trim(font, net.primlixe.aurorachat.ui.I18n.t(w.name), x1 - x0 - 30), x0 + 15, y0 + 3, UI.alpha(UI.TEXT, ha), false);
			if (open && !s.locked) {
				// уголок размера справа сверху
				boolean rh = mx >= x1 - 9 && mx < x1 && my >= y0 && my < y0 + 9;
				int cc = UI.alpha(rh || (dragWin == i && dragMode == 2) ? UI.accent() : 0xFFFFFFFF, rh ? 0.95f : 0.35f);
				for (int k = 0; k < 3; k++) g.fill(x1 - 3 - k * 3, y0 + 2, x1 - 2 - k * 3, y0 + 3, cc);
				for (int k = 0; k < 3; k++) g.fill(x1 - 3, y0 + 2 + k * 3, x1 - 2, y0 + 3 + k * 3, cc);
				if (my >= y0 && my < y0 + headH && mx >= x0 && mx < x1) {
					g.requestCursor(rh ? com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL : com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL);
				}
			}
			// строки
			g.enableScissor(x0, bodyTop + 1, x1, y1);
			for (Object[] row : rows) {
				GuiMessage msg = (GuiMessage) row[0];
				Meta m = (Meta) row[1];
				float ry = (float) row[2];
				@SuppressWarnings("unchecked") List<FormattedCharSequence> ls = (List<FormattedCharSequence>) row[3];
				float a = (float) row[4];
				g.pose().pushMatrix();
				g.pose().translate(x0 + 4, ry);
				PlayerInfo pi = m.sender() == null || mc.getConnection() == null ? null : mc.getConnection().getPlayerInfoIgnoreCase(m.sender());
				if (s.heads && pi != null) PlayerFaceExtractor.extractRenderState(g, net.primlixe.auroralib.compat.Skins.of(pi), 0, 1, 8, UI.alpha(0xFFFFFFFF, a));
				else g.fill(0, 2, 2, 8, UI.alpha(m.cat().color, a));
				for (int k = 0; k < ls.size(); k++) g.text(font, ls.get(k), 12, 1 + k * 9, UI.alpha(0xFF000000 | s.textColor, a * s.textOpacity), s.textShadow);
				g.pose().popMatrix();
				if (open) {
					float top = Math.max(ry, bodyTop + 1), bot = Math.min(ry + ls.size() * 9 + 3, y1);
					if (bot > top) HITS.add(new Object[]{msg, (float) x0, top, (float) x1, bot});
				}
				// время при наведении
				if (open && m.time() > 0 && mx >= x0 && mx < x1 && my >= ry && my < ry + ls.size() * 9 + 3) {
					String t = LocalDateTime.ofInstant(Instant.ofEpochMilli(m.time()), ZoneId.systemDefault()).format(tf);
					g.setTooltipForNextFrame(font, Component.literal(t + (m.sender() != null ? " · " + m.sender() : "")), mx, my);
				}
			}
			g.disableScissor();
			if (open && sc[2] > 1) {
				float bh = y1 - bodyTop - 4;
				float th = Math.max(10, bh * bh / (bh + sc[2]));
				float ty = bodyTop + 2 + (1 - sc[1] / sc[2]) * (bh - th);
				UI.round(g, x1 - 4, Math.round(ty), x1 - 2, Math.round(ty + th), 1, UI.alpha(UI.accent(), 0.6f));
			}
			RECTS.add(new int[]{i, x0, y0, x1, y1, y0 + headH});
		}
	}

	private static int[] at(double mx, double my) {
		for (int k = RECTS.size() - 1; k >= 0; k--) {
			int[] r = RECTS.get(k);
			if (mx >= r[1] && mx < r[3] && my >= r[2] && my < r[4]) return r;
		}
		return null;
	}

	/** Клик в открытом чате: шапка — перенос, уголок — размер. true — клик по окну. */
	public static boolean click(double mx, double my, int button) {
		int[] r = at(mx, my);
		if (r == null) return false;
		ChatConfig c = ChatConfig.get();
		if (button != 0 || c.locked) return true;
		ExtraWindow w = c.windows.get(r[0]);
		if (mx >= r[3] - 9 && my < r[2] + 9) {
			dragWin = r[0]; dragMode = 2;
			sW = w.w; sH = w.h; sMx = (float) mx; sMy = (float) my;
			return true;
		}
		if (my < r[5]) {
			dragWin = r[0]; dragMode = 1;
			dA = (float) (mx - r[1]); dB = (float) (r[4] - my);
			return true;
		}
		return true;
	}

	public static boolean dragging() { return dragWin >= 0; }

	public static void dragTo(int guiW, int guiH, double mx, double my) {
		ChatConfig c = ChatConfig.get();
		if (dragWin < 0 || dragWin >= c.windows.size()) { dragWin = -1; return; }
		ExtraWindow w = c.windows.get(dragWin);
		if (dragMode == 1) {
			w.left = (int) Math.max(0, Math.min(guiW - w.w, Math.round(mx - dA)));
			w.bottom = (int) Math.max(0, Math.min(guiH - 30, Math.round(guiH - (my + dB))));
		} else {
			float left = w.left < 0 ? guiW - w.w - 4 : w.left;
			w.left = Math.round(left);
			w.w = (int) Math.max(100, Math.min(guiW - left, Math.round(sW + (mx - sMx))));
			w.h = (int) Math.max(30, Math.min(guiH - 30, Math.round(sH - (my - sMy))));
		}
	}

	public static void endDrag() {
		if (dragWin < 0) return;
		dragWin = -1;
		ChatConfig.save();
	}

	/** Колесо над окном — прокрутка его истории. */
	public static boolean scroll(double mx, double my, double sy) {
		int[] r = at(mx, my);
		if (r == null) return false;
		float[] sc = SCROLL.computeIfAbsent(r[0], k -> new float[3]);
		sc[0] = Math.max(0, Math.min(sc[2], sc[0] + (float) sy * 18 * ChatConfig.get().scrollSpeed));
		return true;
	}

	/** Добавить окно по умолчанию: «Личные» у правого края. */
	public static ExtraWindow addDefault(String kind) {
		ExtraWindow w = new ExtraWindow();
		w.kind = kind;
		int idx = java.util.Arrays.asList(KINDS).indexOf(kind);
		w.name = idx >= 0 ? KINDS_RU[idx] : net.primlixe.aurorachat.ui.I18n.t("Окно");
		w.bottom = 60 + ChatConfig.get().windows.size() * 120;
		ChatConfig.get().windows.add(w);
		ChatConfig.save();
		return w;
	}

	private ExtraWindows() {}
}
