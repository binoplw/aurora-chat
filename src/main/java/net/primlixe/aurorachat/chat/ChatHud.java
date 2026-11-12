package net.primlixe.aurorachat.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.primlixe.aurorachat.chat.ChatFormat.Category;
import net.primlixe.aurorachat.chat.ChatFormat.Meta;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.config.ChatConfig.BgStyle;
import net.primlixe.aurorachat.mixin.ChatComponentAccessor;
import net.primlixe.aurorachat.ui.UI;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Окно Aurora Chat. Своя отрисовка вместо ванильной:
 *  - окно можно перетаскивать (за ⠿) и растягивать (за уголок), позиция сохраняется;
 *  - фон: панель / пузыри / без фона, свой цвет, прозрачность, скругление, обводка;
 *  - плавность: окно раскрывается при открытии чата, новые сообщения выезжают и мягко
 *    поднимают старые, плавная прокрутка, плавное затухание;
 *  - сверху: вкладки (Все · G · L · ЛС · @ · Система · свои) и переключатель канала отправки G/L.
 */
public final class ChatHud {
	public enum Mode { HUD, OPEN, PREVIEW }

	public enum Kind { ALL, GLOBAL, LOCAL, DM, MENTIONS, CLAN, STAFF, SYSTEM, CUSTOM }

	public record TabDef(String name, String tip, Kind kind, ChatConfig.CustomTab custom) {}

	/** Строка сообщения в локальных координатах окна (с учётом масштаба текста). */
	private record Row(GuiMessage msg, Meta meta, float x0, float y0, float x1, float y1, float textX, float textY,
	                   List<FormattedCharSequence> lines) {}

	private record Plan(GuiMessage msg, Meta meta, float y, float h, List<FormattedCharSequence> lines, float alpha,
	                    String time, float timeW, float countW, boolean player, boolean self, boolean groupOlder, boolean groupNewer,
	                    int maxLine, int lastW, int textW) {}

	private static int activeTab;
	private static final int[] UNREAD = new int[32];
	private static String query = "";
	private static float scrollPx, smooth, maxScroll;
	private static int newSince;
	/** Прокрутка появилась только из-за паузы под мышью (не руками). */
	private static boolean hoverPaused;
	private static float openT, push, tabX = -1, tabW;
	private static GuiMessage lastTop;
	private static long lastFrame = System.nanoTime();
	private static long errorUntil;
	private static int errors;

	// геометрия последнего кадра (экранные координаты интерфейса)
	private static float wx0, wy0, wx1, wy1, scale = 1;
	private static final List<Row> ROWS = new ArrayList<>();
	private static final List<int[]> TAB_RECTS = new ArrayList<>();
	private static int[] newBtn, gripBtn, resizeBtn, gearBtn, gBtn, lBtn;

	// перетаскивание: 0 — нет, 1 — перенос, 2 — размер
	private static int drag;
	private static float dragA, dragB, startW, startH, startMx, startMy;
	private static long lastGripClick;
	private static Runnable openSettings = () -> {};

	private static final Map<GuiMessage, Object[]> LINES = new com.google.common.collect.MapMaker().weakKeys().makeMap();

	/** Готовая раскладка сообщения: считается один раз, пока не поменялись ширина или вид. */
	private static final class Lay {
		int sig, fmtSig;
		List<FormattedCharSequence> lines;
		String time, low;
		float timeW, countW, rowH;
		int maxLine, lastW;
	}

	private static final Map<GuiMessage, Lay> LAY = new com.google.common.collect.MapMaker().weakKeys().makeMap();

	// высота всей истории с прошлого полного прохода (ключ — всё, от чего она зависит)
	private static int passKeyCached;
	private static float passTotal;
	// при фильтре (вкладка/поиск) — номера подходящих сообщений
	private static int[] passIdx, passIdxBuf = new int[256];
	private static int passIdxCount, passIdxN;

	// плавные (анимированные) значения: положение, размер, масштаб, цвета
	private static float gL = -1, gB, gW, gHf, curScale = -1;
	private static float[] bgCur, bg2Cur;
	private static int edges;
	private static float startL, startBottom;
	private static int[] collapseBtn, titleRect;
	private static long lastTitleClick;
	private static List<GuiMessage> samples;

	public static boolean enabled() { return ChatCore.fancy() && System.currentTimeMillis() > errorUntil; }

	public static void disableAfterError() {
		errors++;
		if (errors >= 3) { ChatCore.cfg().customHud = false; ChatConfig.save(); }
		else errorUntil = System.currentTimeMillis() + 5000;
	}

	public static void setSettingsOpener(Runnable r) { openSettings = r; }

	// ответ одним кликом и закреплённые
	private static int[] replyBtn, pinBtn, pinClose;
	private static String replyText;
	private static int pinIndex;
	private static java.util.function.Consumer<String> inputSetter = s -> {};
	private static final Map<String, String> PIN_TEXT = new java.util.HashMap<>();

	/** Открытый чат сообщает, как вписать текст в строку ввода. */
	public static void setInputSetter(java.util.function.Consumer<String> c) { inputSetter = c; }

	// ====================================================================== вкладки

	public static List<TabDef> tabs() {
		ChatConfig c = ChatCore.cfg();
		List<TabDef> t = new ArrayList<>();
		t.add(new TabDef(net.primlixe.aurorachat.ui.I18n.t("Все"), net.primlixe.aurorachat.ui.I18n.t("Все сообщения"), Kind.ALL, null));
		if (c.channels) {
			t.add(new TabDef(Channels.label('G'), net.primlixe.aurorachat.ui.I18n.t("Глобальный чат"), Kind.GLOBAL, null));
			t.add(new TabDef(Channels.label('L'), net.primlixe.aurorachat.ui.I18n.t("Локальный чат"), Kind.LOCAL, null));
		}
		t.add(new TabDef(net.primlixe.aurorachat.ui.I18n.t("ЛС"), net.primlixe.aurorachat.ui.I18n.t("Личные сообщения"), Kind.DM, null));
		if (c.clanTab) t.add(new TabDef(net.primlixe.aurorachat.ui.I18n.t("Клан"), net.primlixe.aurorachat.ui.I18n.t("Клановый чат (") + c.clanPrefix + ")", Kind.CLAN, null));
		else t.add(new TabDef("@", net.primlixe.aurorachat.ui.I18n.t("Упоминания тебя"), Kind.MENTIONS, null));
		if (c.staffTab && !net.primlixe.aurorachat.Edition.PUBLIC) t.add(new TabDef(net.primlixe.aurorachat.ui.I18n.t("Персонал"), net.primlixe.aurorachat.ui.I18n.t("Хелперы и модераторы: их сообщения и сигналы о них"), Kind.STAFF, null));
		else t.add(new TabDef(net.primlixe.aurorachat.ui.I18n.t("Система"), net.primlixe.aurorachat.ui.I18n.t("Сообщения сервера, входы и выходы"), Kind.SYSTEM, null));
		for (ChatConfig.CustomTab ct : c.customTabs) t.add(new TabDef(ct.name, net.primlixe.aurorachat.ui.I18n.t("Своя вкладка: ") + String.join(", ", ct.keywords), Kind.CUSTOM, ct));
		return t;
	}

	/** Канал, которым сейчас фильтруется «Все» ('G'/'L'), или 0 — фильтр выключен. */
	public static char followChannel() {
		ChatConfig c = ChatCore.cfg();
		return c.channels && c.allFollowsChannel && c.allView.length() == 1 ? c.allView.charAt(0) : 0;
	}

	/** «Все» в режиме «оба канала» (G+L). */
	public static boolean allBoth() {
		ChatConfig c = ChatCore.cfg();
		return c.channels && c.allFollowsChannel && c.allView.equals("GL");
	}

	/** Клик по «Все»: L → G → оба → L. Меняет только то, что видно; куда пишешь — не трогает. */
	static void cycleAll() {
		ChatConfig c = ChatCore.cfg();
		c.allView = switch (c.allView) { case "L" -> "G"; case "G" -> "GL"; default -> "L"; };
		ChatConfig.save();
	}


	/** Зелёный «включено». */
	private static final int ON_GREEN = 0xFF3EF06A;

	// вспышка при смене канала отправки
	private static String lastChannel;
	private static long channelFlashAt;

	/** 1 → 0 за 0,7 с после смены канала (для вспышки и пульса). */
	private static float channelFlash() {
		// вспышка — и когда сменили канал отправки, и когда сменили, что показывает «Все»
		String ch = ChatCore.cfg().channel + "|" + ChatCore.cfg().allView;
		if (lastChannel == null) lastChannel = ch;
		if (!ch.equals(lastChannel)) { lastChannel = ch; channelFlashAt = System.nanoTime(); scrollPx = 0; }
		if (channelFlashAt == 0) return 0;
		float t = (System.nanoTime() - channelFlashAt) / 1e9f / 0.7f;
		return t >= 1 ? 0 : 1 - t;
	}

	/** Клановое сообщение: начинается с «КЛАН:» (допускается короткая приставка вроде [G] или времени перед ним). */
	public static boolean isClan(String plainLower) {
		return ChatCore.cfg().clanTab && isClanText(plainLower);
	}

	/** То же без учёта переключателя вкладки — для отдельного окна «Клан». */
	public static boolean isClanText(String plainLower) {
		ChatConfig c = ChatCore.cfg();
		if (plainLower == null) return false;
		String p = c.clanPrefix.toLowerCase(Locale.ROOT);
		int i = plainLower.indexOf(p);
		return i >= 0 && i <= 16;
	}

	/** Убрать из этой вкладки (и свёрнутого чата), потому что сообщение ушло во вкладку «Клан». */
	static boolean hiddenAsClan(TabDef t, String plainLower) {
		return ChatCore.cfg().clanHide && t.kind() != Kind.CLAN && isClan(plainLower);
	}

	static String shortName(TabDef t) {
		return switch (t.kind()) {
			case DM -> "✉"; case MENTIONS -> "@"; case CLAN -> "⚑"; case STAFF -> "★"; case SYSTEM -> "✦";
			default -> t.name();
		};
	}

	/** Сообщение от персонала (или сигнал мода о нём: «⚑ Хелпер Ник — зашёл»). */
	public static boolean isStaff(Meta m, String plainLower) {
		if (m.sender() != null && m.cat() != Category.JOIN && m.cat() != Category.LEAVE && StaffWatch.find(m.sender()) != null) return true;
		return plainLower != null && plainLower.startsWith("⚑ ") && !plainLower.contains("нарушил «"); // сигналы StaffWatch, не модерация
	}

	static boolean matches(TabDef t, Meta m, String plainLower) {
		if (hiddenAsClan(t, plainLower)) return false;
		return switch (t.kind()) {
			case ALL -> true;
			case CLAN -> isClan(plainLower);
			case STAFF -> isStaff(m, plainLower);
			case GLOBAL -> m.channel() == 'G';
			case LOCAL -> m.channel() == 'L';
			case DM -> m.cat() == Category.DM;
			case MENTIONS -> m.cat() == Category.MENTION;
			case SYSTEM -> m.cat() != Category.PLAYER && m.cat() != Category.MENTION && m.cat() != Category.DM;
			case CUSTOM -> {
				for (String k : t.custom().keywords) if (!k.isBlank() && plainLower.contains(k.toLowerCase(Locale.ROOT))) yield true;
				yield false;
			}
		};
	}

	/** Сообщение подменили на месте (перевод) — это не новое сообщение: без анимации появления и счётчиков. */
	public static void replaced(GuiMessage old, GuiMessage now) {
		if (lastTop == old) lastTop = now;
		passKeyCached = 0; // высота строки поменялась
	}

	public static void onIncoming(Meta m, String plain) {
		var self = Minecraft.getInstance().player;
		if (self != null && m.sender() != null && m.sender().equalsIgnoreCase(self.getName().getString())) SendFx.claim(m.time());
		boolean open = net.primlixe.aurorachat.compat.Mc.screen() instanceof net.minecraft.client.gui.screens.ChatScreen;
		List<TabDef> tabs = tabs();
		String low = plain.toLowerCase(Locale.ROOT);
		// сообщение уже видно там, куда смотришь (закрытый чат показывает «Все»), — другие вкладки его не считают
		int viewing = open && ChatCore.cfg().tabs && activeTab < tabs.size() ? activeTab : 0;
		String selfName = self != null ? self.getName().getString() : "";
		boolean seen = visibleIn(tabs.get(viewing), m, low, selfName);
		if (!seen) {
			for (int i = 1; i < tabs.size() && i < UNREAD.length; i++) {
				if (i != viewing && matches(tabs.get(i), m, low)) UNREAD[i]++;
			}
		}
		if (seen && smooth > 4) newSince++; // «↓ N нов.» — только то, что появится в этой вкладке
	}

	/** Видно ли сообщение во вкладке t сейчас — с учётом фильтра «Все» (L/G/оба), скрытия клана и своих сообщений. */
	static boolean visibleIn(TabDef t, Meta m, String low, String selfName) {
		if (t.kind() != Kind.ALL) return matches(t, m, low);
		if (m.sender() != null && m.sender().equalsIgnoreCase(selfName)) return true; // своё видно всегда
		char follow = followChannel();
		if (follow != 0 && m.channel() != 0 && m.channel() != follow) return false;
		return !(ChatCore.cfg().clanTab && ChatCore.cfg().clanHide && isClan(low));
	}

	public static void setTab(int i) { activeTab = i; scrollPx = 0; }

	public static int tab() { return activeTab; }

	private static SearchQuery search = new SearchQuery();

	/** Умный поиск: слова + фильтры ник:, канал:, тип:, дата:, после:, до:, -слово. */
	public static void setQuery(String q) {
		query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
		search = SearchQuery.parse(query);
		if (search.isEmpty()) query = "";
		scrollPx = 0;
	}

	public static void scroll(int lines) {
		long win = Minecraft.getInstance().getWindow().handle();
		boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
				|| org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
		float step = lines * (9 + ChatCore.cfg().lineSpacing) * 2 * ChatCore.cfg().scrollSpeed * (shift ? 3 : 1);
		hoverPaused = false;
		scrollPx = Math.max(0, Math.min(maxScroll, scrollPx + step));
	}

	/** Колесо мыши: дробные шаги (SmoothScroll, тачпад) не теряются; 3 строки за щелчок, Shift — страница. */
	public static void wheel(double sy) {
		long win = Minecraft.getInstance().getWindow().handle();
		boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
				|| org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
		float line = 9 + ChatCore.cfg().lineSpacing;
		float step = (float) sy * line * 3 * ChatCore.cfg().scrollSpeed * (shift ? 4 : 1);
		hoverPaused = false;
		scrollPx = Math.max(0, Math.min(maxScroll, scrollPx + step));
	}

	// полоса прокрутки (экранные координаты)
	private static int[] scrollBtn;
	private static float sbTop, sbH, sbThumb;

	/** Настенное время в мс, но гладкое (currentTimeMillis на Windows идёт шагами ~15 мс) — из Aurora Lib. */
	private static double smoothNowMs() { return net.primlixe.auroralib.anim.Anim.nowMs(); }

	public static void resetScroll() { scrollPx = 0; newSince = 0; hoverPaused = false; }

	private static List<FormattedCharSequence> lines(Font font, GuiMessage m, int width) {
		Object[] c = LINES.get(m);
		if (c != null && (int) c[0] == width) {
			@SuppressWarnings("unchecked") List<FormattedCharSequence> l = (List<FormattedCharSequence>) c[1];
			return l;
		}
		List<FormattedCharSequence> l = font.split(m.content(), Math.max(20, width));
		LINES.put(m, new Object[]{width, l});
		return l;
	}

	/** Примеры для предпросмотра в настройках, если чат пуст. */
	private static List<GuiMessage> samples() {
		if (samples != null) return samples;
		long now = System.currentTimeMillis() - 60_000;
		String[][] s = {
				{net.primlixe.aurorachat.ui.I18n.t("Добро пожаловать на сервер! Удачной игры ✦"), "SYS", ""},
				{net.primlixe.aurorachat.ui.I18n.t("Steve: всем привет!"), "G", "Steve"},
				{net.primlixe.aurorachat.ui.I18n.t("Alex: кто со мной в шахту?"), "L", "Alex"},
				{net.primlixe.aurorachat.ui.I18n.t("[Steve -> me] есть минутка?"), "DM", "Steve"},
				{net.primlixe.aurorachat.ui.I18n.t("Alex: Player, глянь сюда!"), "M", "Alex"},
		};
		List<GuiMessage> out = new ArrayList<>();
		for (String[] r : s) {
			Component c = Component.literal(r[0]);
			if (!r[2].isEmpty() && ChatCore.cfg().colorNames) c = ChatFormat.colorName(c, r[2]);
			if (r[1].equals("SYS")) c = c.copy().withStyle(ChatFormatting.YELLOW);
			if (r[1].equals("DM")) c = c.copy().withStyle(ChatFormatting.LIGHT_PURPLE);
			Category cat = switch (r[1]) { case "SYS" -> Category.SYSTEM; case "DM" -> Category.DM; case "M" -> Category.MENTION; default -> Category.PLAYER; };
			char ch = r[1].equals("G") ? 'G' : r[1].equals("L") || r[1].equals("M") ? 'L' : 0;
			ChatCore.putMeta(c, new Meta(now, cat, r[2].isEmpty() ? null : r[2], r[1].equals("M") ? 2 : 1, ch));
			out.add(0, new GuiMessage(0, c, null, GuiMessageSource.SYSTEM_CLIENT, null));
		}
		samples = out;
		return out;
	}

	// ====================================================================== геометрия окна

	/** Область предпросмотра (экран настроек): окно рисуется внутри неё, прижатым к левому нижнему углу. */
	private static int[] previewBox;

	public static void renderPreview(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int y1) {
		previewBox = new int[]{x0, y0, x1, y1};
		try {
			render(g, font, Mode.PREVIEW, -1000, -1000);
		} finally {
			previewBox = null;
		}
	}

	/** Плавно ведёт положение и размер окна к настройкам (при перетаскивании — сразу). */
	private static void animateGeometry(int guiW, int guiH, float dt) {
		ChatConfig c = ChatCore.cfg();
		float W = Math.min(c.winW, guiW - 4);
		float Hf = Math.min(c.collapsed ? Math.min(c.winH, 40) : c.winH, guiH - 44);
		float left = Math.max(0, Math.min(c.winLeft, guiW - W));
		float bottom = guiH - Math.max(0, Math.min(c.winBottom, guiH - 30));
		bottom = Math.max(bottom, Hf + 18);
		if (gL < 0 || drag == 1 || drag == 2 || !net.primlixe.aurorachat.ui.Perf.anim()) {
			gL = left; gB = bottom; gW = W; gHf = Hf;
		} else {
			float rate = 16 * c.animSpeed;
			gL = UI.damp(gL, left, rate, dt);
			gB = UI.damp(gB, bottom, rate, dt);
			gW = UI.damp(gW, W, rate, dt);
			gHf = UI.damp(gHf, Hf, rate, dt);
		}
		float sc = c.textScale;
		curScale = curScale < 0 || !net.primlixe.aurorachat.ui.Perf.anim() ? sc : UI.damp(curScale, sc, 14 * c.animSpeed, dt);
		bgCur = dampColor(bgCur, c.bgColor, dt);
		bg2Cur = dampColor(bg2Cur, c.bgColor2, dt);
	}

	private static float[] dampColor(float[] cur, int rgb, float dt) {
		float r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		if (cur == null || !net.primlixe.aurorachat.ui.Perf.anim()) return new float[]{r, g, b};
		float k = 10 * ChatCore.cfg().animSpeed;
		cur[0] = UI.damp(cur[0], r, k, dt);
		cur[1] = UI.damp(cur[1], g, k, dt);
		cur[2] = UI.damp(cur[2], b, k, dt);
		return cur;
	}

	private static int rgb(float[] c) { return 0xFF000000 | (Math.round(c[0]) << 16) | (Math.round(c[1]) << 8) | Math.round(c[2]); }

	/** {left, top, right, bottom} окна при доле раскрытия oe. */
	private static float[] window(int guiW, int guiH, float oe) {
		ChatConfig c = ChatCore.cfg();
		if (previewBox != null) {
			int[] b = previewBox;
			float W = Math.min(gW, b[2] - b[0] - 8);
			float H = Math.min(gHf, b[3] - b[1] - 26);
			return new float[]{b[0] + 4, b[3] - 4 - H, b[0] + 4 + W, b[3] - 4};
		}
		float H = gHf * (c.closedHeight + (1 - c.closedHeight) * oe);
		return new float[]{gL, gB - H, gL + gW, gB};
	}

	/** Край окна под курсором: 1 — левый, 2 — правый, 4 — верх, 8 — низ (можно сочетать). */
	private static int edgeAt(double mx, double my) {
		if (ChatCore.cfg().locked) return 0;
		if (mx < wx0 - 3 || mx > wx1 + 3 || my < wy0 - 3 || my > wy1 + 3) return 0;
		int f = 0;
		if (mx >= wx0 - 3 && mx <= wx0 + 1) f |= 1;
		if (mx >= wx1 - 1 && mx <= wx1 + 3) f |= 2;
		if (my >= wy0 - 3 && my <= wy0 + 1) f |= 4;
		if (my >= wy1 - 1 && my <= wy1 + 3) f |= 8;
		return f;
	}

	private static com.mojang.blaze3d.platform.cursor.CursorType cursorFor(int e) {
		boolean h = (e & 3) != 0, v = (e & 12) != 0;
		if (h && v) return com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL;
		if (h) return com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_EW;
		return com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_NS;
	}

	// ====================================================================== отрисовка

	public static void render(GuiGraphicsExtractor g, Font font, Mode mode, int mx, int my) {
		var mc = Minecraft.getInstance();
		boolean open = mode == Mode.OPEN, preview = mode == Mode.PREVIEW;
		if (!preview) {
			ROWS.clear();
			TAB_RECTS.clear();
			newBtn = gripBtn = resizeBtn = gearBtn = gBtn = lBtn = scrollBtn = collapseBtn = titleRect = null;
			replyBtn = pinBtn = pinClose = null;
		}
		ChatVisiblity vis = mc.options.chatVisibility().get();
		if (vis == ChatVisiblity.HIDDEN && !preview) return;
		ChatConfig s = ChatCore.cfg();
		boolean anim = net.primlixe.aurorachat.ui.Perf.anim();
		float aspd = s.animSpeed;

		long nowNs = System.nanoTime();
		float dt = Math.min(0.1f, (nowNs - lastFrame) / 1e9f);
		lastFrame = nowNs;
		animateGeometry(g.guiWidth(), g.guiHeight(), dt);

		float oe;
		if (preview) oe = 1;
		else {
			openT = anim && s.openAnimation ? UI.damp(openT, open ? 1 : 0, 13 * aspd * s.openSpeed, dt) : (open ? 1 : 0);
			if (Math.abs(openT - (open ? 1 : 0)) < 0.002f) openT = open ? 1 : 0;
			oe = Math.max(0, ease(openT));
			if (!open) scrollPx = 0;
			// быстрый отклик: колесо сразу двигает чат, плавность — только «доводка» (~60 мс)
			float scrollRate = 22 + (1 - s.scrollSmoothness) * 40;
			// прокрутка плавная и в экономном режиме — она почти ничего не стоит
			smooth = s.smoothScroll && s.scrollSmoothness > 0.01f ? UI.damp(smooth, scrollPx, scrollRate, dt) : scrollPx;
			if (Math.abs(smooth - scrollPx) < 0.05f) smooth = scrollPx;
			push = s.animations && s.slideIn ? UI.damp(push, 0, 11 * aspd * s.pushSpeed, dt) : 0;
			if (Math.abs(push) < 0.05f) push = 0;
		}
		float oc = Math.min(1, oe); // для прозрачностей — без «пружинного» перелёта
		float titleH = s.titleBar && s.bgStyle != BgStyle.MINIMAL ? 14 * (preview ? 1 : oc) : 0;
		float pinH = s.pins && !s.pinned.isEmpty() && (open || preview) ? 14 * (preview ? 1 : oc) : 0;
		float topInset = titleH + pinH;

		float[] win = window(g.guiWidth(), g.guiHeight(), oe);
		float X0 = win[0], Y0 = win[1], X1 = win[2], Y1 = win[3];
		scale = curScale;
		float w = (X1 - X0) / scale, h = (Y1 - Y0) / scale;
		if (!preview) { wx0 = X0; wy0 = Y0; wx1 = X1; wy1 = Y1; }

		int gt = net.primlixe.aurorachat.compat.Mc.guiTicks();
		long now = System.currentTimeMillis();
		List<TabDef> tabs = tabs();
		if (activeTab >= tabs.size()) activeTab = 0;
		TabDef tab = open && s.tabs ? tabs.get(activeTab) : tabs.get(0);
		if (open && activeTab < UNREAD.length) UNREAD[activeTab] = 0; // своих вкладок может быть больше 32
		BgStyle style = s.bgStyle;
		boolean bubbles = style == BgStyle.BUBBLES;
		int lineH = 9 + s.lineSpacing;
		int pad = s.rowPadding + (bubbles ? 2 : 0);
		float headW = s.heads ? s.headSize + 3 : 0;
		boolean timeLeft = s.timePos == ChatConfig.TimePos.LEFT;
		DateTimeFormatter tf = ChatCore.timeFormatter();
		int fmtSig = (s.timestamps ? s.timeFormat.hashCode() : 1) + Float.floatToIntBits(s.timeScale) * 31 + (s.timeBold ? 7 : 0);

		// ---------- раскладка снизу вверх
		List<GuiMessage> all = ((ChatComponentAccessor) net.primlixe.aurorachat.compat.Mc.chat()).aurorachat$all();
		if (preview && all.isEmpty()) all = samples();
		boolean pending = false;
		int pendingN = 0; // сколько сообщений пришло с прошлого кадра (их может быть несколько сразу)
		if (!preview && !all.isEmpty() && all.get(0) != lastTop) {
			if (lastTop != null) {
				pendingN = 1;
				for (int k = 1; k < Math.min(all.size(), 300); k++) if (all.get(k) == lastTop) { pendingN = k; break; }
			}
			pending = pendingN > 0;
			lastTop = all.get(0);
		}
		// пауза под мышью (как на Twitch): пока курсор над чатом, новые сообщения не двигают текст
		boolean hoverNow = open && !preview && s.pauseOnHover && mx >= wx0 && mx < wx1 && my >= wy0 && my < wy1;
		if (!preview && hoverPaused && !hoverNow) { hoverPaused = false; scrollPx = 0; } // убрал мышь — плавно вниз
		float y = h - 2 + (open ? smooth : 0) + (preview ? 0 : push);
		float total = 0;
		List<Plan> plan = new ArrayList<>();
		ChatConfig.Layout layout = s.layout;
		String selfName = mc.player != null ? mc.player.getName().getString() : "";
		// Полный проход по истории нужен только для высоты (полоса прокрутки). Пока ничего не поменялось —
		// берём её из прошлого прохода и идём только по видимым строкам: 2000 сообщений ≠ 2000 строк работы в кадр.
		// «Все» показывает только выбранный канал отправки (G или L); без канала (система, ЛС) — остаётся
		char follow = !preview && (!open || tab.kind() == Kind.ALL) ? followChannel() : 0;
		int passKey = 0;
		if (open && !preview) {
			passKey = java.util.Objects.hash(all.size(), all.isEmpty() ? 0 : System.identityHashCode(all.get(0)), activeTab, query, (int) follow,
					(int) w, layout, fmtSig, lineH, pad, s.chatFont, vis, s.heads, s.headSize, bubbles, s.messageGap, s.channels, s.channelBadges, s.clanTab, s.clanHide, s.clanPrefix);
		}
		boolean reuse = passKey != 0 && passKey == passKeyCached;
		boolean incomplete = false;
		int splitBudget = 50; // новые переносы строк для невидимых сообщений — порциями, без рывка при открытии
		// вкладка/поиск: подходящих мало, а пройти пришлось бы всю историю — идём по списку совпавших с полного прохода
		boolean filtered = open && (tab.kind() != Kind.ALL || !query.isEmpty() || follow != 0);
		boolean byIndex = reuse && filtered && passIdx != null;
		if (passKey != 0 && !reuse) passIdxN = 0;
		long tl = net.primlixe.aurorachat.ui.Perf.LAYOUT.start();
		int count = byIndex ? passIdxCount : all.size();
		for (int k = 0; k < count; k++) {
			int idx = byIndex ? passIdx[k] : k;
			if (idx >= all.size()) break;
			GuiMessage msg = all.get(idx);
			Meta m = ChatCore.meta(msg.content());
			if (vis == ChatVisiblity.SYSTEM && (m.cat() == Category.PLAYER || m.cat() == Category.DM || m.cat() == Category.MENTION)) continue;
			float alpha = 1f;
			if (!preview && ExtraWindows.takesExclusive(msg, m)) continue; // ушло в отдельное окно
			// своё сообщение видно всегда — иначе кажется, что Enter не отправил (клан и чужой канал прятались)
			boolean mine = m.sender() != null && m.sender().equalsIgnoreCase(selfName);
			if (!mine && follow != 0 && m.channel() != 0 && m.channel() != follow) continue; // другой канал — скрыт, пока выбран этот
			Lay L = LAY.computeIfAbsent(msg, key -> new Lay());
			if (!preview && !mine && s.clanTab && s.clanHide) {
				if (L.low == null) L.low = msg.content().getString().toLowerCase(Locale.ROOT);
				if ((!open || tab.kind() == Kind.ALL) && isClan(L.low)) continue; // клан — только во вкладке «Клан»
			}
			if (open) {
				if (tab.kind() != Kind.ALL || !query.isEmpty()) {
					if (L.low == null) L.low = msg.content().getString().toLowerCase(Locale.ROOT);
					if (tab.kind() != Kind.ALL && !matches(tab, m, L.low)) continue;
					if (!query.isEmpty() && !search.matches(L.low, m)) continue;
				}
				if (passKey != 0 && !reuse && filtered) {
					if (passIdxBuf.length == passIdxN) passIdxBuf = java.util.Arrays.copyOf(passIdxBuf, passIdxN * 2);
					passIdxBuf[passIdxN++] = idx;
				}
			} else if (!preview) {
				int age = gt - msg.addedTime();
				float fade = s.fadeSeconds * 20;
				float ageA = age < 0 ? 1 : Math.max(0, Math.min(1f, (fade - age) / (s.fadeOutTime * 20f)));
				alpha = Math.max(ageA, oc);
				if (alpha <= 0.01f) { if (oc < 0.01f) break; continue; }
			}
			// время и ×N — один раз на сообщение (и при смене формата)
			if (L.time == null || L.fmtSig != fmtSig) {
				L.time = s.timestamps && m.time() > 0
						? LocalDateTime.ofInstant(Instant.ofEpochMilli(m.time()), ZoneId.systemDefault()).format(tf) : "";
				L.timeW = L.time.isEmpty() ? 0 : timeWidth(font, L.time) + 6;
				L.countW = m.count() > 1 ? font.width("×" + m.count()) + 10 : 0;
				L.fmtSig = fmtSig;
				L.sig = Integer.MIN_VALUE;
			}
			boolean player = isPlayer(m);
			// группы: соседние сообщения одного игрока в пределах 5 минут
			boolean groupOlder = false, groupNewer = false;
			if (layout != ChatConfig.Layout.COMPACT && player) {
				if (idx + 1 < all.size()) groupOlder = sameGroup(m, ChatCore.meta(all.get(idx + 1).content()));
				if (idx > 0) groupNewer = sameGroup(ChatCore.meta(all.get(idx - 1).content()), m);
			}
			boolean self = player && m.sender().equalsIgnoreCase(selfName);
			float badge = badgeWidth(font, s, m);
			int textW = switch (layout) {
				case COZY -> (int) (w - 10 - pad * 2 - (s.heads ? 20 : 4) - L.countW);
				case MESSENGER -> (int) (w * 0.74f) - 12;
				default -> (int) (w - 8 - pad * 2 - headW - badge - L.timeW - L.countW);
			};
			textW = Math.max(20, textW & ~3); // шаг 4px — меньше пересчётов при растягивании
			int sig = textW * 31 + lineH * 1009 + pad * 7919 + (int) headW * 104729 + (bubbles ? 1 : 0) + layout.ordinal() * 15485863 + (player ? 3 : 0)
					+ s.chatFont.hashCode() * 7;
			// перенос строк — только при изменении ширины/вида, далёкие от экрана — лениво и порциями
			boolean far = open && y < -h * 1.5f;
			if (L.lines == null && far && splitBudget <= 0) {
				// ещё не разложено и далеко за экраном — оценка в одну строку, доложим в следующих кадрах
				incomplete = true;
				float est = Math.max(lineH, s.heads ? s.headSize : 0) + pad * 2 + 1;
				y -= est;
				total += est + s.messageGap;
				y -= s.messageGap;
				continue;
			}
			if (L.lines == null || (L.sig != sig && !far)) {
				if (far) splitBudget--;
				if (L.lines != null) passKeyCached = 0; // высота могла поменяться — пересчитать полосу прокрутки
				Component body = layout != ChatConfig.Layout.COMPACT && player ? stripSender(msg.content(), m.sender()) : msg.content();
				L.lines = font.split(net.primlixe.aurorachat.ui.Fonts.wrap(body, s.chatFont), textW);
				int ml = 0;
				for (FormattedCharSequence l : L.lines) ml = Math.max(ml, font.width(l));
				L.maxLine = ml;
				L.lastW = L.lines.isEmpty() ? 0 : font.width(L.lines.get(L.lines.size() - 1));
				L.sig = sig;
			}
			List<FormattedCharSequence> ls = L.lines;
			float textH = ls.size() * lineH - s.lineSpacing;
			float rowH, gap;
			switch (layout) {
				case COZY -> {
					if (player) rowH = (groupOlder ? textH : Math.max(lineH + textH, s.heads ? 18 : 0)) + pad * 2 + 1;
					else rowH = Math.max(textH, s.heads ? s.headSize : 0) + pad * 2 + 1;
					gap = (groupOlder ? 0 : 3) + s.messageGap;
				}
				case MESSENGER -> {
					if (player) {
						float nameH = !self && !groupOlder ? lineH : 0;
						float stamp = stampW(font, L.time, m.count());
						boolean inline = stamp == 0 || L.lastW + stamp + 4 <= textW;
						rowH = nameH + textH + (inline ? 0 : lineH) + 8;
					} else rowH = textH + 6;
					gap = (groupOlder ? 1 : 4) + s.messageGap;
				}
				default -> {
					rowH = Math.max(textH, s.heads ? s.headSize : 0) + pad * 2 + (bubbles ? 0 : 1);
					gap = s.messageGap + (bubbles ? 1 : 0);
				}
			}
			if (pending && idx < pendingN && !byIndex) {
				float d = rowH + gap;
				if (open && (scrollPx > 0 || hoverNow)) { // читаешь историю или мышь над чатом — вид не прыгает
					if (scrollPx <= 0) hoverPaused = true;
					scrollPx += d; smooth += d; y += d;
				}
				else if (s.animations && s.slideIn) { push += d; y += d; }              // новое сообщение плавно поднимает старые
			}
			y -= rowH;
			if (!open && !preview && oc < 0.01f && y < 0) break; // в закрытом чате — только целые сообщения
			total += rowH + gap;
			if (y + rowH >= -2 && y <= h + 2) {
				plan.add(new Plan(msg, m, y, rowH, ls, alpha, L.time, L.timeW, L.countW, player, self, groupOlder, groupNewer, L.maxLine, L.lastW, textW));
			}
			y -= gap;
			if (!open && y < -4) break;
			if (reuse && y < -4) break; // выше окна — уже не видно, высота известна
		}
		if (reuse) total = passTotal;
		else if (passKey != 0) {
			if (incomplete) passKeyCached = 0;
			else {
				passKeyCached = passKey;
				passTotal = total;
				passIdx = filtered ? passIdxBuf : null;
				passIdxCount = passIdxN;
			}
		}
		net.primlixe.aurorachat.ui.Perf.LAYOUT.stop(tl);
		if (!preview) {
			maxScroll = Math.max(0, total - h + 4 + topInset / scale);
			if (scrollPx > maxScroll) scrollPx = maxScroll;
		}
		if (plan.isEmpty() && oc < 0.01f) return;

		// ---------- фон (субпиксельно: целые размеры + дробный сдвиг матрицей — без «ступенек» при движении)
		float bgA = s.bgOpacityClosed + (s.bgOpacity - s.bgOpacityClosed) * oc;
		int bgRgb = rgb(bgCur);
		int bgTop = s.bgGradient ? rgb(bg2Cur) : UI.mix(bgRgb, 0xFFFFFFFF, 0.04f);
		float contentTop = Y1;
		float maxAlpha = 0;
		for (Plan p : plan) {
			contentTop = Math.min(contentTop, Y0 + p.y() * scale);
			maxAlpha = Math.max(maxAlpha, p.alpha());
		}
		maxAlpha = Math.max(maxAlpha, oc);
		int r = s.radius;
		float panelTop = contentTop - 3 + (Y0 - (contentTop - 3)) * oe;
		panelTop = Math.max(Y0, Math.min(Y1 - 4, panelTop));
		float panelA = switch (style) {
			case PANEL -> bgA * maxAlpha;
			case BUBBLES, MINIMAL -> bgA * 0.55f * oc;
		};
		if (panelA > 0.01f) {
			int pw = Math.round(X1 - X0), ph = Math.round(Y1 - panelTop);
			g.pose().pushMatrix();
			g.pose().translate(X0, Y1 - ph);
			if (s.shadow && !net.primlixe.aurorachat.ui.Perf.lite() && oc > 0.05f) UI.shadow(g, 0, 0, pw, ph, r, oc * Math.min(1, panelA * 1.5f));
			UI.roundGradient(g, 0, 0, pw, ph, r, UI.alpha(bgTop, panelA), UI.alpha(bgRgb, panelA));
			// своя картинка поверх цвета фона: гаснет вместе с панелью (закрытый чат — прозрачнее)
			if (!s.bgImage.isEmpty() && style == BgStyle.PANEL)
				net.primlixe.aurorachat.ui.BgImage.draw(g, pw, ph, r, s.bgImageOpacity * Math.min(1, panelA / Math.max(0.05f, s.bgOpacity)));
			if (s.outline) UI.roundOutline(g, 0, 0, pw, ph, r, UI.alpha(UI.accent(), s.outlineOpacity * (0.45f + 0.55f * oc) * maxAlpha));
			g.pose().popMatrix();
		}

		// ---------- строки: каждая рисуется от своей дробной точки — всё ровно и плавно
		long td = net.primlixe.aurorachat.ui.Perf.DRAW.start();
		g.enableScissor((int) X0, (int) (Y0 + topInset), (int) Math.ceil(X1), (int) Y1 + 1);
		g.pose().pushMatrix();
		g.pose().translate(X0, Y0);
		g.pose().scale(scale, scale);
		float lmx = (mx - X0) / scale, lmy = (my - Y0) / scale;
		Row hovered = null;
		int textBase = 0xFF000000 | s.textColor;
		int timeCol = 0xFF000000 | s.timestampColor;
		int index = 0;
		for (Plan p : plan) {
			Meta m = p.meta();
			float alpha = p.alpha();
			float appear = 1f;
			// появление плавное и в экономном режиме; время — точное (nanoTime), а не шагами по ~15 мс
			if (s.animations && s.slideIn && s.appear != ChatConfig.Appear.NONE && m.time() > 0 && !preview && !SendFx.instant(m.time())) {
				appear = ease((float) Math.max(0, smoothNowMs() - m.time()) / (s.appearTime * 1000f / aspd));
			}
			float ac = Math.max(0, Math.min(1, appear));
			alpha *= switch (s.appear) {
				case FADE, POP -> ac;
				default -> 0.15f + 0.85f * ac;
			};
			if (alpha <= 0.02f) { index++; continue; }
			float dx = s.appear == ChatConfig.Appear.SLIDE ? (1 - appear) * -s.slideDistance : 0;
			float dy = s.appear == ChatConfig.Appear.RISE ? (1 - appear) * s.slideDistance * 0.7f : 0;
			int H = Math.round(p.h());
			float y0 = p.y() + dy;
			int cat = m.cat() == Category.PLAYER && m.sender() != null && s.colorNames ? (0xFF000000 | ChatFormat.nameColor(m.sender())) : m.cat().color;
			if (s.badgeStyle == ChatConfig.BadgeStyle.BAR && s.channels && m.channel() != 0) cat = Channels.color(m.channel());
			// заметный канал: полоска и оттенок строки цветом G/L — видно даже когда чат быстро бежит
			boolean chHi = s.channelHighlight && s.channels && m.channel() != 0 && layout != ChatConfig.Layout.MESSENGER;
			if (chHi) cat = Channels.color(m.channel());
			int ta = UI.alpha(textBase, alpha * s.textOpacity);

			// ---- геометрия строки по раскладке
			float ox, rw;
			if (layout == ChatConfig.Layout.MESSENGER) {
				if (p.player()) {
					float stamp = stampW(font, p.time(), m.count());
					boolean inline = stamp == 0 || p.lastW() + stamp + 4 <= p.textW();
					int nameW = !p.self() && !p.groupOlder() ? font.width(Nicknames.display(m.sender())) + (badgeWidth(font, s, m) > 0 ? 12 : 0) : 0;
					int inner = Math.max(p.maxLine(), Math.max(nameW, inline ? p.lastW() + (int) stamp + 4 : (int) stamp));
					rw = inner + 10;
					float av = !p.self() && s.heads ? 12 : 0;
					ox = p.self() ? w - 6 - rw - dx : 3 + av + dx;
				} else {
					rw = p.maxLine() + 14;
					ox = (w - rw) / 2;
				}
			} else if (bubbles && layout == ChatConfig.Layout.COMPACT) {
				ox = 3 + dx;
				rw = Math.min(w - 6, pad * 2 + headW + badgeWidth(font, s, m) + 4 + p.maxLine() + p.timeW() + p.countW() + 2);
			} else {
				ox = 3 + dx;
				rw = w - 6;
			}
			int W = Math.round(rw);
			boolean hover = open && lmx >= ox && lmx < ox + W && lmy >= y0 && lmy < y0 + H && my >= Y0 + topInset && my <= Y1;

			g.pose().pushMatrix();
			g.pose().translate(ox, y0);
			boolean pop = s.appear == ChatConfig.Appear.POP && appear < 1;
			if (pop) {
				float sc = 0.85f + 0.15f * appear;
				g.pose().translate(0, H / 2f);
				g.pose().scale(sc, sc);
				g.pose().translate(0, -H / 2f);
			}
			if (chHi) {
				if (s.channelTint > 0.005f) g.fill(0, 0, W, H, UI.alpha(cat, s.channelTint * alpha));
				if (s.channelStripe > 0) UI.round(g, 0, 0, s.channelStripe, H, 1, UI.alpha(cat, alpha));
			}
			// текст сообщения — с примесью цвета канала (только неокрашенные куски, цвета сервера не трогаются)
			if (s.channels && m.channel() != 0 && s.channelTextTint > 0.005f)
				ta = UI.alpha(UI.mix(textBase, Channels.color(m.channel()), s.channelTextTint), alpha * s.textOpacity);
			int textX, textY;
			if (layout == ChatConfig.Layout.MESSENGER) {
				int[] t = drawMessenger(g, font, s, p, m, W, H, alpha, ta, timeCol, bgRgb, bgA, hover, lineH, cat, mc);
				textX = t[0]; textY = t[1];
			} else if (layout == ChatConfig.Layout.COZY) {
				int[] t = drawCozy(g, font, s, p, m, W, H, alpha, ta, timeCol, hover, lineH, pad, cat, index, mc);
				textX = t[0]; textY = t[1];
			} else {
				int[] t = drawCompact(g, font, s, p, m, W, H, alpha, ta, timeCol, bgRgb, bgA, hover, lineH, pad, (int) headW, timeLeft, bubbles, cat, index, mc);
				textX = t[0]; textY = t[1];
			}
			g.pose().popMatrix();
			Row row = new Row(p.msg(), m, ox, y0, ox + W, y0 + H, ox + textX, y0 + textY, p.lines());
			if (!preview) ROWS.add(row);
			if (hover) hovered = row;
			index++;
		}
		g.pose().popMatrix();
		g.disableScissor();
		net.primlixe.aurorachat.ui.Perf.DRAW.stop(td);

		// ---------- кнопка «ответить» у сообщения под курсором
		if (open && s.replyButton && hovered != null && hovered.meta().sender() != null && drag == 0) drawReply(g, font, hovered, X0, Y0, mc);

		// ---------- закреплённые
		if (pinH > 0.5f) drawPins(g, font, s, X0, Y0 + titleH, X1, pinH / 14f, bgRgb, bgA, mx, my);

		// ---------- строка заголовка (деловой вид): вкладка · онлайн · часы
		float ta = preview ? 1 : oc;
		float chFlash = preview ? 0 : channelFlash();   // вспышка после смены G/L
		char allCh = preview ? 0 : followChannel();     // «Все» сейчас показывает только этот канал
		String allTag = allCh != 0 ? String.valueOf(allCh) : !preview && allBoth() ? "GL" : null; // что написать у «Все»
		if (s.titleBar && ta > 0.05f && style != BgStyle.MINIMAL) {
			g.pose().pushMatrix();
			g.pose().translate(X0 - (float) Math.floor(X0), Y0 - (float) Math.floor(Y0));
			int tb0 = (int) Y0, tb1 = (int) Y0 + 13;
			if (open) titleRect = new int[]{(int) X0 + 2, tb0 + 2, (int) X1 - 12, tb1};
			UI.round(g, (int) X0 + 1, tb0 + 1, (int) X1 - 1, tb1, Math.max(0, r - 1), UI.alpha(bgTop, Math.min(1, bgA + 0.25f) * ta));
			g.fill((int) X0 + 4, tb1, (int) X1 - 4, tb1 + 1, UI.alpha(UI.accent(), 0.35f * ta));
			TabDef cur = s.tabs ? tabs.get(Math.min(activeTab, tabs.size() - 1)) : tabs.get(0);
			boolean allF = cur.kind() == Kind.ALL && allTag != null;
			int dot = cur.kind() == Kind.GLOBAL ? Channels.color('G') : cur.kind() == Kind.LOCAL ? Channels.color('L') : allF ? (allCh != 0 ? Channels.color(allCh) : ON_GREEN) : UI.accent();
			if (allF && chFlash > 0) UI.round(g, (int) X0 + 4, tb0 + 3, (int) X0 + 12, tb0 + 11, 4, UI.alpha(dot, 0.6f * chFlash * ta));
			UI.round(g, (int) X0 + 6, tb0 + 5, (int) X0 + 10, tb0 + 9, 2, UI.alpha(dot, ta));
			String tip = allF ? (allCh != 0 ? net.primlixe.aurorachat.ui.I18n.t("Все · только канал ") + Channels.label(allCh) : net.primlixe.aurorachat.ui.I18n.t("Все · каналы ") + Channels.label('G') + net.primlixe.aurorachat.ui.I18n.t(" и ") + Channels.label('L')) : cur.tip();
			int online = mc.getConnection() == null ? 0 : mc.getConnection().getOnlinePlayers().size();
			String info = (!net.primlixe.aurorachat.Edition.PUBLIC && StaffWatch.onlineCount() > 0 ? "★ " + StaffWatch.onlineCount() + "  ·  " : "") + (AfkResponder.isAfk() ? net.primlixe.aurorachat.ui.I18n.t("☾ АФК  ·  ") : "") + (AntiSpam.hiddenCount() > 0 ? "⊘ " + AntiSpam.hiddenCount() + "  ·  " : "") + "☺ " + online + "  ·  " + net.primlixe.auroralib.text.TimeFmt.now("HH:mm");
			// подпись слева не заезжает на счётчики справа
			g.text(font, UI.trim(font, tip, Math.max(20, (int) X1 - 14 - font.width(info) - 8 - ((int) X0 + 14))), (int) X0 + 14, tb0 + 3, UI.alpha(UI.TEXT, ta), false);
			g.text(font, info, (int) X1 - 14 - font.width(info), tb0 + 3, UI.alpha(UI.SUB, ta), false);
			if (open && !preview && !net.primlixe.aurorachat.Edition.PUBLIC && StaffWatch.onlineCount() > 0) {
				int ix = (int) X1 - 14 - font.width(info), iw = font.width("★ " + StaffWatch.onlineCount());
				if (mx >= ix - 2 && mx < ix + iw + 2 && my >= tb0 && my < tb1) {
					List<FormattedCharSequence> staffTip = new ArrayList<>();
					for (Component c : StaffWatch.onlineLines()) staffTip.add(c.getVisualOrderText());
					g.setTooltipForNextFrame(font, staffTip, mx, my);
				}
			}
			g.pose().popMatrix();
		}

		// ---------- полоса прокрутки
		if (open && s.scrollbar && maxScroll > 1) {
			sbTop = Y0 + (s.titleBar ? 16 : 3);
			sbH = Math.max(10, Y1 - 3 - sbTop);
			float H = Y1 - Y0;
			sbThumb = Math.max(12, sbH * H / (H + maxScroll * scale));
			// smooth на миг может выйти за maxScroll (история укоротилась) — ползунок не вылезает за полосу
			float ty = sbTop + (1 - Math.max(0, Math.min(1, smooth / maxScroll))) * (sbH - sbThumb);
			boolean sh = in(new int[]{(int) X1 - 7, (int) sbTop, (int) X1, (int) (sbTop + sbH)}, mx, my) || drag == 3;
			int sx = (int) X1 - (sh ? 5 : 4);
			UI.round(g, sx, (int) sbTop, (int) X1 - 2, (int) (sbTop + sbH), 1, UI.alpha(0xFFFFFFFF, 0.06f * oc));
			UI.round(g, sx, (int) ty, (int) X1 - 2, (int) (ty + sbThumb), 2, UI.alpha(UI.accent(), (sh ? 0.95f : 0.6f) * oc));
			scrollBtn = new int[]{(int) X1 - 7, (int) sbTop, (int) X1, (int) (sbTop + sbH)};
		}

		if (!open && !preview) return;

		// ---------- верхняя полоса: ⠿ вкладки … ✎ G L ⚙
		float ha = preview ? 1 : oc;
		if (ha < 0.05f) return;
		float hyF = Y0 - 16 + (1 - ha) * 6;
		int hy = (int) Math.floor(hyF);
		int left = (int) Math.floor(X0), right = left + Math.round(X1 - X0);
		g.pose().pushMatrix();
		g.pose().translate(X0 - left, hyF - hy);
		int cx = left;
		if (!s.locked) {
			int[] grip = {cx, hy, cx + 12, hy + 13};
			boolean gh = !preview && in(grip, mx, my);
			UI.round(g, grip[0], grip[1], grip[2], grip[3], 5, UI.alpha(gh || drag == 1 ? UI.accent() : 0xFF141626, (gh ? 0.95f : 0.8f) * ha));
			UI.centered(g, font, "⠿", cx + 6, hy + 3, UI.alpha(0xFFFFFFFF, ha));
			if (!preview) gripBtn = grip;
			cx += 15;
		}
		// справа: настройки и канал
		int rx = right;
		if (s.headerGear) {
			int[] gear = {rx - 13, hy, rx, hy + 13};
			boolean gearH = !preview && in(gear, mx, my);
			UI.round(g, gear[0], gear[1], gear[2], gear[3], 5, UI.alpha(gearH ? UI.accent() : 0xFF141626, 0.85f * ha));
			UI.centered(g, font, "⚙", rx - 6, hy + 3, UI.alpha(0xFFFFFFFF, ha));
			if (!preview) gearBtn = gear;
			rx -= 16;
		}
		if (s.headerCollapse) {
			int[] colB = {rx - 13, hy, rx, hy + 13};
			boolean colH = !preview && in(colB, mx, my);
			UI.round(g, colB[0], colB[1], colB[2], colB[3], 5, UI.alpha(colH ? UI.accent() : 0xFF141626, 0.85f * ha));
			UI.centered(g, font, s.collapsed ? "▴" : "▾", rx - 6, hy + 3, UI.alpha(0xFFFFFFFF, ha));
			if (!preview) collapseBtn = colB;
			rx -= 16;
		}
		if (s.channels && s.headerChannels) {
			int segW = Math.max(13, Math.max(font.width("§l" + Channels.label('G')), font.width("§l" + Channels.label('L'))) + 6);
			int x0 = rx - segW * 2 - 2;
			UI.round(g, x0 - 12, hy, rx, hy + 13, 6, UI.alpha(0xFF141626, 0.85f * ha));
			g.text(font, "✎", x0 - 9, hy + 3, UI.alpha(UI.SUB, ha), false);
			boolean isG = s.channel.equals("G");
			int[] gb = {x0, hy + 1, x0 + segW, hy + 12}, lb = {x0 + segW + 1, hy + 1, x0 + segW * 2 + 1, hy + 12};
			int[] selSeg = isG ? gb : lb;
			int selCol = Channels.color(isG ? 'G' : 'L');
			// вспышка: кольцо расходится от выбранного канала и гаснет
			if (chFlash > 0) {
				int e = Math.round((1 - chFlash) * 5);
				UI.roundOutline(g, selSeg[0] - 1 - e, selSeg[1] - 1 - e, selSeg[2] + 1 + e, selSeg[3] + 1 + e, 6 + e, UI.alpha(selCol, chFlash * ha));
				UI.round(g, selSeg[0] - 1, selSeg[1] - 1, selSeg[2] + 1, selSeg[3] + 1, 6, UI.alpha(0xFFFFFFFF, 0.45f * chFlash * ha));
			}
			UI.round(g, gb[0], gb[1], gb[2], gb[3], 5, UI.alpha(isG ? Channels.color('G') : 0x00000000, 0.95f * ha));
			UI.round(g, lb[0], lb[1], lb[2], lb[3], 5, UI.alpha(!isG ? Channels.color('L') : 0x00000000, 0.95f * ha));
			UI.centered(g, font, "§l" + Channels.label('G'), (gb[0] + gb[2]) / 2 + 1, hy + 3, UI.alpha(isG ? Channels.labelColor(Channels.color('G')) : UI.SUB, ha));
			UI.centered(g, font, "§l" + Channels.label('L'), (lb[0] + lb[2]) / 2 + 1, hy + 3, UI.alpha(!isG ? Channels.labelColor(Channels.color('L')) : UI.SUB, ha));
			// зелёный кружок «включён» в углу выбранного
			UI.round(g, selSeg[2] - 4, selSeg[1] - 2, selSeg[2] + 1, selSeg[1] + 3, 2, UI.alpha(0xFF101010, ha));
			UI.round(g, selSeg[2] - 3, selSeg[1] - 1, selSeg[2], selSeg[1] + 2, 1, UI.alpha(ON_GREEN, ha));
			if (!preview) { gBtn = gb; lBtn = lb; }
			rx = x0 - 15;
		}
		// вкладки
		if (s.tabs) {
			int tx = cx;
			int selX = -1, selW = 0;
			List<int[]> rects = new ArrayList<>();
			// не влезают все — длинные названия сворачиваются в значки (подсказка при наведении остаётся)
			int need = 0;
			// «Все ●G»: место под зелёный кружок и букву канала
			int allExtra = 0;
			if (allTag != null) {
				allExtra = 4 + 5 + 2;
				for (char ch : allTag.toCharArray()) allExtra += font.width("§l" + Channels.label(ch));
			}
			for (int i = 0; i < tabs.size(); i++) need += font.width(tabs.get(i).name()) + 12 + (i == 0 ? allExtra : 0) + (s.unreadCounters && i < UNREAD.length && UNREAD[i] > 0 ? 18 : 0);
			boolean compactTabs = cx + need > rx;
			for (int i = 0; i < tabs.size(); i++) {
				String name = compactTabs ? shortName(tabs.get(i)) : tabs.get(i).name();
				int unread = s.unreadCounters && i < UNREAD.length ? UNREAD[i] : 0;
				int tw = font.width(name) + 10 + (i == 0 ? allExtra : 0) + (unread > 0 ? font.width(unread > 99 ? "99+" : String.valueOf(unread)) + 7 : 0);
				if (tx + tw > rx) break;
				rects.add(new int[]{tx, hy, tx + tw, hy + 13, i});
				if (i == activeTab) { selX = tx; selW = tw; }
				tx += tw + 2;
			}
			if (!rects.isEmpty() && s.tabStyle == ChatConfig.TabStyle.PILLS) {
				int[] lastR = rects.get(rects.size() - 1);
				UI.round(g, cx - 1, hy, lastR[2] + 1, hy + 13, 6, UI.alpha(0xFF141626, 0.85f * ha));
			}
			if (selX >= 0) {
				if (tabX < 0 || !anim) { tabX = selX; tabW = selW; }
				tabX = UI.damp(tabX, selX, 18 * aspd * s.tabSpeed, dt);
				tabW = UI.damp(tabW, selW, 18 * aspd * s.tabSpeed, dt);
				switch (s.tabStyle) {
					case PILLS -> UI.round(g, Math.round(tabX), hy + 1, Math.round(tabX + tabW), hy + 12, 5, UI.alpha(UI.accent(), 0.9f * ha));
					case UNDERLINE -> UI.round(g, Math.round(tabX) + 3, hy + 11, Math.round(tabX + tabW) - 3, hy + 13, 1, UI.alpha(UI.accent(), ha));
					case TEXT -> { }
				}
			}
			for (int[] t : rects) {
				int i = t[4];
				String name = compactTabs ? shortName(tabs.get(i)) : tabs.get(i).name();
				if (compactTabs && in(t, mx, my) && !preview) g.setTooltipForNextFrame(font, Component.literal(tabs.get(i).name()), mx, my);
				boolean sel = i == activeTab, hov = !preview && in(t, mx, my);
				if (hov && !sel) UI.round(g, t[0], t[1] + 1, t[2], t[3] - 1, 5, UI.alpha(0x30FFFFFF, ha));
				int col = tabs.get(i).kind() == Kind.GLOBAL ? Channels.color('G') : tabs.get(i).kind() == Kind.LOCAL ? Channels.color('L') : UI.SUB;
				int selCol = s.tabStyle == ChatConfig.TabStyle.PILLS ? 0xFFFFFFFF : UI.accent();
				if (i == 0 && allTag != null && chFlash > 0) UI.round(g, t[0], t[1], t[2], t[3], 5, UI.alpha(allCh != 0 ? Channels.color(allCh) : ON_GREEN, 0.55f * chFlash * ha)); // вспышка
				g.text(font, name, t[0] + 5, hy + 3, UI.alpha(sel ? selCol : col, ha), s.tabStyle != ChatConfig.TabStyle.PILLS);
				if (i == 0 && allTag != null) {
					// зелёный кружок «фильтр включён» + буква канала его цветом
					int dx = t[0] + 5 + font.width(name) + 4, dy = hy + 4;
					if (chFlash > 0) { int e = Math.round((1 - chFlash) * 3); UI.round(g, dx - 1 - e, dy - 1 - e, dx + 6 + e, dy + 6 + e, 4 + e, UI.alpha(ON_GREEN, 0.5f * chFlash * ha)); }
					UI.round(g, dx - 1, dy - 1, dx + 6, dy + 6, 3, UI.alpha(0xFF0B0C10, 0.9f * ha));
					UI.round(g, dx, dy, dx + 5, dy + 5, 2, UI.alpha(ON_GREEN, ha));
					// буквы каналов — каждая своим цветом («GL» в режиме «оба»)
					int lx = dx + 7;
					for (char ch : allTag.toCharArray()) {
						String lb = "§l" + Channels.label(ch);
						g.text(font, lb, lx, hy + 3, UI.alpha(Channels.color(ch), ha), true);
						lx += font.width(lb);
					}
				}
				int unread = s.unreadCounters && i < UNREAD.length ? UNREAD[i] : 0;
				if (unread > 0) {
					String u = unread > 99 ? "99+" : String.valueOf(unread);
					int bx = t[0] + 5 + font.width(name) + 2;
					UI.round(g, bx, hy + 2, bx + font.width(u) + 4, hy + 11, 4, UI.alpha(0xFFFF5470, ha));
					g.text(font, u, bx + 2, hy + 3, UI.alpha(0xFFFFFFFF, ha), false);
				}
				if (!preview) TAB_RECTS.add(t);
				if (hov) g.setTooltipForNextFrame(font, Component.literal(i == 0 && allTag != null
						? (allCh != 0 ? net.primlixe.aurorachat.ui.I18n.t("Сейчас: канал ") + allCh : net.primlixe.aurorachat.ui.I18n.t("Сейчас: оба канала G и L")) + net.primlixe.aurorachat.ui.I18n.t(" (+ система и ЛС) · клик — ")
								+ (allCh == 'L' ? "G" : allCh == 'G' ? net.primlixe.aurorachat.ui.I18n.t("G и L сразу") : net.primlixe.aurorachat.ui.I18n.t("только L")) + net.primlixe.aurorachat.ui.I18n.t(" · пишешь в ") + s.channel
						: tabs.get(i).tip()), mx, my);
			}
		}
		g.pose().popMatrix();
		if (preview) return;

		// уголок изменения размера (справа сверху окна)
		if (!s.locked) {
			int[] rz = {right - 9, (int) Y0, right, (int) Y0 + 9};
			boolean rh = in(rz, mx, my) || drag == 2;
			int c = UI.alpha(rh ? UI.accent() : 0xFFFFFFFF, rh ? 0.95f : 0.35f);
			for (int i = 0; i < 3; i++) g.fill(right - 3 - i * 3, (int) Y0 + 2, right - 2 - i * 3, (int) Y0 + 3, c);
			for (int i = 0; i < 3; i++) g.fill(right - 3, (int) Y0 + 2 + i * 3, right - 2, (int) Y0 + 3 + i * 3, c);
			resizeBtn = rz;
		}

		// «↓ новые»
		if (s.newMessagesHint && smooth > 4 && newSince > 0) {
			String t = "↓ " + newSince + net.primlixe.aurorachat.ui.I18n.t(" нов.");
			int bw = font.width(t) + 10, bx = right - bw - 4, by = (int) Y1 - 16;
			UI.round(g, bx, by, bx + bw, by + 13, 6, UI.alpha(UI.accent(), 0.95f));
			g.text(font, t, bx + 5, by + 3, 0xFFFFFFFF, false);
			newBtn = new int[]{bx, by, bx + bw, by + 13};
		} else if (smooth < 1) newSince = 0;

		// курсор: стрелки на краях, «перенос» на ручке
		int hoverEdge = drag == 2 ? edges : (drag == 0 ? edgeAt(mx, my) : 0);
		if (hoverEdge != 0) g.requestCursor(cursorFor(hoverEdge));
		else if (drag == 1 || (gripBtn != null && in(gripBtn, mx, my))) g.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL);
		else if (drag == 3 || in(scrollBtn, mx, my) || in(gearBtn, mx, my) || in(collapseBtn, mx, my) || in(gBtn, mx, my) || in(lBtn, mx, my))
			g.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
		// подсветка края, за который тянем
		if (hoverEdge != 0) {
			int ec = UI.alpha(UI.accent(), 0.9f);
			if ((hoverEdge & 1) != 0) g.fill(right - (right - left) - 1, (int) Y0, left + 1, (int) Y1, ec);
			if ((hoverEdge & 2) != 0) g.fill(right - 1, (int) Y0, right + 1, (int) Y1, ec);
			if ((hoverEdge & 4) != 0) g.fill(left, (int) Y0 - 1, right, (int) Y0 + 1, ec);
			if ((hoverEdge & 8) != 0) g.fill(left, (int) Y1 - 1, right, (int) Y1 + 1, ec);
		}

		// подсказки
		if (in(collapseBtn, mx, my)) g.setTooltipForNextFrame(font, Component.literal(s.collapsed ? net.primlixe.aurorachat.ui.I18n.t("Развернуть чат") : net.primlixe.aurorachat.ui.I18n.t("Свернуть чат (двойной клик по заголовку тоже)")), mx, my);
		else if (gripBtn != null && in(gripBtn, mx, my) && drag == 0) g.setTooltipForNextFrame(font, Component.literal(net.primlixe.aurorachat.ui.I18n.t("Тяни, чтобы переместить чат · двойной клик — на место")), mx, my);
		else if (resizeBtn != null && in(resizeBtn, mx, my) && drag == 0) g.setTooltipForNextFrame(font, Component.literal(net.primlixe.aurorachat.ui.I18n.t("Тяни, чтобы изменить размер")), mx, my);
		else if (gearBtn != null && in(gearBtn, mx, my)) g.setTooltipForNextFrame(font, Component.literal(net.primlixe.aurorachat.ui.I18n.t("Настройки чата")), mx, my);
		else if ((gBtn != null && in(gBtn, mx, my)) || (lBtn != null && in(lBtn, mx, my))) {
			g.setTooltipForNextFrame(font, Component.literal(net.primlixe.aurorachat.ui.I18n.t("Куда писать: G — глобальный (префикс «") + s.globalPrefix + net.primlixe.aurorachat.ui.I18n.t("»), L — локальный · Alt+G / Alt+L")), mx, my);
		} else if (hovered != null) {
			Style st = styleAt(font, hovered, mx, my);
			if (st != null && s.linkPreview && st.getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI uri)) {
				// ссылка: домен, адрес, предупреждения (+ подсказка сервера, если она была)
				List<Component> tip = new ArrayList<>(LinkInfo.describe(uri.toString()));
				if (st.getHoverEvent() instanceof HoverEvent.ShowText(Component value)) tip.add(0, value);
				g.setComponentTooltipForNextFrame(font, tip, mx, my);
			} else if (st != null && st.getHoverEvent() instanceof HoverEvent.ShowText(Component value)) {
				g.setTooltipForNextFrame(font, value, mx, my);
			} else if (hovered.meta().time() > 0 && (mx - X0) / scale > hovered.x1() - 40) {
				String full = net.primlixe.auroralib.text.TimeFmt.format(hovered.meta().time(), "dd.MM.yyyy HH:mm:ss");
				String ch = hovered.meta().channel() != 0 ? Channels.name(hovered.meta().channel()) + " · " : "";
				g.setTooltipForNextFrame(font, Component.literal(ch + hovered.meta().cat().ru + " · " + full), mx, my);
			}
		}
	}

	// ====================================================================== раскладки строк
	// Все функции рисуют в локальных координатах строки (0,0 — её левый верхний угол) и
	// возвращают {x, y} начала текста — для кликов по ссылкам и подсказок.

	private static boolean isPlayer(Meta m) {
		return m.sender() != null && (m.cat() == Category.PLAYER || m.cat() == Category.MENTION);
	}

	private static boolean sameGroup(Meta newer, Meta older) {
		return isPlayer(newer) && isPlayer(older) && newer.sender().equalsIgnoreCase(older.sender())
				&& newer.time() > 0 && older.time() > 0 && newer.time() - older.time() < 300_000 && newer.channel() == older.channel();
	}

	/** Текст без «<Ник> » / «Ник: » в начале — ник рисуется отдельно. */
	static Component stripSender(Component c, String sender) {
		String plain = c.getString();
		String key = sender;
		int i = plain.toLowerCase(Locale.ROOT).indexOf(sender.toLowerCase(Locale.ROOT));
		if (i < 0) { key = Nicknames.display(sender); i = plain.toLowerCase(Locale.ROOT).indexOf(key.toLowerCase(Locale.ROOT)); } // ник заменён своим именем
		if (i < 0 || i > 48) return c;
		int e = i + key.length();
		while (e < plain.length() && " >:»|]-".indexOf(plain.charAt(e)) >= 0) e++;
		if (e >= plain.length()) return c;
		return ChatFormat.dropChars(c, e);
	}

	/** Подпись времени в пузыре мессенджера: «×3 12:30». */
	private static String stamp(String time, int count) { return (count > 1 ? "×" + count + " " : "") + time; }

	private static float stampW(Font font, String time, int count) {
		String st = stamp(time, count);
		return st.isEmpty() ? 0 : font.width(st);
	}

	private static void face(GuiGraphicsExtractor g, Font font, ChatConfig s, Meta m, int x, int y, int size, float alpha, Minecraft mc) {
		PlayerInfo pi = m.sender() == null || mc.getConnection() == null ? null : mc.getConnection().getPlayerInfoIgnoreCase(m.sender());
		if (pi != null && m.cat() != Category.SYSTEM && m.cat() != Category.CLIENT && m.cat() != Category.SESSION) {
			PlayerFaceExtractor.extractRenderState(g, net.primlixe.auroralib.compat.Skins.of(pi), x, y, size, UI.alpha(0xFFFFFFFF, alpha));
		} else if (s.icons) {
			String icon = switch (m.cat()) {
				case DM -> "✉";
				case MENTION -> "@";
				case JOIN -> "+";
				case LEAVE -> "−";
				case PLAYER -> "☺";
				default -> "✦";
			};
			g.text(font, icon, x + (size - font.width(icon) + 1) / 2, y + (size - 7) / 2, UI.alpha(m.cat().color, alpha * 0.9f), false);
		}
	}

	private static int channelBadge(GuiGraphicsExtractor g, Font font, ChatConfig s, Meta m, int x, int y, float alpha) {
		if (!s.channels || !s.channelBadges || m.channel() == 0) return 0;
		int bc = Channels.color(m.channel());
		switch (s.badgeStyle) {
			case TAG -> {
				String t = "§l[" + Channels.label(m.channel()) + "]";
				g.text(font, t, x, y, UI.alpha(bc, alpha), true);
				return font.width(t) + 3;
			}
			case LETTER -> {
				// яркая метка с тёмной обводкой и жирной надписью — видна на любом фоне; форма и надпись — из настроек
				String t = "§l" + Channels.label(m.channel());
				int bw = pillW(font, m.channel());
				int r = switch (s.badgeShape) { case SQUARE -> 0; case CIRCLE -> 5; default -> 3; };
				UI.round(g, x - 1, y - 2, x + bw + 1, y + 9, r + 1, UI.alpha(0xFF0B0C10, 0.85f * alpha));
				if (s.badgeShape == ChatConfig.BadgeShape.OUTLINE) {
					UI.roundOutline(g, x, y - 1, x + bw, y + 8, 3, UI.alpha(bc, alpha));
					g.text(font, t, x + (bw - font.width(t)) / 2 + 1, y, UI.alpha(bc, alpha), false);
				} else {
					UI.round(g, x, y - 1, x + bw, y + 8, r, UI.alpha(bc, alpha));
					g.text(font, t, x + (bw - font.width(t)) / 2 + 1, y, UI.alpha(Channels.labelColor(bc), alpha), false);
				}
				return bw + 3;
			}
			default -> { return 0; }
		}
	}

	private static Component name(String sender, int color) {
		ChatConfig s = ChatCore.cfg();
		return Component.literal(s.nameCaps ? sender.toUpperCase(Locale.ROOT) : sender)
				.withStyle(Style.EMPTY.withColor(color & 0xFFFFFF).withBold(true).withItalic(s.nameItalic).withUnderlined(s.nameUnderline));
	}

	private static int[] drawCompact(GuiGraphicsExtractor g, Font font, ChatConfig s, Plan p, Meta m, int W, int H, float alpha, int ta, int timeCol,
	                                 int bgRgb, float bgA, boolean hover, int lineH, int pad, int headW, boolean timeLeft, boolean bubbles, int cat, int index, Minecraft mc) {
		if (bubbles) {
			int bg = switch (m.cat()) {
				case DM -> UI.mix(bgRgb, 0xFF6A2A70, 0.35f);
				case MENTION -> UI.mix(bgRgb, 0xFF6E5A1A, 0.35f);
				default -> UI.mix(bgRgb, 0xFFFFFFFF, 0.04f);
			};
			UI.round(g, 0, 0, W, H, s.bubbleRadius, UI.alpha(bg, Math.min(1, bgA * 1.1f + (hover && s.hoverHighlight ? 0.12f : 0)) * alpha));
		} else {
			if (s.zebra && index % 2 == 1) g.fill(0, 0, W, H, UI.alpha(0xFFFFFFFF, 0.035f * alpha));
			if (hover && s.hoverHighlight) UI.round(g, 0, 0, W, H, 3, UI.alpha(0xFFFFFFFF, 0.07f));
			if (s.separators) g.fill(4, H, W - 4, H + 1, UI.alpha(0xFFFFFFFF, 0.06f * alpha));
		}
		if (m.cat() == Category.MENTION && s.highlightSelf) UI.round(g, 0, 0, W, H, 4, UI.alpha(0xFF000000 | s.mentionColor, 0.09f * alpha));
		if (s.stripes) UI.round(g, 0, 1, s.stripeWidth, H - 1, 1, UI.alpha(cat, alpha * 0.9f));
		int tx = pad + 3, ty = pad;
		if (timeLeft && !p.time().isEmpty()) {
			drawTime(g, font, p.time(), tx, ty, UI.alpha(timeCol, alpha));
			tx += (int) p.timeW();
		}
		if (s.heads) {
			face(g, font, s, m, tx, ty, s.headSize, alpha, mc);
			tx += headW;
		}
		tx += channelBadge(g, font, s, m, tx, ty, alpha);
		tx += 1;
		for (int i = 0; i < p.lines().size(); i++) g.text(font, p.lines().get(i), tx, ty + i * lineH, ta, s.textShadow);
		int rx = W - pad - 2;
		if (p.countW() > 0) {
			String cs = "×" + m.count();
			int cw = font.width(cs) + 6;
			UI.round(g, rx - cw, ty, rx, ty + 9, 4, UI.alpha(UI.accent(), 0.75f * alpha));
			g.text(font, cs, rx - cw + 3, ty + 1, UI.alpha(0xFFFFFFFF, alpha), false);
			rx -= cw + 3;
		}
		if (!timeLeft && !p.time().isEmpty()) drawTime(g, font, p.time(), rx - timeWidth(font, p.time()), ty, UI.alpha(timeCol, alpha));
		return new int[]{tx, ty};
	}

	/** «Как в Discord»: аватар 16px, жирный ник и время над текстом, сообщения подряд — без повтора шапки. */
	private static int[] drawCozy(GuiGraphicsExtractor g, Font font, ChatConfig s, Plan p, Meta m, int W, int H, float alpha, int ta, int timeCol,
	                              boolean hover, int lineH, int pad, int cat, int index, Minecraft mc) {
		if (hover && s.hoverHighlight) g.fill(0, 0, W, H, UI.alpha(0xFFFFFFFF, 0.05f));
		if (m.cat() == Category.MENTION && s.highlightSelf) {
			g.fill(0, 0, W, H, UI.alpha(0xFF000000 | s.mentionColor, 0.10f * alpha));
			g.fill(0, 0, 2, H, UI.alpha(0xFF000000 | s.mentionColor, alpha));
		} else if (s.stripes && !p.player()) {
			UI.round(g, 0, 1, s.stripeWidth, H - 1, 1, UI.alpha(cat, alpha * 0.9f));
		}
		int col = s.heads ? 20 : 4;
		int tx = pad + 2 + col, ty = pad;
		if (p.player()) {
			if (!p.groupOlder()) {
				if (s.heads) face(g, font, s, m, pad + 2, pad + 1, 16, alpha, mc);
				int nx = tx;
				nx += channelBadge(g, font, s, m, nx, ty, alpha);
				int nc = !s.highlightSelf && p.self() ? UI.TEXT : s.colorNames ? 0xFF000000 | ChatFormat.nameColor(m.sender()) : UI.accent();
				g.text(font, name(Nicknames.display(m.sender()), nc), nx, ty, UI.alpha(nc, alpha), s.textShadow);
				nx += font.width(name(Nicknames.display(m.sender()), nc)) + 5;
				if (!p.time().isEmpty()) drawTime(g, font, p.time(), nx, ty, UI.alpha(timeCol, alpha * 0.9f));
				ty += lineH;
			} else if (hover && !p.time().isEmpty()) {
				// у сообщений группы время видно при наведении — слева, как в Discord
				g.pose().pushMatrix();
				g.pose().translate(pad + 1, ty + 1.5f);
				g.pose().scale(0.7f, 0.7f);
				g.text(font, p.time(), 0, 0, UI.alpha(timeCol, alpha), false);
				g.pose().popMatrix();
			}
		} else {
			if (s.heads) face(g, font, s, m, pad + 2 + (16 - s.headSize) / 2, pad, s.headSize, alpha, mc);
			if (!p.time().isEmpty()) drawTime(g, font, p.time(), W - pad - 2 - timeWidth(font, p.time()), ty, UI.alpha(timeCol, alpha));
		}
		for (int i = 0; i < p.lines().size(); i++) g.text(font, p.lines().get(i), tx, ty + i * lineH, ta, s.textShadow);
		if (p.countW() > 0) {
			String cs = "×" + m.count();
			int cw = font.width(cs) + 6, rx = W - pad - 2;
			UI.round(g, rx - cw, pad, rx, pad + 9, 4, UI.alpha(UI.accent(), 0.75f * alpha));
			g.text(font, cs, rx - cw + 3, pad + 1, UI.alpha(0xFFFFFFFF, alpha), false);
		}
		return new int[]{tx, ty};
	}

	/** «Мессенджер»: пузыри, свои справа цветом акцента, чужие слева с аватаром, системные — по центру. */
	private static int[] drawMessenger(GuiGraphicsExtractor g, Font font, ChatConfig s, Plan p, Meta m, int W, int H, float alpha, int ta, int timeCol,
	                                   int bgRgb, float bgA, boolean hover, int lineH, int cat, Minecraft mc) {
		float a = Math.min(1, Math.max(bgA, 0.35f) * 1.15f + (hover && s.hoverHighlight ? 0.1f : 0)) * alpha;
		int rad = Math.max(4, s.bubbleRadius + 2);
		if (!p.player()) {
			UI.round(g, 0, 0, W, H, Math.min(rad, H / 2), UI.alpha(UI.mix(bgRgb, 0xFFFFFFFF, 0.10f), a * 0.85f));
			for (int i = 0; i < p.lines().size(); i++) {
				int lw = font.width(p.lines().get(i));
				g.text(font, p.lines().get(i), (W - lw) / 2, 3 + i * lineH, UI.alpha(UI.mix(ta, 0xFFB8BCCC, 0.4f), alpha), s.textShadow);
			}
			return new int[]{(W - p.maxLine()) / 2, 3};
		}
		int bubble = p.self() ? UI.mix(bgRgb, UI.accent(), 0.55f) : UI.mix(bgRgb, 0xFFFFFFFF, 0.09f);
		if (m.cat() == Category.MENTION && s.highlightSelf) bubble = UI.mix(bubble, 0xFF000000 | s.mentionColor, 0.25f);
		UI.round(g, 0, 0, W, H, rad, UI.alpha(bubble, a));
		// «хвостик» у последнего сообщения группы
		if (!p.groupNewer()) {
			if (p.self()) { g.fill(W - 3, H - 4, W + 1, H, UI.alpha(bubble, a)); g.fill(W + 1, H - 2, W + 3, H, UI.alpha(bubble, a)); }
			else { g.fill(-1, H - 4, 3, H, UI.alpha(bubble, a)); g.fill(-3, H - 2, -1, H, UI.alpha(bubble, a)); }
		}
		if (!p.self() && s.heads && !p.groupNewer()) face(g, font, s, m, -12 - 2, H - 10, 10, alpha, mc);
		int tx = 5, ty = 4;
		if (!p.self() && !p.groupOlder()) {
			int nx = tx;
			nx += channelBadge(g, font, s, m, nx, ty, alpha);
			int nc = !s.highlightSelf && p.self() ? UI.TEXT : s.colorNames ? 0xFF000000 | ChatFormat.nameColor(m.sender()) : UI.accent();
			g.text(font, name(Nicknames.display(m.sender()), nc), nx, ty, UI.alpha(nc, alpha), false);
			ty += lineH;
		}
		for (int i = 0; i < p.lines().size(); i++) g.text(font, p.lines().get(i), tx, ty + i * lineH, ta, s.textShadow);
		String st = stamp(p.time(), m.count());
		if (!st.isEmpty()) {
			int sw = font.width(st);
			boolean inline = p.lastW() + sw + 4 <= p.textW();
			int sy = inline ? ty + (p.lines().size() - 1) * lineH : ty + p.lines().size() * lineH;
			int sc = p.self() ? 0xC8FFFFFF : timeCol;
			g.text(font, st, W - 5 - sw, sy, UI.alpha(sc, alpha * 0.9f), false);
		}
		return new int[]{tx, ty};
	}

	/** Кнопка «↩ ответить» в правом верхнем углу строки под курсором. */
	private static void drawReply(GuiGraphicsExtractor g, Font font, Row row, float X0, float Y0, Minecraft mc) {
		Meta m = row.meta();
		String self = mc.player != null ? mc.player.getName().getString() : "";
		boolean dm = m.cat() == Category.DM;
		String to = m.sender();
		if (to.equalsIgnoreCase(self)) {
			if (!dm) return;
			// своё исходящее ЛС: отвечаем собеседнику, а не себе
			to = ChatFormat.dmPeer(row.msg().content());
			if (to == null || to.equalsIgnoreCase(self)) return;
		}
		int bx1 = Math.round(X0 + row.x1() * scale) - 3, by0 = Math.round(Y0 + row.y0() * scale) + 1;
		String label = dm ? net.primlixe.aurorachat.ui.I18n.t("↩ ЛС") : "↩";
		int bw = font.width(label) + 8;
		int[] r = {bx1 - bw, by0, bx1, by0 + 11};
		double mx = mc.mouseHandler.getScaledXPos(mc.getWindow()), my = mc.mouseHandler.getScaledYPos(mc.getWindow());
		boolean h = in(r, mx, my);
		UI.round(g, r[0], r[1], r[2], r[3], 5, h ? UI.alpha(UI.accent(), 0.95f) : 0xE0202336);
		g.text(font, label, r[0] + 4, r[1] + 2, 0xFFFFFFFF, false);
		replyBtn = r;
		replyText = dm ? "/" + ChatMenu.firstDm() + " " + to + " " : to + ", ";
		if (h) g.setTooltipForNextFrame(font, Component.literal(dm ? net.primlixe.aurorachat.ui.I18n.t("Ответить в личку ") + to : net.primlixe.aurorachat.ui.I18n.t("Ответить ") + to + net.primlixe.aurorachat.ui.I18n.t(" (упомянуть)")), (int) mx, (int) my);
	}

	/** Полоска закреплённых сообщений под заголовком окна: клик — следующее, ✕ — открепить. */
	private static void drawPins(GuiGraphicsExtractor g, Font font, ChatConfig s, float X0, float y, float X1, float a, int bgRgb, float bgA, int mx, int my) {
		List<ChatConfig.Pin> pins = s.pinned;
		if (pins.isEmpty()) return;
		if (pinIndex >= pins.size()) pinIndex = 0;
		ChatConfig.Pin p = pins.get(pinIndex);
		String text = PIN_TEXT.computeIfAbsent(p.json, j -> ChatText.fromJson(j).getString());
		int x0 = (int) X0 + 1, x1 = (int) X1 - 1, y0 = (int) y + 1, y1 = (int) y + 13;
		UI.round(g, x0, y0, x1, y1, 3, UI.alpha(UI.mix(bgRgb, UI.accent(), 0.18f), Math.min(1, bgA + 0.2f) * a));
		g.fill(x0 + 2, y0 + 2, x0 + 4, y1 - 2, UI.alpha(UI.accent(), a));
		String head = "📌" + (pins.size() > 1 ? " " + (pinIndex + 1) + "/" + pins.size() : "");
		g.text(font, head, x0 + 7, y0 + 2, UI.alpha(UI.accent(), a), false);
		int tx = x0 + 11 + font.width(head);
		g.text(font, UI.trim(font, text, x1 - tx - 16), tx, y0 + 2, UI.alpha(UI.TEXT, a), false);
		int[] close = {x1 - 12, y0, x1, y1};
		boolean ch = in(close, mx, my);
		g.text(font, "✕", x1 - 9, y0 + 2, UI.alpha(ch ? UI.BAD : UI.SUB, a), false);
		pinClose = close;
		pinBtn = new int[]{x0, y0, x1 - 12, y1};
		if (in(pinBtn, mx, my)) {
			String when = net.primlixe.auroralib.text.TimeFmt.format(p.time, "dd.MM HH:mm");
			g.setComponentTooltipForNextFrame(font, List.of(Component.literal(text), Component.literal("§7" + when + (pins.size() > 1 ? net.primlixe.aurorachat.ui.I18n.t(" · клик — следующее") : ""))), mx, my);
		}
	}

	// ---------- время: размер и жирность из настроек
	static Component timeComp(String t) {
		return ChatCore.cfg().timeBold ? Component.literal(t).withStyle(Style.EMPTY.withBold(true)) : Component.literal(t);
	}

	static int timeWidth(Font f, String t) {
		return t.isEmpty() ? 0 : Math.round(f.width(timeComp(t)) * ChatCore.cfg().timeScale);
	}

	static void drawTime(GuiGraphicsExtractor g, Font f, String t, int x, int y, int color) {
		float sc = ChatCore.cfg().timeScale;
		g.pose().pushMatrix();
		g.pose().translate(x, y + (8 - 8 * sc) / 2f);
		g.pose().scale(sc, sc);
		g.text(f, timeComp(t), 0, 0, color, false);
		g.pose().popMatrix();
	}

	/** Кривая сглаживания из настроек «Плавность». */	static float ease(float t) {
		t = Math.max(0, Math.min(1, t));
		return switch (ChatCore.cfg().ease) {
			case SMOOTH -> 1 - (float) Math.pow(1 - t, 3);
			case SOFT -> t * t * (3 - 2 * t);
			case LINEAR -> t;
			case SPRING -> {
				float c1 = 1.70158f, c3 = c1 + 1, u = t - 1;
				yield 1 + c3 * u * u * u + c1 * u * u;
			}
		};
	}

	private static float badgeWidth(Font font, ChatConfig s, Meta m) {
		if (!s.channels || !s.channelBadges || m.channel() == 0) return 0;
		return switch (s.badgeStyle) {
			case LETTER -> pillW(font, m.channel()) + 3;
			case TAG -> font.width("§l[" + Channels.label(m.channel()) + "]") + 3;
			case BAR -> 0;
		};
	}

	/** Ширина метки канала под её надпись (не уже 11 px). */
	private static int pillW(Font font, char ch) { return Math.max(11, font.width("§l" + Channels.label(ch)) + 5); }

	private static boolean in(int[] r, double x, double y) { return r != null && x >= r[0] && x < r[2] && y >= r[1] && y < r[3]; }

	// ====================================================================== перетаскивание

	public static void endDrag() {
		if (drag == 0) return;
		drag = 0;
		ChatConfig.save();
	}

	/** Вызывается из mouseDragged открытого чата. */
	public static void dragTo(int guiW, int guiH, double mx, double my) { updateDrag(guiW, guiH, (int) Math.round(mx), (int) Math.round(my)); }

	private static void updateDrag(int guiW, int guiH, int mx, int my) {
		if (drag == 0) return;
		ChatConfig c = ChatCore.cfg();
		if (drag == 3) {
			float f = (my - dragA - sbTop) / Math.max(1, sbH - sbThumb);
			scrollPx = Math.max(0, Math.min(maxScroll, (1 - f) * maxScroll));
			return;
		}
		if (drag == 1) {
			float left = mx - dragA, bottom = my + dragB;
			float W = Math.min(c.winW, guiW - 4);
			if (left < 8) left = left < -2 ? 0 : 4;                    // прилипание к краям
			if (left + W > guiW - 8) left = guiW - W - 4;
			int gap = Math.round(guiH - bottom);
			if (Math.abs(gap - 40) < 6) gap = 40;                      // привычное место над хотбаром
			if (gap < 8) gap = 4;
			c.winLeft = Math.max(0, Math.round(left));
			c.winBottom = Math.max(0, Math.min(guiH - 30, gap));
		} else {
			// растягивание за любой край/угол
			float dx = mx - startMx, dy = my - startMy;
			float W = startW, H = startH, L = startL, B = startBottom;
			if ((edges & 2) != 0) W = startW + dx;
			if ((edges & 1) != 0) { W = startW - dx; L = startL + dx; }
			if ((edges & 4) != 0) H = startH - dy;
			if ((edges & 8) != 0) { H = startH + dy; B = startBottom - dy; }
			if (W < 100) { if ((edges & 1) != 0) L -= 100 - W; W = 100; }
			if (L < 0) { W += L; L = 0; }
			W = Math.min(W, guiW - L);
			if (H < 30) { if ((edges & 8) != 0) B -= 30 - H; H = 30; }
			if (B < 0) { H += B; B = 0; }
			H = Math.min(H, guiH - B - 18);
			c.winW = Math.round(W);
			c.winH = Math.round(H);
			c.winLeft = Math.round(L);
			c.winBottom = Math.round(B);
			c.collapsed = false;
		}
	}

	private static void startResize(int e, double mx, double my) {
		ChatConfig c = ChatCore.cfg();
		drag = 2;
		edges = e;
		startW = c.winW; startH = c.winH; startL = c.winLeft; startBottom = c.winBottom;
		startMx = (float) mx; startMy = (float) my;
	}

	public static boolean dragging() { return drag != 0; }

	// ====================================================================== ввод

	private static Row rowAt(double mx, double my) {
		if (mx < wx0 || mx >= wx1 || my < wy0 || my > wy1) return null;
		double lx = (mx - wx0) / scale, ly = (my - wy0) / scale;
		for (Row r : ROWS) if (lx >= r.x0() && lx < r.x1() && ly >= r.y0() && ly < r.y1()) return r;
		return null;
	}

	public static GuiMessage messageAt(double mx, double my) {
		Row r = rowAt(mx, my);
		return r == null ? null : r.msg();
	}

	static Style styleAt(Font font, Row r, double mx, double my) {
		ChatConfig s = ChatCore.cfg();
		double lx = (mx - wx0) / scale, ly = (my - wy0) / scale;
		int li = (int) ((ly - r.textY()) / (9 + s.lineSpacing));
		if (ly < r.textY() || li < 0 || li >= r.lines().size()) return null;
		// «Мессенджер»: системные строки стоят по центру пузыря, каждая со своим отступом
		float lineX = s.layout == ChatConfig.Layout.MESSENGER && !isPlayer(r.meta())
				? r.x0() + (r.x1() - r.x0() - font.width(r.lines().get(li))) / 2f : r.textX();
		float target = (float) (lx - lineX);
		if (target < 0) return null;
		float[] acc = {0};
		Style[] found = {null};
		r.lines().get(li).accept((idx, style, cp) -> {
			float cw = font.width(FormattedCharSequence.forward(new String(Character.toChars(cp)), style));
			if (target >= acc[0] && target < acc[0] + cw) { found[0] = style; return false; }
			acc[0] += cw;
			return true;
		});
		return found[0];
	}

	/** Клик в открытом чате. true — обработан. */
	public static boolean click(double mx, double my, int button, java.util.function.Predicate<Style> styleClick) {
		ChatConfig c = ChatCore.cfg();
		if (button == 0 && in(gripBtn, mx, my)) {
			long now = System.currentTimeMillis();
			if (now - lastGripClick < 350) {
				c.resetWindow();
				ChatConfig.save();
				drag = 0;
			} else {
				drag = 1;
				dragA = (float) (mx - wx0);
				dragB = (float) (wy1 - my);
			}
			lastGripClick = now;
			return true;
		}
		if (button == 0 && in(resizeBtn, mx, my)) { startResize(2 | 4, mx, my); return true; }
		if (in(collapseBtn, mx, my)) { c.collapsed = !c.collapsed; ChatConfig.save(); return true; }
		int e = button == 0 ? edgeAt(mx, my) : 0;
		if (e != 0) { startResize(e, mx, my); return true; }
		if (button == 0 && in(titleRect, mx, my)) {
			long now = System.currentTimeMillis();
			if (now - lastTitleClick < 350) { c.collapsed = !c.collapsed; ChatConfig.save(); lastTitleClick = 0; return true; }
			lastTitleClick = now;
		}
		if (button == 0 && in(scrollBtn, mx, my)) {
			float ty = sbTop + (1 - smooth / Math.max(1, maxScroll)) * (sbH - sbThumb);
			drag = 3;
			dragA = my >= ty && my < ty + sbThumb ? (float) (my - ty) : sbThumb / 2;
			updateDrag(0, 0, (int) mx, (int) my);
			return true;
		}
		if (in(gearBtn, mx, my)) { openSettings.run(); return true; }
		if (in(replyBtn, mx, my) && replyText != null) { inputSetter.accept(replyText); return true; }
		if (in(pinClose, mx, my)) { ChatCore.unpin(pinIndex); pinIndex = 0; return true; }
		if (in(pinBtn, mx, my)) { pinIndex = (pinIndex + 1) % Math.max(1, c.pinned.size()); return true; }
		if (in(gBtn, mx, my)) { c.channel = "G"; ChatConfig.save(); return true; }
		if (in(lBtn, mx, my)) { c.channel = "L"; ChatConfig.save(); return true; }
		for (int[] t : TAB_RECTS) {
			if (in(t, mx, my)) {
				// «Все ●G» уже открыта — клик переключает канал G ↔ L (и что показывает «Все»)
				if (t[4] == 0 && activeTab == 0 && (followChannel() != 0 || allBoth())) {
					cycleAll();
					return true;
				}
				activeTab = t[4];
				if (activeTab < UNREAD.length) UNREAD[activeTab] = 0;
				scrollPx = 0;
				return true;
			}
		}
		if (in(newBtn, mx, my)) { resetScroll(); return true; }
		if (button == 0) {
			Row r = rowAt(mx, my);
			if (r != null) {
				Style st = styleAt(Minecraft.getInstance().font, r, mx, my);
				if (st != null && (st.getClickEvent() != null || st.getInsertion() != null)) return styleClick.test(st);
			}
		}
		return false;
	}

	/** Курсор над кнопками шапки окна (G/L, вкладки, ⚙, ⠿…) — они важнее доп. окон, лежащих сверху. */
	public static boolean overControls(double mx, double my) {
		if (in(gBtn, mx, my) || in(lBtn, mx, my) || in(gearBtn, mx, my) || in(collapseBtn, mx, my) || in(gripBtn, mx, my) || in(resizeBtn, mx, my)) return true;
		for (int[] t : TAB_RECTS) if (in(t, mx, my)) return true;
		return false;
	}

	/** Курсор над окном чата (для прокрутки колесом только над чатом). */
	public static boolean over(double mx, double my) { return mx >= wx0 && mx < wx1 && my >= wy0 - 16 && my <= wy1; }

	private ChatHud() {}
}
