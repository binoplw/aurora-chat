package net.primlixe.aurorachat.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.primlixe.aurorachat.chat.ChatHud;
import net.primlixe.aurorachat.chat.ChatLog;
import net.primlixe.aurorachat.chat.ChatLogScreen;
import net.primlixe.aurorachat.chat.ColorCodes;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.config.ChatConfig.BgStyle;
import net.primlixe.aurorachat.config.ChatThemes;
import net.primlixe.aurorachat.mixin.GuiGraphicsAccessor;
import net.primlixe.aurorachat.ui.Fonts;
import net.primlixe.aurorachat.ui.UI;
import net.primlixe.aurorachat.ui.Widgets.Choice;
import net.primlixe.aurorachat.ui.Widgets.Field;
import net.primlixe.aurorachat.ui.Widgets.Header;
import net.primlixe.aurorachat.ui.Widgets.Pill;
import net.primlixe.aurorachat.ui.Widgets.Slider;
import net.primlixe.aurorachat.ui.Widgets.Style;
import net.primlixe.aurorachat.ui.Widgets.Swatches;
import net.primlixe.aurorachat.ui.Widgets.Toggle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Настройки Aurora Chat: разделы слева, параметры по центру, предпросмотр чата справа.
 * Всё применяется сразу и сохраняется автоматически. Окно можно уменьшить и сменить ему шрифт
 * (раздел «Окно настроек») — оно рисуется в своём масштабе.
 */
public class ChatSettingsScreen extends Screen {
	enum Section {
		FEATURES("Все функции"), THEMES("Темы"), WINDOW("Окно чата"), BACKGROUND("Фон и цвета"), LOOK("Вид сообщений"),
		ANIM("Плавность"), MINECRAFT("Майнкрафт"), INPUT("Строка ввода"), COLORS("Цвета и градиенты"), CHANNELS("Каналы G / L"), MESSAGES("Сообщения"),
		AUTO("Автоматика"), WHEEL("Действия с игроком"), MODERATION("Модерация"), TABS("Вкладки"), WINDOWS("Доп. окна"), STAFF("Персонал"), AUTOJOIN("Вход на сервер"),
		NOTIFY("Уведомления"), HISTORY("История"), FILTER("Фильтр"), UI_SETTINGS("Окно настроек");
		final String ru;
		Section(String ru) { this.ru = ru; }
	}

	private static Section last = Section.FEATURES;
	/** Публичная сборка (Modrinth): без разделов под Mineblaze. */
	private static final boolean PUB = net.primlixe.aurorachat.Edition.PUBLIC;

	private static final int[] BG_COLORS = {0x0B0C16, 0x000000, 0x1B1F2A, 0x0E1A33, 0x1E0F2E, 0x0F241B, 0x2A1010, 0x2B2418};
	private static final int[] ACCENTS = {0xB18CFF, 0x6CB4FF, 0x5BE39A, 0xFFB347, 0xFF6B7A, 0xFF7EC8, 0xFFFFFF};
	private static final int[] UI_ACCENTS = {0x4F8EF7, 0x6E7BF2, 0x3FB68B, 0xD9A441, 0xE5484D, 0x9AA0AE};
	private static final int[] CH_COLORS = {0xFFA21F, 0x3EF06A, 0xFFB347, 0x6CE38A, 0x6CB4FF, 0xFF6B7A, 0xB18CFF, 0xFFE066, 0xFFFFFF};
	private static final String[] TIME_FORMATS = {"HH:mm", "HH:mm:ss", "hh:mm a", "dd.MM HH:mm"};
	private static final String[] TR_CODES = net.primlixe.aurorachat.chat.Translator.LANGS;
	private static final String[] TR_LANGS = net.primlixe.aurorachat.chat.Translator.LANG_NAMES;
	private static final String[] TR_TO_CODES = java.util.stream.Stream.concat(java.util.stream.Stream.of("auto"), Arrays.stream(TR_CODES)).toArray(String[]::new);
	private static final String[] TR_TO_LANGS = java.util.stream.Stream.concat(java.util.stream.Stream.of("Как в игре"), Arrays.stream(TR_LANGS)).toArray(String[]::new);

	private final Screen parent;
	private final long openedAt = System.nanoTime();
	private Section section = last;

	/** Масштаб окна (из настроек), координаты ниже — уже «в масштабе». */
	private float sc = 1f;
	private int navX0, navX1, conX0, conX1, conY0, conY1, prevX0, prevX1;
	private boolean showPreview, compact;
	private float scroll, smooth, maxScroll;
	private final List<AbstractWidget> scrollables = new ArrayList<>();
	private final List<AbstractWidget> fixed = new ArrayList<>();
	private final Map<AbstractWidget, Integer> baseY = new HashMap<>();
	private int cursorY;
	private long lastFrame = System.nanoTime();

	public ChatSettingsScreen(Screen parent) {
		super(Component.literal("Aurora Chat"));
		this.parent = parent;
	}

	private static ChatConfig cfg() { return ChatConfig.get(); }

	/** Открыть сразу на разделе «Действия с игроком» (кнопка ⚙ в круговом меню). */
	public static ChatSettingsScreen wheel(Screen parent) {
		last = Section.WHEEL;
		return new ChatSettingsScreen(parent);
	}

	private float intro() { return UI.ease((System.nanoTime() - openedAt) / 1e9f / 0.25f); }

	// ====================================================================== раскладка

	@Override
	protected void init() {
		// своё «виртуальное» разрешение: окно рисуется в масштабе sc
		sc = cfg().uiScale;
		var win = minecraft.getWindow();
		width = Math.round(win.getGuiScaledWidth() / sc);
		height = Math.round(win.getGuiScaledHeight() / sc);

		UI.beginSettings();
		try {
			build();
		} finally {
			UI.endSettings();
		}
	}

	private void build() {
		scrollables.clear();
		baseY.clear();
		fixed.clear();
		compact = width < 520;
		showPreview = cfg().uiPreview && width >= 600;
		navX0 = 8;
		navX1 = navX0 + (compact ? 26 : 112);
		prevX1 = width - 8;
		prevX0 = showPreview ? prevX1 - Math.min(340, Math.max(210, (int) (width * 0.32f))) : prevX1;
		conX0 = navX1 + 10;
		conX1 = (showPreview ? prevX0 : prevX1) - 10;
		conY0 = 56;
		conY1 = height - 10;

		Section[] all = Arrays.stream(Section.values()).filter(x -> !PUB || (x != Section.MODERATION && x != Section.STAFF)).toArray(Section[]::new);
		if (PUB && (section == Section.MODERATION || section == Section.STAFF)) section = last = Section.FEATURES; // разделов нет в публичной сборке
		int step = Math.max(11, Math.min(UI.rowH() + 1, (height - 48) / all.length));
		for (int i = 0; i < all.length; i++) fixed.add(addRenderableWidget(new NavItem(navX0 + 3, 42 + i * step, navX1 - navX0 - 6, step - 1, all[i])));
		fixed.add(addRenderableWidget(new Pill(width - 30, 7, 22, 16, "✕", Style.GHOST, p -> onClose()).tip("Закрыть (Esc)")));

		cursorY = conY0;
		switch (section) {
			case FEATURES -> initFeatures();
			case THEMES -> initThemes();
			case AUTO -> initAuto();
			case WHEEL -> initWheel();
			case MODERATION -> initModeration();
			case WINDOWS -> initWindows();
			case STAFF -> initStaff();
			case AUTOJOIN -> initAutoJoin();
			case LOOK -> initLook();
			case WINDOW -> initWindow();
			case BACKGROUND -> initBackground();
			case ANIM -> initAnim();
			case MINECRAFT -> initMinecraft();
			case INPUT -> initInput();
			case COLORS -> initColors();
			case CHANNELS -> initChannels();
			case MESSAGES -> initMessages();
			case TABS -> initTabs();
			case NOTIFY -> initNotify();
			case HISTORY -> initHistory();
			case FILTER -> initFilter();
			case UI_SETTINGS -> initUiSettings();
		}
		maxScroll = Math.max(0, cursorY + 8 - conY1);
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		layoutScroll();
	}

	private void switchSection(Section s) {
		if (s == section) return;
		section = s;
		last = s;
		scroll = smooth = 0;
		sectionAt = System.nanoTime();
		rebuildWidgets();
	}

	private int w() { return conX1 - conX0; }

	private int rowStep() { return UI.rowH() + 2; }

	private <T extends AbstractWidget> T add(T wd, int h) {
		wd.setX(conX0);
		wd.setY(cursorY);
		baseY.put(wd, cursorY);
		addRenderableWidget(wd);
		scrollables.add(wd);
		cursorY += h;
		return wd;
	}

	private void header(String text, String hint) { add(new Header(0, 0, w(), text, hint), 18); }

	private void note(String text) {
		Note n = new Note(w(), text);
		add(n, n.getHeight() + 2);
	}

	private void toggle(String label, BooleanSupplier get, Consumer<Boolean> set, String tip) {
		add(new Toggle(0, 0, w(), label, get, set, tip), rowStep());
	}

	private Slider slider(String label, float min, float max, float cur, Consumer<Float> set, java.util.function.Function<Float, String> fmt) {
		return add(new Slider(0, 0, w(), label, min, max, cur, set, fmt), rowStep() + 2);
	}

	private void choice(String label, String[] opts, java.util.function.IntSupplier get, java.util.function.IntConsumer set, String tip) {
		add(new Choice(0, 0, w(), label, opts, get, set, tip), rowStep());
	}

	private void swatches(String label, int[] colors, java.util.function.IntSupplier get, java.util.function.IntConsumer set, boolean animated) {
		Swatches sw = new Swatches(0, 0, w(), label, colors, get, set, animated);
		add(sw, sw.getHeight() + 2);
	}

	/** 16 цветов Minecraft (&0…&f). */
	private static final int[] LEGACY_RGB = {0xFFFFFF, 0xFFFF55, 0xFFAA00, 0xFF5555, 0xAA0000, 0xFF55FF, 0xAA00AA, 0x5555FF,
			0x0000AA, 0x55FFFF, 0x00AAAA, 0x55FF55, 0x00AA00, 0xAAAAAA, 0x555555, 0x000000};
	private static final String LEGACY_CH = "fe6c4d591b3a2780";

	/** Код цвета («&a», «&#FF8800», «&{огонь}», «&e&l») → RGB для палитры; -1 — без цвета. */
	private static int codeRgb(String code) {
		if (code == null || code.isBlank()) return -1;
		var m = java.util.regex.Pattern.compile("&#([0-9a-fA-F]{6})").matcher(code);
		if (m.find()) return Integer.parseInt(m.group(1), 16);
		for (int i = 0; i + 1 < code.length(); i++) {
			if (code.charAt(i) != '&') continue;
			int k = LEGACY_CH.indexOf(Character.toLowerCase(code.charAt(i + 1)));
			if (k >= 0 && k < LEGACY_RGB.length) return LEGACY_RGB[k];
		}
		int g0 = code.indexOf("&{"), g1 = code.indexOf('}');
		if (g0 >= 0 && g1 > g0) { int[] cs = ColorCodes.colors(code.substring(g0 + 2, g1)); if (cs != null) return cs[0]; }
		return -1;
	}

	/** RGB из палитры → код: точный цвет Minecraft — «&x», иначе «&#RRGGBB»; стили (&l &o &n &m) сохраняются. */
	private static String rgbCode(int rgb, String old) {
		StringBuilder st = new StringBuilder();
		if (old != null) for (String s : new String[]{"&l", "&o", "&n", "&m"}) if (old.toLowerCase(Locale.ROOT).contains(s)) st.append(s);
		if (rgb < 0) return st.toString();
		for (int i = 0; i < LEGACY_RGB.length; i++) if (LEGACY_RGB[i] == (rgb & 0xFFFFFF)) return "&" + LEGACY_CH.charAt(i) + st;
		return String.format(Locale.ROOT, "&#%06X", rgb & 0xFFFFFF) + st;
	}

	/** Цвет для автоцвета: палитра + спектр + «без цвета», и «жирным» отдельным переключателем. */
	private void codeColor(String label, Supplier<String> get, Consumer<String> set) {
		Swatches sw = new Swatches(0, 0, w(), label, LEGACY_RGB, () -> codeRgb(get.get()), v -> { set.accept(rgbCode(v, get.get())); }, true).none("без цвета");
		add(sw, sw.getHeight() + 2);
		toggle(label + " — жирным", () -> get.get().toLowerCase(Locale.ROOT).contains("&l"), v -> {
			String base = get.get().replace("&l", "").replace("&L", "");
			set.accept(v ? base + "&l" : base);
			ChatConfig.save();
			rebuildWidgets();
		}, null);
	}

	private Pill button(String label, Style st, Consumer<Pill> press, String tip) {
		return add(new Pill(0, 0, w(), UI.rowH(), label, st, press).tip(tip), rowStep());
	}

	private Field field(String label, String value, String hint, Consumer<String> onChange) {
		header(label, null);
		Field f = new Field(font, conX0, cursorY, w(), UI.rowH(), hint);
		f.setMaxLength(500);
		f.setValue(value);
		f.setResponder(s -> { onChange.accept(s); ChatConfig.save(); });
		return add(f, UI.rowH() + 4);
	}

	private void gap() { cursorY += 6; }

	private static String pct(float v) { return Math.round(v * 100) + "%"; }

	private static String px(float v) { return Math.round(v) + " px"; }

	private static List<String> split(String s) {
		List<String> out = new ArrayList<>();
		for (String p : s.split(",")) if (!p.isBlank()) out.add(p.trim());
		return out;
	}

	// ---------------------------------------------------------------- разделы

	/** Всё, что можно включить и выключить, — в одном месте. */
	private void initFeatures() {
		ChatConfig c = cfg();
		note("Здесь собраны все функции мода — любую можно выключить. Подробные настройки каждой — в своём разделе слева.");
		header("Главное", null);
		toggle("Aurora Chat включён", () -> c.enabled, v -> c.enabled = v, "Выключи — и чат станет полностью ванильным");
		toggle("Своё окно чата", () -> c.customHud, v -> c.customHud = v, "Выкл — ванильная отрисовка, улучшения текста остаются");
		toggle("Анимации чата", () -> c.animations, v -> c.animations = v, null);
		toggle("Плавные панели", () -> c.popupAnim, v -> c.popupAnim = v, null);
		toggle("Плавная отправка", () -> c.sendAnim, v -> c.sendAnim = v, null);
		toggle("Экономить при низком FPS", () -> c.perfAuto, v -> c.perfAuto = v, null);
		if (!PUB) toggle("Модерация по правилам", () -> c.modEnabled, v -> c.modEnabled = v, null);
		toggle("Круговое меню действий с игроком", () -> c.playerWheel, v -> c.playerWheel = v, null);
		toggle("Быстрые фразы над строкой ввода", () -> c.quickPhrases, v -> c.quickPhrases = v, null);
		gap();
		var compat = net.primlixe.aurorachat.compat.Compat.report();
		if (!compat.isEmpty()) {
			header("Совместимость с модами сборки", "найдено и настроено автоматически");
			for (String[] m : compat) note("✔ " + m[0] + " — " + m[1]);
			gap();
		}
		header("Майнкрафт", null);
		toggle("Плавное открытие экранов", () -> c.mcScreenAnim, v -> c.mcScreenAnim = v, null);
		if (net.primlixe.aurorachat.compat.Compat.hotbarByOtherMod()) note("Плавный хотбар делает SmoothScroll — его настройки в Mod Menu");
		else toggle("Плавный выбор слота хотбара", () -> c.mcSmoothHotbar, v -> c.mcSmoothHotbar = v, null);
		toggle("Список игроков (Tab) выезжает", () -> c.mcTabAnim, v -> c.mcTabAnim = v, null);
		gap();
		header("Окно чата", null);
		toggle("Строка заголовка", () -> c.titleBar, v -> c.titleBar = v, null);
		toggle("Вкладки", () -> c.tabs, v -> c.tabs = v, null);
		toggle("Переключатель G / L в шапке", () -> c.headerChannels, v -> c.headerChannels = v, null);
		toggle("Кнопка «свернуть» в шапке", () -> c.headerCollapse, v -> c.headerCollapse = v, null);
		toggle("Кнопка настроек в шапке", () -> c.headerGear, v -> c.headerGear = v, "Настройки всегда открываются командой /achat");
		toggle("Полоса прокрутки", () -> c.scrollbar, v -> c.scrollbar = v, null);
		toggle("Плашка «↓ новые»", () -> c.newMessagesHint, v -> c.newMessagesHint = v, null);
		gap();
		header("Сообщения", null);
		toggle("Время у сообщений", () -> c.timestamps, v -> c.timestamps = v, null);
		toggle("Головы игроков", () -> c.heads, v -> c.heads = v, null);
		toggle("Значки у системных сообщений", () -> c.icons, v -> c.icons = v, null);
		toggle("Цветная полоска слева", () -> c.stripes, v -> c.stripes = v, null);
		toggle("Цветные ники", () -> c.colorNames, v -> c.colorNames = v, null);
		toggle("Склейка повторов ×N", () -> c.stacking, v -> c.stacking = v, null);
		toggle("Кликабельные ссылки", () -> c.clickableLinks, v -> c.clickableLinks = v, null);
		toggle("Предпросмотр ссылок", () -> c.linkPreview, v -> c.linkPreview = v, null);
		toggle("Переводчик (ПКМ → «Перевести»)", () -> c.translate, v -> c.translate = v, null);
		toggle("Компактные входы/выходы", () -> c.compactJoins, v -> c.compactJoins = v, null);
		toggle("Кнопка «↩ ответить»", () -> c.replyButton, v -> c.replyButton = v, null);
		toggle("Закреплённые сообщения", () -> c.pins, v -> c.pins = v, null);
		toggle("Свои имена для ников", () -> c.nicknamesEnabled, v -> c.nicknamesEnabled = v, null);
		toggle("Упоминания", () -> c.mentions, v -> c.mentions = v, null);
		toggle("Карточки ЛС и упоминаний", () -> c.toasts, v -> c.toasts = v, null);
		toggle("Разделитель при входе на сервер", () -> c.sessionSeparator, v -> c.sessionSeparator = v, null);
		gap();
		header("Строка ввода", null);
		toggle("Плашка G / L в строке ввода", () -> c.inputChannelChip, v -> c.inputChannelChip = v, null);
		toggle("Стильная строка ввода", () -> c.styledInput, v -> c.styledInput = v, null);
		toggle("Поиск по чату", () -> c.searchBar, v -> c.searchBar = v, null);
		toggle("Кнопка истории ☰", () -> c.historyButton, v -> c.historyButton = v, null);
		toggle("Панель эмодзи ☺", () -> c.emojiPicker, v -> c.emojiPicker = v, null);
		toggle("Эмодзи :коды:", () -> c.emojiShortcodes, v -> c.emojiShortcodes = v, null);
		toggle("Кнопка «отправить позже» ⏱", () -> c.laterButton, v -> c.laterButton = v, null);
		toggle("Кнопка перевода ⇄", () -> c.translateButton, v -> c.translateButton = v, null);
		toggle("Палитра цветов «&»", () -> c.colorPicker, v -> c.colorPicker = v, null);
		toggle("Ники по Tab", () -> c.nickComplete, v -> c.nickComplete = v, null);
		toggle("Счётчик символов", () -> c.charCounter, v -> c.charCounter = v, null);
		toggle("Предпросмотр цветов", () -> c.colorPreview, v -> c.colorPreview = v, null);
		toggle("Меню по ПКМ", () -> c.copyMenu, v -> c.copyMenu = v, null);
		toggle("Черновики", () -> c.drafts, v -> c.drafts = v, null);
		toggle("Шаблоны {xyz} {ник}…", () -> c.templates, v -> c.templates = v, null);
		toggle("Своя история ↑ / ↓", () -> c.betterHistory, v -> c.betterHistory = v, null);
		gap();
		header("Цвета", null);
		toggle("Каналы G / L", () -> c.channels, v -> c.channels = v, null);
		toggle("Автоцвет сообщений", () -> c.autoColor, v -> c.autoColor = v, null);
		toggle("HEX-цвета и градиенты", () -> c.hexColors, v -> c.hexColors = v, null);
		gap();
		header("Автоматика", null);
		toggle("Автоприветствия", () -> c.autoGreet, v -> c.autoGreet = v, null);
		toggle("АФК-автоответчик", () -> c.afk, v -> c.afk = v, null);
		toggle("Сводка «пока тебя не было»", () -> c.afkSummary, v -> c.afkSummary = v, null);
		toggle("Антиспам", () -> c.antiSpam, v -> c.antiSpam = v, null);
		toggle("Напоминания", () -> c.remindersEnabled, v -> c.remindersEnabled = v, null);
		toggle("Отложенные сообщения", () -> c.laterSend, v -> c.laterSend = v, null);
		toggle("Автовход на сервер", () -> c.autoJoin, v -> c.autoJoin = v, null);
		if (!PUB) toggle("Слежка за персоналом", () -> c.staffWatch, v -> c.staffWatch = v, null);
		gap();
		header("Звуки", null);
		toggle("Звук упоминания", () -> c.mentionSound, v -> c.mentionSound = v, null);
		toggle("Звук личных сообщений", () -> c.dmSound, v -> c.dmSound = v, null);
		toggle("Звук напоминаний", () -> c.reminderSound, v -> c.reminderSound = v, null);
		if (!PUB) toggle("Звук персонала", () -> c.staffSound, v -> c.staffSound = v, null);
		gap();
		header("Журналы", null);
		toggle("Помнить чат после перезапуска", () -> c.persistHistory, v -> c.persistHistory = v, null);
		toggle("Помнить историю ввода", () -> c.persistInput, v -> c.persistInput = v, null);
		toggle("Журнал в файлы", () -> c.logging, v -> c.logging = v, null);
		toggle("Консоль (Ё)", () -> c.consoleLog, v -> c.consoleLog = v, "Запись событий для экрана консоли");
	}

	private void initThemes() {
		ChatConfig c = cfg();
		// ---- свои темы: сохранить, поделиться кодом, вставить чужую
		header("Мои темы", "сохрани текущий вид, отправь код другу — он вставит его у себя");
		Field tname = field("Название новой темы", "", "например: Моя ночная", s -> {});
		button("Сохранить текущий вид как тему", Style.PRIMARY, p -> {
			String n = tname.getValue().trim();
			if (n.isEmpty()) n = "Моя тема " + (c.myThemes.size() + 1);
			final String nm = n;
			c.myThemes.removeIf(x -> x.name.equalsIgnoreCase(nm));
			ChatConfig.SavedTheme st = new ChatConfig.SavedTheme();
			st.name = nm;
			st.code = net.primlixe.aurorachat.config.ThemeCodec.export(nm);
			c.myThemes.add(st);
			c.theme = nm;
			ChatConfig.save();
			rebuildWidgets();
		}, "Всё, что настроено в «Фон и цвета», «Вид сообщений», «Окно чата» (размер текста), — в одну тему");
		button("Вставить тему из буфера обмена", Style.SECONDARY, p -> {
			String clip = minecraft.keyboardHandler.getClipboard();
			var d = net.primlixe.aurorachat.config.ThemeCodec.decode(clip);
			if (d == null) { p.setLabel("В буфере нет кода темы (он начинается с AURORA-THEME:)"); return; }
			if (net.primlixe.aurorachat.config.ThemeCodec.apply(clip) == 0) { p.setLabel("Код темы повреждён"); return; }
			c.myThemes.removeIf(x -> x.name.equalsIgnoreCase(d.name()));
			ChatConfig.SavedTheme st = new ChatConfig.SavedTheme();
			st.name = d.name();
			st.code = clip.trim().substring(clip.trim().indexOf(net.primlixe.aurorachat.config.ThemeCodec.PREFIX)).split("\\s", 2)[0];
			c.myThemes.add(st);
			ChatConfig.save();
			rebuildWidgets();
		}, "Друг скопировал код своей темы — вставь его сюда");
		button("Скопировать код текущего вида", Style.SECONDARY, p -> {
			minecraft.keyboardHandler.setClipboard(net.primlixe.aurorachat.config.ThemeCodec.export(c.theme));
			p.setLabel("Код скопирован — отправь его другу ✔");
		}, null);
		for (ChatConfig.SavedTheme st : new ArrayList<>(c.myThemes)) {
			boolean sel = st.name.equals(c.theme);
			button((sel ? "✔ " : "▸ ") + st.name, sel ? Style.PRIMARY : Style.SECONDARY, p -> {
				net.primlixe.aurorachat.config.ThemeCodec.apply(st.code);
				rebuildWidgets();
			}, "Применить тему");
			button("⧉ Код «" + st.name + "» в буфер", Style.GHOST, p -> {
				minecraft.keyboardHandler.setClipboard(st.code);
				p.setLabel("Скопировано ✔");
			}, null);
			button("✕ Удалить «" + st.name + "»", Style.DANGER, p -> { c.myThemes.remove(st); ChatConfig.save(); rebuildWidgets(); }, null);
		}
		gap();
		// ---- быстрая правка: главное в одном месте (подробно — в разделах слева)
		header("Быстрая правка", "главное здесь, все детали — в «Фон и цвета» и «Вид сообщений»");
		ChatConfig.Layout[] lay = ChatConfig.Layout.values();
		choice("Раскладка", Arrays.stream(lay).map(x -> x.ru).toArray(String[]::new), () -> c.layout.ordinal(), i -> { c.layout = lay[i]; c.theme = "Своя"; }, null);
		swatches("Фон", BG_COLORS, () -> c.bgColor, v -> { c.bgColor = v; c.theme = "Своя"; }, false);
		swatches("Акцент", ACCENTS, () -> c.accentColor, v -> { c.accentColor = v; c.theme = "Своя"; }, true);
		swatches("Текст", new int[]{0xFFFFFF, 0xE6E8F2, 0xD8D2C0, 0xBFD8FF, 0xC8F2D6, 0x202020}, () -> c.textColor, v -> { c.textColor = v; c.theme = "Своя"; }, false);
		slider("Прозрачность фона", 0, 1, c.bgOpacity, v -> { c.bgOpacity = v; c.theme = "Своя"; }, ChatSettingsScreen::pct);
		slider("Скругление", 0, 12, c.radius, v -> { c.radius = Math.round(v); c.theme = "Своя"; }, v -> Math.round(v) + "");
		slider("Размер текста", 0.5f, 1.6f, c.textScale, v -> c.textScale = Math.round(v * 20) / 20f, ChatSettingsScreen::pct);
		gap();
		header("Готовые темы", "клик — применить, потом можно подправить любую деталь");
		for (ChatThemes.Theme t : ChatThemes.ALL) add(new ThemeCard(w(), t), 28);
		gap();
		button("Сбросить весь вид по умолчанию", Style.SECONDARY, p -> { ChatThemes.apply(ChatThemes.ALL.get(0)); rebuildWidgets(); }, null);
	}

	private void initAuto() {
		ChatConfig c = cfg();
		String[] names = Arrays.stream(net.primlixe.aurorachat.chat.AutoColor.PRESETS).map(p -> p[1]).toArray(String[]::new);
		String[][] pr = net.primlixe.aurorachat.chat.AutoColor.PRESETS;
		header("Автоцвет", "код цвета сам добавляется к твоим сообщениям");
		toggle("Автоцвет сообщений", () -> c.autoColor, v -> c.autoColor = v, "Работает, если сервер разрешает цветной чат (&-коды)");
		note("Первые 16 квадратиков — обычные цвета Minecraft (работают на любом сервере). Остальные цвета и спектр — HEX (&#RRGGBB): нужен сервер с HEX-цветами.");
		codeColor("Личные (/m /msg /w /r)", () -> c.colorDm, v -> c.colorDm = v);
		codeColor("Глобальный чат (G)", () -> c.colorGlobal, v -> c.colorGlobal = v);
		codeColor("Локальный чат (L)", () -> c.colorLocal, v -> c.colorLocal = v);
		field("Свой код для личных", c.colorDm, "&a&l, &#55FF99 или &{огонь}", s -> c.colorDm = s.trim());
		field("Свой код для G", c.colorGlobal, "&e, &#FFAA00 или &{закат}", s -> c.colorGlobal = s.trim());
		field("Свой код для L", c.colorLocal, "&f, &#DDDDDD или &{океан}", s -> c.colorLocal = s.trim());
		field("Команды личных сообщений", String.join(", ", c.dmCommands), "через запятую, без /", s -> c.dmCommands = split(s.toLowerCase(Locale.ROOT)));
		gap();
		if (!PUB) {
		header("Викторины в чате", "«Chat Game » Решите пример…» — мод знает ответ");
		toggle("Распознавать викторины", () -> c.chatGames, v -> c.chatGames = v, "Примеры решает сам, остальные вопросы запоминает по «Правильный ответ: …»");
		toggle("Кнопка [= ответ]", () -> c.chatGameHint, v -> c.chatGameHint = v, "Клик — ответ в строку ввода");
		toggle("Отвечать автоматически", () -> c.chatGameAuto, v -> c.chatGameAuto = v, "Сам пишет ответ в чат. Осторожно: на некоторых серверах за это наказывают");
		slider("Задержка автоответа", 0f, 10f, c.chatGameDelay, v -> c.chatGameDelay = v < 0.05f ? 0f : v, v -> v < 0.05f ? "сразу" : String.format(java.util.Locale.ROOT, "%.1f с", v));
		field("Начало сообщения викторины", c.chatGamePrefix, "Chat Game »", s -> c.chatGamePrefix = s);
		field("Перед ответом ставить", c.chatGameAnswerPrefix, "пусто, или ! — в глобальный", s -> c.chatGameAnswerPrefix = s);
		gap();
		}
		header("Автоприветствия", "пишут «пр Ник», «ку Ник», «qq Ник» — отвечаю сам");
		toggle("Отвечать на приветствия", () -> c.autoGreet, v -> c.autoGreet = v, "Ответ уходит через 1–2,5 с, не чаще паузы на игрока");
		String[] chs = {"Глобальный (G)", "Локальный (L)", "Текущий канал"};
		String[] chv = {"G", "L", "CURRENT"};
		choice("Куда отвечать", chs, () -> Math.max(0, Arrays.asList(chv).indexOf(c.greetChannel)), i -> c.greetChannel = chv[i], null);
		toggle("Отвечать тем же словом", () -> c.greetMirror, v -> c.greetMirror = v, "«qq Ник» → «qq Steve». Выкл — случайный ответ из списка ниже");
		toggle("Отвечать на «всем привет»", () -> c.greetAll, v -> c.greetAll = v, "Даже если твой ник не написали");
		field("Ответ в клан: как писать в клановый чат", c.greetClan, "пусто — в клане не отвечать · /c или /clan chat {text}", s -> c.greetClan = s.trim());
		slider("Пауза на игрока", 5, 600, c.greetCooldown, v -> c.greetCooldown = Math.round(v), v -> Math.round(v) + " с");
		field("Слова-приветствия", String.join(", ", c.greetWords), "пр, ку, qq, q, привет…", s -> c.greetWords = split(s.toLowerCase(Locale.ROOT)));
		field("Ответы (через |, {name} — ник)", String.join(" | ", c.greetReplies), "Привет, {name}! | ку, {name}", s -> {
			List<String> out = new ArrayList<>();
			for (String p : s.split("\\|")) if (!p.isBlank()) out.add(p.trim());
			if (!out.isEmpty()) c.greetReplies = out;
		});
		gap();
		header("АФК-автоответчик", "/achat afk — включить вручную");
		toggle("Автоответчик", () -> c.afk, v -> c.afk = v, "Отвечает на личные, пока ты неактивен");
		slider("АФК после бездействия", 0, 60, c.afkMinutes, v -> c.afkMinutes = Math.round(v), v -> Math.round(v) == 0 ? "только вручную" : Math.round(v) + " мин");
		field("Текст ответа", c.afkMessage, "Я сейчас АФК, отвечу позже :)", s -> c.afkMessage = s);
		field("Команда ответа ({name}, {text})", c.afkCommand, "/msg {name} {text}", s -> c.afkCommand = s.isBlank() ? "/msg {name} {text}" : s);
		toggle("Сводка «пока тебя не было»", () -> c.afkSummary, v -> c.afkSummary = v,
				"Вернулся после простоя — в чате карточка: кто писал в ЛС, кто упоминал, нарушения, кто из персонала заходил. Работает и без автоответчика");
		slider("Не чаще одному игроку", 10, 1800, c.afkCooldown, v -> c.afkCooldown = Math.round(v / 10f) * 10, v -> Math.round(v / 60f) + " мин");
		gap();
		header("Антиспам", "повторы и флуд от одного игрока");
		toggle("Антиспам", () -> c.antiSpam, v -> c.antiSpam = v, "Подряд одинаковые и так склеиваются в ×N");
		slider("Повтор скрывать в течение", 0, 300, c.spamRepeatSeconds, v -> c.spamRepeatSeconds = Math.round(v), v -> Math.round(v) == 0 ? "выкл" : Math.round(v) + " с");
		slider("Флуд: больше сообщений", 2, 20, c.floodLimit, v -> c.floodLimit = Math.round(v), v -> Math.round(v) + "");
		slider("…за время", 2, 60, c.floodSeconds, v -> c.floodSeconds = Math.round(v), v -> Math.round(v) + " с");
		toggle("Гасить КАПС", () -> c.antiCaps, v -> c.antiCaps = v, "Сообщения ЦЕЛИКОМ ЗАГЛАВНЫМИ показываются строчными");
		gap();
		header("Напоминания", "/achat remind 10m текст · /achat remind 20:30 текст · /achat timer 5m");
		toggle("Напоминания", () -> c.remindersEnabled, v -> c.remindersEnabled = v, null);
		toggle("Звук напоминания", () -> c.reminderSound, v -> c.reminderSound = v, null);
		Field rf = field("Новое напоминание", "", "10m сходить на ивент  ·  20:30 рейд", s -> {});
		button("Добавить напоминание", Style.PRIMARY, p -> {
			String r = net.primlixe.aurorachat.chat.Reminders.add(rf.getValue(), "Напоминание");
			if (r != null) rebuildWidgets(); else p.setLabel("Не понял время — пример: 10m текст");
		}, null);
		for (int i = 0; i < c.reminders.size(); i++) {
			int idx = i;
			ChatConfig.Reminder rem = c.reminders.get(i);
			button("✕ " + net.primlixe.aurorachat.chat.Reminders.describe(rem), Style.SECONDARY,
					p -> { c.reminders.remove(rem); ChatConfig.save(); rebuildWidgets(); }, "Удалить напоминание");
		}
		gap();
		header("Отложенные сообщения", "кнопка ⏱ в чате · /achat later 10m текст");
		toggle("Отложенные сообщения", () -> c.laterSend, v -> c.laterSend = v, "Выкл — очередь не отправляется и кнопка ⏱ скрыта");
		Field lf = field("Новое отложенное", "", "10m всем удачи на ивенте  ·  20:30 /m Steve го", s -> {});
		button("Запланировать", Style.PRIMARY, p -> {
			String r = net.primlixe.aurorachat.chat.LaterSend.add(lf.getValue());
			if (r != null) rebuildWidgets(); else p.setLabel("Пример: 10m текст");
		}, null);
		if (c.scheduled.isEmpty()) add(new Header(0, 0, w(), "", "очередь пуста"), 18);
		for (int i = 0; i < c.scheduled.size(); i++) {
			int idx = i;
			ChatConfig.Scheduled sch = c.scheduled.get(i);
			button("✕ " + net.primlixe.aurorachat.chat.LaterSend.describe(sch), Style.SECONDARY,
					p -> { c.scheduled.remove(sch); ChatConfig.save(); rebuildWidgets(); }, "Отменить отправку");
		}
	}

	private void initModeration() {
		ChatConfig c = cfg();
		note("Пиши правило своими словами — например «1.3 Реклама других серверов — мут 1 час, потом бан 7 дней» или "
				+ "«2.1 Запрещены слова: \"слив\", \"читы\" — варн». Мод сам поймёт, что искать (мат, оскорбления, капс, флуд, реклама "
				+ "или твои слова) и какие команды выдавать за 1-е, 2-е, 3-е нарушение. Потом всё можно поправить.");
		header("Модерация", null);
		toggle("Проверять чат по правилам", () -> c.modEnabled, v -> c.modEnabled = v, "Сообщения других игроков сверяются с правилами ниже");
		ChatConfig.ModMode[] mm = ChatConfig.ModMode.values();
		choice("Что делать при нарушении", Arrays.stream(mm).map(x -> x.ru).toArray(String[]::new), () -> c.modMode.ordinal(), i -> c.modMode = mm[i],
				"Предлагать — в чате кнопка [Выдать]: клик кладёт команду в строку, Enter — выдать. Выдавать сам — команда уходит автоматически");
		toggle("Звук при нарушении", () -> c.modSound, v -> c.modSound = v, null);
		toggle("Не трогать персонал из списка", () -> c.modIgnoreStaff, v -> c.modIgnoreStaff = v, "Ники из раздела «Персонал»");
		slider("Считать повторы за", 1, 168, c.modWindowHours, v -> c.modWindowHours = Math.round(v), v -> Math.round(v) + " ч");
		slider("Не чаще одному игроку", 0, 120, c.modCooldown, v -> c.modCooldown = Math.round(v), v -> Math.round(v) == 0 ? "без паузы" : Math.round(v) + " с");
		field("Не проверять игроков", String.join(", ", c.modWhitelist), "ники через запятую", s -> c.modWhitelist = split(s));
		field("Разрешённые сайты (не реклама)", String.join(", ", c.modAllowedDomains), "мой-сервер.ru, discord.gg/мой", s -> c.modAllowedDomains = split(s.toLowerCase(Locale.ROOT)));
		button("Журнал наказаний (" + net.primlixe.aurorachat.chat.PunishLog.list().size() + ")", Style.PRIMARY,
				p -> net.primlixe.aurorachat.compat.Mc.setScreen(new PunishLogScreen(this)), "Кто, когда, за что · снять наказание · выгрузить в файл · /achat punish");
		gap();
		header("Защита от ошибок", "чтобы самому не получить бан за неверное наказание");
		toggle("Тренировка (ничего не отправлять)", () -> c.modDryRun, v -> c.modDryRun = v,
				"Автонаказания только пишутся в чат и журнал «выдал бы…». Включи на первые дни и проверь, что правила не ошибаются");
		slider("Задержка перед автовыдачей", 0, 30, c.modConfirmDelay, v -> c.modConfirmDelay = Math.round(v),
				v -> Math.round(v) == 0 ? "сразу" : Math.round(v) + " с").tip("За это время можно отменить: клавиша Delete (меняется в управлении) или /achat cancel");
		toggle("Баны — только с подтверждением", () -> c.modBanConfirm, v -> c.modBanConfirm = v, "ban и tempban сам не выдаёт — только кнопкой [Выдать]");
		slider("Сам — не дольше", 5, 1440, c.modMaxAutoMinutes, v -> c.modMaxAutoMinutes = Math.round(v / 5f) * 5,
				v -> { int m = Math.round(v / 5f) * 5; return m >= 60 ? (m / 60) + " ч" + (m % 60 > 0 ? " " + m % 60 + " мин" : "") : m + " мин"; })
				.tip("Мут дольше этого — только кнопкой");
		slider("Сам — не больше в час", 1, 60, c.modMaxPerHour, v -> c.modMaxPerHour = Math.round(v), v -> Math.round(v) + "");
		gap();
		header("Команды сервера", "warn / mute 30m / kick / tempban 7d / ban в правилах превращаются в эти команды");
		note("{name} — ник, {time} — срок, {reason} — правило. Если на сервере команды другие (например /tempmute), поменяй здесь один раз.");
		field("Предупреждение (warn)", c.cmdWarn, "/warn {name} {reason}", s -> c.cmdWarn = s.trim());
		field("Мут (mute 30m)", c.cmdMute, "/mute {name} {time} {reason}", s -> c.cmdMute = s.trim());
		field("Кик (kick)", c.cmdKick, "/kick {name} {reason}", s -> c.cmdKick = s.trim());
		field("Временный бан (tempban 7d)", c.cmdTempban, "/tempban {name} {time} {reason}", s -> c.cmdTempban = s.trim());
		field("Бан навсегда (ban)", c.cmdBan, "/ban {name} {reason}", s -> c.cmdBan = s.trim());
		field("Снять мут", c.cmdUnmute, "/unmute {name}", s -> c.cmdUnmute = s.trim());
		field("Снять бан", c.cmdUnban, "/unban {name}", s -> c.cmdUnban = s.trim());
		gap();
		header("Готовые наборы правил", "добавляются к твоим — потом всё можно менять");
		for (int ti = 0; ti < net.primlixe.aurorachat.chat.Moderation.TEMPLATES.length; ti++) {
			int idx = ti;
			button("Добавить: " + net.primlixe.aurorachat.chat.Moderation.TEMPLATES[ti], ti == 2 ? Style.PRIMARY : Style.SECONDARY, p -> {
				c.modRules.addAll(net.primlixe.aurorachat.chat.Moderation.template(idx));
				ChatConfig.save();
				rebuildWidgets();
			}, idx == 2 ? "Настоящие пункты 4.x правил Mineblaze, которые видно по чату (мат, флуд, реклама, продажа за реал…)" : null);
		}
		button("Вставить правила сервера из буфера обмена", Style.PRIMARY, p -> {
			var list = net.primlixe.aurorachat.chat.Moderation.importText(minecraft.keyboardHandler.getClipboard());
			if (list.isEmpty()) { p.setLabel("В буфере не нашёл правил — скопируй текст правил (Ctrl+C)"); return; }
			c.modRules.addAll(list);
			ChatConfig.save();
			rebuildWidgets();
		}, "Скопируй правила с сайта/форума сервера или из /rules — каждая строка станет правилом");
		if (!c.modRules.isEmpty()) button("Удалить все правила", Style.DANGER, p -> {
			if (!p.getMessage().getString().equals("Точно удалить все?")) { p.setLabel("Точно удалить все?"); p.setMessage(Component.literal("Точно удалить все?")); return; }
			c.modRules.clear();
			ChatConfig.save();
			rebuildWidgets();
		}, null);
		gap();
		header("Новое правило", null);
		Field rule = field("Правило своими словами", "", "1.3 Реклама — мут 1 час, потом бан 7 дней", s -> {});
		button("Добавить правило", Style.PRIMARY, p -> {
			String t = rule.getValue().trim();
			if (t.isEmpty()) { p.setLabel("Сначала напиши правило"); return; }
			c.modRules.add(net.primlixe.aurorachat.chat.Moderation.fromText(t));
			ChatConfig.save();
			rebuildWidgets();
		}, null);
		String[][] presets = {{"PROFANITY", "Мат"}, {"INSULTS", "Оскорбления"}, {"CAPS", "Капс"}, {"FLOOD", "Флуд"}, {"ADS", "Реклама"}};
		for (String[] pr : presets) {
			button("Готовое: " + pr[1], Style.SECONDARY, p -> {
				c.modRules.add(net.primlixe.aurorachat.chat.Moderation.preset(pr[0], pr[1]));
				ChatConfig.save();
				rebuildWidgets();
			}, null);
		}
		gap();
		header("Проверить сообщение", null);
		Field test = field("Текст", "", "напиши, как написал бы нарушитель", s -> {});
		add(new ResultLine(w(), () -> {
			String v = test.getValue();
			if (v.isBlank()) return "";
			var r = net.primlixe.aurorachat.chat.Moderation.test(v);
			return r == null ? "§aнарушений нет" : "§c⚑ нарушает «" + r.name + "» §7→ " + (r.actions.isEmpty() ? "без наказания" : net.primlixe.aurorachat.chat.Moderation.resolve(r.actions.get(0), "Ник", r.name, 1, v));
		}), UI.rowH() + 4);
		for (int i = 0; i < c.modRules.size(); i++) {
			ChatConfig.ModRule r = c.modRules.get(i);
			int idx = i;
			gap();
			int ti = Math.max(0, Arrays.asList(net.primlixe.aurorachat.chat.Moderation.TYPES).indexOf(r.type));
			header("Правило " + (i + 1), net.primlixe.aurorachat.chat.Moderation.TYPES_RU[ti]);
			toggle("Включено", () -> r.enabled, v -> r.enabled = v, null);
			field("Название (для {rule})", r.name, "1.3 Реклама", s -> r.name = s.isBlank() ? "Правило" : s.trim());
			choice("Что искать", net.primlixe.aurorachat.chat.Moderation.TYPES_RU, () -> Math.max(0, Arrays.asList(net.primlixe.aurorachat.chat.Moderation.TYPES).indexOf(r.type)),
					k -> { r.type = net.primlixe.aurorachat.chat.Moderation.TYPES[k]; rebuildWidgets(); }, null);
			switch (r.type) {
				case "WORDS", "INSULTS", "PROFANITY" -> field(r.type.equals("PROFANITY") ? "Ещё слова (мат уже знаю)" : "Слова через запятую",
						String.join(", ", r.words), "слив, читы, продам акк", s -> r.words = split(s.toLowerCase(Locale.ROOT)));
				case "REGEX" -> field("Регулярные выражения через запятую", String.join(", ", r.words), "(?i)ч[иы]т", s -> r.words = split(s));
				case "CAPS" -> slider("Заглавных букв не меньше", 30, 100, r.capsPercent, v -> r.capsPercent = Math.round(v), v -> Math.round(v) + "%");
				case "FLOOD" -> {
					slider("Одинаковых сообщений", 2, 10, r.floodCount, v -> r.floodCount = Math.round(v), v -> Math.round(v) + "");
					slider("За время", 3, 300, r.floodSeconds, v -> r.floodSeconds = Math.round(v), v -> Math.round(v) + " с");
				}
				default -> { }
			}
			field("Наказания за 1-е, 2-е, 3-е… (через ;)", String.join(" ; ", r.actions), "warn ; mute 30m ; tempban 1d", s -> {
				List<String> out = new ArrayList<>();
				for (String a : s.split(";")) if (!a.isBlank()) out.add(a.trim());
				r.actions = out;
			});
			button("Удалить правило", Style.DANGER, p -> { c.modRules.remove(r); ChatConfig.save(); rebuildWidgets(); }, null);
		}
		var hits = net.primlixe.aurorachat.chat.Moderation.recent();
		if (!hits.isEmpty()) {
			gap();
			header("Последние нарушения", null);
			java.time.format.DateTimeFormatter tf = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
			for (int i = 0; i < Math.min(10, hits.size()); i++) {
				var h = hits.get(i);
				String time = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(h.time()), java.time.ZoneId.systemDefault()).format(tf);
				note(time + "  " + h.player() + " — «" + h.rule() + "»: " + h.text() + (h.command() != null ? (h.auto() ? "  → выдано " : "  → ") + h.command() : ""));
			}
		}
	}

	private void initStaff() {
		ChatConfig c = cfg();
		header("Слежка за персоналом", "хелперы и модераторы — находятся сами или вписываешь ник");
		toggle("Предупреждать", () -> c.staffWatch, v -> c.staffWatch = v,
				"Появился в табе · написал в чат · сообщение о входе · оказался рядом в мире, даже без таба");
		toggle("Звук", () -> c.staffSound, v -> c.staffSound = v, null);
		toggle("Предупреждать о выходе", () -> c.staffLeave, v -> c.staffLeave = v, null);
		toggle("Находить персонал сам", () -> c.staffAuto, v -> c.staffAuto = v, "По рангу в чате, в табе и в старых журналах. «Хелпер=a» — только зелёный [Хелпер] (жёлтый — донат). Владелец, Модер, Админ и др. — донат");
		field("Ранги персонала (через запятую, =цвет)", String.join(", ", c.staffRanks), "Хелпер=a, Модератор, Гл.Модератор", v -> c.staffRanks = split(v));
		toggle("Значок ★ перед их сообщениями", () -> c.staffBadge, v -> c.staffBadge = v, null);
		gap();
		String[] roles = {"Модератор", "Хелпер", "Гл.Модератор", "Ютубер", "Свой"};
		int[] roleCols = {0xE04848, 0x55FF55, 0x5B8CFF, 0xFF4FD8, 0xB18CFF};
		for (int i = 0; i < c.staff.size(); i++) {
			ChatConfig.Staff s = c.staff.get(i);
			int idx = i;
			boolean on = net.primlixe.aurorachat.chat.StaffWatch.online().contains(s.nick.toLowerCase(Locale.ROOT));
			header((on ? "● " : "") + s.nick, on ? "сейчас на сервере" : null);
			field("Ник", s.nick, "ник", v -> s.nick = v.trim());
			field("Роль (подпись)", s.role, "Модератор", v -> s.role = v.isBlank() ? "Модератор" : v.trim());
			swatches("Цвет", roleCols, () -> s.color, v -> s.color = v, false);
			button("Убрать " + s.nick, Style.DANGER, p -> { c.staff.remove(s); ChatConfig.save(); rebuildWidgets(); }, null);
			gap();
		}
		header("Добавить", "или командой /achat staff add Ник Роль");
		Field nick = field("Ник", "", "ник игрока", v -> {});
		for (int r = 0; r < roles.length; r++) {
			int ri = r;
			button("Добавить как: " + roles[r], r == 0 ? Style.PRIMARY : Style.SECONDARY, p -> {
				String n = nick.getValue().trim();
				if (n.isEmpty()) { p.setLabel("Сначала впиши ник"); return; }
				c.staff.removeIf(x -> x.nick.equalsIgnoreCase(n));
				ChatConfig.Staff s = new ChatConfig.Staff();
				s.nick = n; s.role = roles[ri]; s.color = roleCols[ri];
				c.staff.add(s);
				ChatConfig.save();
				rebuildWidgets();
			}, null);
		}
	}

	private void initAutoJoin() {
		ChatConfig c = cfg();
		var list = net.primlixe.aurorachat.chat.AutoJoin.entries();
		header("Автовход на сервер", "команды по очереди при заходе");
		toggle("Автовход включён", () -> c.autoJoin, v -> c.autoJoin = v, "Главный выключатель для всех серверов ниже");
		note("Пароли хранятся только на этом ПК: config/aurorachat/autojoin.json · в консоли и истории ввода скрыты.");
		for (int i = 0; i < list.size(); i++) {
			var e = list.get(i);
			int idx = i;
			gap();
			header("Сервер " + (i + 1), e.server.isBlank() ? "адрес не указан" : e.server);
			toggle("Включено", () -> e.enabled, v -> { e.enabled = v; net.primlixe.aurorachat.chat.AutoJoin.save(); }, null);
			field("Адрес (часть) или * для всех", e.server, "mc.example.ru", v -> { e.server = v.trim(); net.primlixe.aurorachat.chat.AutoJoin.save(); });
			field("Команды через ;", String.join(" ; ", e.commands), "/login пароль ; /warp spawn", v -> {
				List<String> out = new ArrayList<>();
				for (String p : v.split(";")) if (!p.isBlank()) out.add(p.trim());
				e.commands = out;
				net.primlixe.aurorachat.chat.AutoJoin.save();
			});
			slider("Пауза между командами", 300, 10000, e.delay, v -> { e.delay = Math.round(v / 100f) * 100; net.primlixe.aurorachat.chat.AutoJoin.save(); }, v -> String.format(Locale.ROOT, "%.1f с", Math.round(v / 100f) / 10f));
			button("Удалить", Style.DANGER, p -> { list.remove(e); net.primlixe.aurorachat.chat.AutoJoin.save(); rebuildWidgets(); }, null);
		}
		gap();
		button("Добавить для текущего сервера (" + net.primlixe.aurorachat.chat.AutoJoin.currentServer() + ")", Style.PRIMARY, p -> {
			var e = new net.primlixe.aurorachat.chat.AutoJoin.Entry();
			e.server = net.primlixe.aurorachat.chat.AutoJoin.currentServer();
			e.commands.add("/login пароль");
			list.add(e);
			net.primlixe.aurorachat.chat.AutoJoin.save();
			rebuildWidgets();
		}, null);
		button("Добавить для всех серверов (*)", Style.SECONDARY, p -> {
			var e = new net.primlixe.aurorachat.chat.AutoJoin.Entry();
			e.server = "*";
			list.add(e);
			net.primlixe.aurorachat.chat.AutoJoin.save();
			rebuildWidgets();
		}, null);
	}

	private void initWindows() {
		ChatConfig c = cfg();
		header("Отдельные окна", "в игре: шапка — перенос, уголок — размер, колесо — прокрутка");
		for (int i = 0; i < c.windows.size(); i++) {
			ChatConfig.ExtraWindow w = c.windows.get(i);
			int idx = i;
			field("Окно " + (i + 1) + " — название", w.name, "название", s -> w.name = s.isBlank() ? "Окно" : s);
			choice("Что показывать", net.primlixe.aurorachat.chat.ExtraWindows.KINDS_RU,
					() -> Math.max(0, Arrays.asList(net.primlixe.aurorachat.chat.ExtraWindows.KINDS).indexOf(w.kind)),
					k -> { w.kind = net.primlixe.aurorachat.chat.ExtraWindows.KINDS[k]; rebuildWidgets(); }, null);
			if (w.kind.equals("CUSTOM")) field("Слова через запятую", String.join(", ", w.keywords), "например: продам, куплю", s -> w.keywords = split(s));
			toggle("Только в этом окне", () -> w.exclusive, v -> w.exclusive = v, "Такие сообщения не дублируются в основном чате");
			toggle("Видно, когда чат закрыт", () -> w.showClosed, v -> w.showClosed = v, null);
			button("Удалить окно " + (i + 1), Style.DANGER, p -> { c.windows.remove(w); ChatConfig.save(); rebuildWidgets(); }, null);
			gap();
		}
		button("Окно для личных сообщений", Style.PRIMARY, p -> { net.primlixe.aurorachat.chat.ExtraWindows.addDefault("DM"); rebuildWidgets(); }, null);
		button("Окно для глобального чата (G)", Style.SECONDARY, p -> { net.primlixe.aurorachat.chat.ExtraWindows.addDefault("GLOBAL"); rebuildWidgets(); }, null);
		button("Окно для клана (⚑)", Style.SECONDARY, p -> { net.primlixe.aurorachat.chat.ExtraWindows.addDefault("CLAN"); rebuildWidgets(); }, null);
		button("Окно со своими словами", Style.SECONDARY, p -> { net.primlixe.aurorachat.chat.ExtraWindows.addDefault("CUSTOM"); rebuildWidgets(); }, null);
	}

	private void fontChoice(String label, Supplier<String> get, Consumer<String> set) {
		boolean custom = Fonts.indexOf(get.get()) < 0;
		String[] names = custom ? new String[]{Fonts.NAMES[0], Fonts.NAMES[1], "Свой: " + get.get()} : Fonts.NAMES;
		choice(label, names, () -> custom && Fonts.indexOf(get.get()) < 0 ? 2 : Math.max(0, Fonts.indexOf(get.get())),
				i -> { if (i < Fonts.IDS.length) set.accept(Fonts.IDS[i]); rebuildWidgets(); },
				"Обычный — как в игре · Ровный — Unifont, чёткий и одинаковой высоты");
		field(label + " из ресурспака (необязательно)", custom ? get.get() : "", "namespace:имя, например mypack:clean",
				s -> { if (!s.isBlank()) set.accept(s.trim()); });
	}

	private void initLook() {
		ChatConfig c = cfg();
		header("Раскладка", "как выглядят сообщения");
		ChatConfig.Layout[] lay = ChatConfig.Layout.values();
		choice("Раскладка", Arrays.stream(lay).map(x -> x.ru).toArray(String[]::new), () -> c.layout.ordinal(), i -> c.layout = lay[i],
				"Компактная — всё в одну строку · Как в Discord — ник и время над текстом, аватар, группы · Мессенджер — пузыри, свои справа");
		fontChoice("Шрифт сообщений", () -> c.chatFont, v -> c.chatFont = v);
		gap();
		header("Строки", null);
		slider("Отступ внутри сообщения", 0, 6, c.rowPadding, v -> c.rowPadding = Math.round(v), v -> Math.round(v) + " px");
		slider("Расстояние между сообщениями", 0, 8, c.messageGap, v -> c.messageGap = Math.round(v), v -> Math.round(v) + " px");
		slider("Скругление пузырей", 0, 10, c.bubbleRadius, v -> c.bubbleRadius = Math.round(v), v -> Math.round(v) + "");
		toggle("Цветная полоска слева", () -> c.stripes, v -> c.stripes = v, "Цвет по типу сообщения или нику");
		slider("Толщина полоски", 1, 4, c.stripeWidth, v -> c.stripeWidth = Math.round(v), v -> Math.round(v) + " px");
		toggle("Чередовать фон строк", () -> c.zebra, v -> c.zebra = v, "Каждая вторая строка чуть светлее");
		toggle("Разделители между сообщениями", () -> c.separators, v -> c.separators = v, null);
		toggle("Подсветка при наведении", () -> c.hoverHighlight, v -> c.hoverHighlight = v, null);
		gap();
		header("Игроки", null);
		toggle("Головы игроков", () -> c.heads, v -> c.heads = v, null);
		slider("Размер головы", 6, 16, c.headSize, v -> c.headSize = Math.round(v), v -> Math.round(v) + " px");
		toggle("Значки у системных сообщений", () -> c.icons, v -> c.icons = v, "✦ + − ✉ @ вместо головы");
		toggle("Цветные ники", () -> c.colorNames, v -> c.colorNames = v, null);
		gap();
		header("Время и каналы", null);
		ChatConfig.TimePos[] tp = ChatConfig.TimePos.values();
		choice("Время", Arrays.stream(tp).map(x -> x.ru).toArray(String[]::new), () -> c.timePos.ordinal(), i -> c.timePos = tp[i], null);
		ChatConfig.BadgeStyle[] bs = ChatConfig.BadgeStyle.values();
		choice("Метка канала", Arrays.stream(bs).map(x -> x.ru).toArray(String[]::new), () -> c.badgeStyle.ordinal(), i -> c.badgeStyle = bs[i], "Как показывать G / L у сообщения");
		gap();
		header("Ники и время", null);
		toggle("Ники жирным", () -> c.boldNames, v -> c.boldNames = v, "Для новых сообщений");
		toggle("Ники курсивом", () -> c.nameItalic, v -> c.nameItalic = v, null);
		toggle("Ники подчёркнутые", () -> c.nameUnderline, v -> c.nameUnderline = v, null);
		toggle("Ники КАПСОМ", () -> c.nameCaps, v -> c.nameCaps = v, null);
		slider("Размер времени", 0.6f, 1f, c.timeScale, v -> c.timeScale = Math.round(v * 20) / 20f, ChatSettingsScreen::pct);
		toggle("Время жирным", () -> c.timeBold, v -> c.timeBold = v, null);
		gap();
		header("Вкладки", null);
		ChatConfig.TabStyle[] ts = ChatConfig.TabStyle.values();
		choice("Стиль вкладок", Arrays.stream(ts).map(x -> x.ru).toArray(String[]::new), () -> c.tabStyle.ordinal(), i -> c.tabStyle = ts[i], null);
		toggle("Счётчики непрочитанного", () -> c.unreadCounters, v -> c.unreadCounters = v, null);
		if (!PUB) toggle("Вкладка «Персонал» вместо «Система»", () -> c.staffTab, v -> c.staffTab = v, null);
		toggle("Пауза, пока мышь над чатом", () -> c.pauseOnHover, v -> c.pauseOnHover = v, "Выкл — чат останавливается, только когда отмотал вверх (новые копятся под «↓ N нов.»). Вкл — ещё и от наведения мыши");
	}

	private void initWindow() {
		ChatConfig c = cfg();
		header("Чат", null);
		toggle("Aurora Chat включён", () -> c.enabled, v -> c.enabled = v, "Выключи — и чат станет полностью ванильным");
		toggle("Своё окно чата", () -> c.customHud, v -> c.customHud = v, "Окно Aurora: фон, анимации, вкладки, перенос. Выкл — ванильная отрисовка, но улучшения текста остаются");
		gap();
		header("Размер и положение", "в игре: ⠿ — перенос, любой край — размер, Ctrl+колесо — текст, Alt+колесо — фон");
		var win = minecraft.getWindow();
		int gw = win.getGuiScaledWidth(), gh = win.getGuiScaledHeight();
		slider("Ширина", 100, Math.max(130, gw - 8), c.winW, v -> c.winW = Math.round(v), ChatSettingsScreen::px);
		slider("Высота", 30, Math.max(60, gh - 44), c.winH, v -> c.winH = Math.round(v), ChatSettingsScreen::px);
		slider("Отступ слева", 0, Math.max(10, gw - c.winW), c.winLeft, v -> c.winLeft = Math.round(v), ChatSettingsScreen::px);
		slider("Отступ снизу", 0, Math.max(10, gh - 30), c.winBottom, v -> c.winBottom = Math.round(v), ChatSettingsScreen::px);
		slider("Высота, когда чат закрыт", 0.2f, 1f, c.closedHeight, v -> c.closedHeight = v, ChatSettingsScreen::pct);
		slider("Размер текста", 0.5f, 1.6f, c.textScale, v -> c.textScale = Math.round(v * 20) / 20f, ChatSettingsScreen::pct);
		slider("Расстояние между строками", 0, 6, c.lineSpacing, v -> c.lineSpacing = Math.round(v), v -> Math.round(v) + "");
		toggle("Закрепить окно", () -> c.locked, v -> c.locked = v, "Спрятать ручки перемещения и размера");
		toggle("Свернуть окно", () -> c.collapsed, v -> c.collapsed = v, "В открытом чате видно только пару строк");
		gap();
		header("Шапка окна", null);
		toggle("Строка заголовка", () -> c.titleBar, v -> c.titleBar = v, "Вкладки, переключатель канала и кнопки над окном");
		toggle("Переключатель G / L", () -> c.headerChannels, v -> c.headerChannels = v, null);
		toggle("Кнопка «свернуть»", () -> c.headerCollapse, v -> c.headerCollapse = v, null);
		toggle("Кнопка настроек", () -> c.headerGear, v -> c.headerGear = v, "Настройки всегда открываются командой /achat");
		gap();
		header("Прокрутка", "колесо мыши, PageUp/PageDown, Shift — быстрее");
		toggle("Полоса прокрутки", () -> c.scrollbar, v -> c.scrollbar = v, "Её можно тянуть мышкой");
		toggle("Плашка «↓ новые»", () -> c.newMessagesHint, v -> c.newMessagesHint = v, "Когда прокрутил вверх, а пришли новые сообщения");
		slider("Скорость прокрутки", 0.3f, 4f, c.scrollSpeed, v -> c.scrollSpeed = v, v -> String.format(Locale.ROOT, "×%.1f", v));
		button("Переместить в игре", Style.PRIMARY, p -> net.primlixe.aurorachat.compat.Mc.setScreen(new ChatScreen("", false)), "Открой чат и тяни окно за ⠿, размер — за уголок справа сверху");
		button("Вернуть размер и место", Style.SECONDARY, p -> { c.resetWindow(); ChatConfig.save(); rebuildWidgets(); }, null);
	}

	private void initBackground() {
		ChatConfig c = cfg();
		header("Фон", null);
		BgStyle[] styles = BgStyle.values();
		String[] names = Arrays.stream(styles).map(s -> s.ru).toArray(String[]::new);
		choice("Стиль", names, () -> c.bgStyle.ordinal(), i -> c.bgStyle = styles[i], "Панель — одно окно, пузыри — у каждого сообщения свой фон");
		swatches("Цвет фона", BG_COLORS, () -> c.bgColor, v -> c.bgColor = v, false);
		float[] hsb = UI.toHsb(c.bgColor);
		add(new Slider(0, 0, w(), "Оттенок", 0, 1, hsb[0], v -> c.bgColor = UI.hsb(v, Math.max(0.05f, UI.toHsb(c.bgColor)[1]), UI.toHsb(c.bgColor)[2], 1) & 0xFFFFFF, v -> "").hue(), rowStep() + 2);
		add(new Slider(0, 0, w(), "Насыщенность", 0, 1, hsb[1], v -> { float[] t = UI.toHsb(c.bgColor); c.bgColor = UI.hsb(t[0], v, t[2], 1) & 0xFFFFFF; }, ChatSettingsScreen::pct)
				.gradient(() -> { float[] t = UI.toHsb(c.bgColor); return new int[]{UI.hsb(t[0], 0, Math.max(0.3f, t[2]), 1), UI.hsb(t[0], 1, Math.max(0.3f, t[2]), 1)}; }), rowStep() + 2);
		add(new Slider(0, 0, w(), "Яркость", 0, 0.6f, hsb[2], v -> { float[] t = UI.toHsb(c.bgColor); c.bgColor = UI.hsb(t[0], t[1], v, 1) & 0xFFFFFF; }, ChatSettingsScreen::pct)
				.gradient(() -> { float[] t = UI.toHsb(c.bgColor); return new int[]{0xFF000000, UI.hsb(t[0], t[1], 0.6f, 1)}; }), rowStep() + 2);
		slider("Прозрачность (чат открыт)", 0, 1, c.bgOpacity, v -> c.bgOpacity = v, ChatSettingsScreen::pct);
		slider("Прозрачность (чат закрыт)", 0, 1, c.bgOpacityClosed, v -> c.bgOpacityClosed = v, ChatSettingsScreen::pct);
		slider("Скругление углов", 0, 12, c.radius, v -> c.radius = Math.round(v), v -> Math.round(v) + "");
		toggle("Тень под окном", () -> c.shadow, v -> c.shadow = v, null);
		gap();
		header("Картинка на фоне", "png / jpg из папки config/aurorachat/backgrounds");
		List<String> imgs = net.primlixe.aurorachat.ui.BgImage.files();
		if (!c.bgImage.isEmpty() && !imgs.contains(c.bgImage)) imgs.add(0, c.bgImage);
		String[] imgNames = new String[imgs.size() + 1];
		imgNames[0] = "Без картинки";
		for (int i = 0; i < imgs.size(); i++) imgNames[i + 1] = imgs.get(i);
		choice("Картинка", imgNames, () -> c.bgImage.isEmpty() ? 0 : imgs.indexOf(c.bgImage) + 1, i -> {
			c.bgImage = i == 0 ? "" : imgs.get(i - 1);
			net.primlixe.aurorachat.ui.BgImage.reload();
			rebuildWidgets();
		}, imgs.isEmpty() ? "Папка пуста — нажми «Открыть папку», положи туда картинку и вернись сюда" : "Видна в стиле фона «Панель»");
		if (!c.bgImage.isEmpty()) {
			slider("Видимость картинки", 0.05f, 1f, c.bgImageOpacity, v -> c.bgImageOpacity = v, ChatSettingsScreen::pct);
			ChatConfig.ImageMode[] im = ChatConfig.ImageMode.values();
			choice("Как разместить", Arrays.stream(im).map(x -> x.ru).toArray(String[]::new), () -> c.bgImageMode.ordinal(), i -> c.bgImageMode = im[i], null);
			if (c.bgStyle != BgStyle.PANEL) note("Картинка видна только при стиле фона «Панель».");
			add(new ResultLine(w(), () -> {
				String err = net.primlixe.aurorachat.ui.BgImage.error();
				return err == null ? "§aкартинка: " + c.bgImage : "§c" + c.bgImage + ": " + err;
			}), UI.rowH() + 4);
		}
		button("Открыть папку с картинками", Style.SECONDARY, p -> {
			try { java.nio.file.Files.createDirectories(net.primlixe.aurorachat.ui.BgImage.dir()); } catch (Exception ignored) { }
			net.minecraft.util.Util.getPlatform().openPath(net.primlixe.aurorachat.ui.BgImage.dir());
		}, "Положи туда картинку, потом выбери её выше (список обновится при возврате в раздел)");
		gap();
		header("Градиент фона", null);
		toggle("Градиент фона", () -> c.bgGradient, v -> c.bgGradient = v, "Сверху — второй цвет, снизу — цвет фона");
		swatches("Второй цвет", BG_COLORS, () -> c.bgColor2, v -> c.bgColor2 = v, false);
		float[] hsb2 = UI.toHsb(c.bgColor2);
		add(new Slider(0, 0, w(), "Оттенок второго цвета", 0, 1, hsb2[0], v -> { float[] t = UI.toHsb(c.bgColor2); c.bgColor2 = UI.hsb(v, Math.max(0.3f, t[1]), Math.max(0.15f, t[2]), 1) & 0xFFFFFF; }, v -> "").hue(), rowStep() + 2);
		gap();
		header("Акцент и обводка", null);
		swatches("Цвет акцента", ACCENTS, () -> c.accentColor, v -> c.accentColor = v, true);
		toggle("Обводка окна", () -> c.outline, v -> c.outline = v, "Тонкая рамка цвета акцента");
		slider("Яркость обводки", 0, 1, c.outlineOpacity, v -> c.outlineOpacity = v, ChatSettingsScreen::pct);
		gap();
		header("Текст", null);
		swatches("Цвет обычного текста", new int[]{0xFFFFFF, 0xE6E8F2, 0xD8D2C0, 0xBFD8FF, 0xC8F2D6, 0x202020}, () -> c.textColor, v -> c.textColor = v, false);
		slider("Непрозрачность текста", 0.3f, 1, c.textOpacity, v -> c.textOpacity = v, ChatSettingsScreen::pct);
		toggle("Тень у текста", () -> c.textShadow, v -> c.textShadow = v, null);
		button("Цвета по умолчанию", Style.SECONDARY, p -> {
			ChatConfig d = new ChatConfig();
			c.bgStyle = d.bgStyle; c.bgColor = d.bgColor; c.bgOpacity = d.bgOpacity; c.bgOpacityClosed = d.bgOpacityClosed;
			c.radius = d.radius; c.outline = d.outline; c.accentColor = d.accentColor; c.textShadow = d.textShadow;
			c.bgGradient = d.bgGradient; c.bgColor2 = d.bgColor2; c.outlineOpacity = d.outlineOpacity; c.shadow = d.shadow;
			c.textColor = d.textColor; c.textOpacity = d.textOpacity;
			ChatConfig.save();
			rebuildWidgets();
		}, null);
	}

	private void initAnim() {
		ChatConfig c = cfg();
		java.util.function.Function<Float, String> mul = v -> String.format(Locale.ROOT, "×%.1f", v);
		header("Общее", null);
		toggle("Анимации", () -> c.animations, v -> c.animations = v, "Главный выключатель всех анимаций чата");
		slider("Общая скорость", 0.3f, 3f, c.animSpeed, v -> c.animSpeed = v, mul);
		ChatConfig.Ease[] es = ChatConfig.Ease.values();
		choice("Сглаживание", Arrays.stream(es).map(x -> x.ru).toArray(String[]::new), () -> c.ease.ordinal(), i -> c.ease = es[i],
				"Плавная — быстро и мягко, Пружина — с лёгким отскоком, Мягкая — медленный старт и финиш");
		gap();
		header("Окно", null);
		toggle("Раскрытие окна", () -> c.openAnimation, v -> c.openAnimation = v, "Окно плавно вырастает при открытии чата");
		slider("Скорость раскрытия", 0.3f, 3f, c.openSpeed, v -> c.openSpeed = v, mul);
		slider("Скорость переключения вкладок", 0.3f, 3f, c.tabSpeed, v -> c.tabSpeed = v, mul);
		gap();
		header("Новые сообщения", null);
		toggle("Анимация появления", () -> c.slideIn, v -> c.slideIn = v, "Новое сообщение появляется плавно и мягко поднимает старые");
		ChatConfig.Appear[] ap = ChatConfig.Appear.values();
		choice("Как появляются", Arrays.stream(ap).map(x -> x.ru).toArray(String[]::new), () -> c.appear.ordinal(), i -> c.appear = ap[i], null);
		slider("Длительность появления", 0.05f, 1.5f, c.appearTime, v -> c.appearTime = v, v -> String.format(Locale.ROOT, "%.2f с", v));
		slider("Дальность выезда", 0, 60, c.slideDistance, v -> c.slideDistance = Math.round(v), v -> Math.round(v) + " px");
		slider("Скорость подъёма старых", 0.3f, 3f, c.pushSpeed, v -> c.pushSpeed = v, mul);
		gap();
		header("Прокрутка", null);
		toggle("Плавная прокрутка", () -> c.smoothScroll, v -> c.smoothScroll = v, null);
		slider("Гладкость прокрутки", 0, 1, c.scrollSmoothness, v -> c.scrollSmoothness = v, ChatSettingsScreen::pct);
		gap();
		header("Затухание", null);
		slider("Сообщения видны", 2, 120, c.fadeSeconds, v -> c.fadeSeconds = Math.round(v), v -> Math.round(v) + " с");
		slider("Длительность затухания", 0.1f, 5f, c.fadeOutTime, v -> c.fadeOutTime = v, v -> String.format(Locale.ROOT, "%.1f с", v));
		gap();
		header("Отправка", null);
		toggle("Плавная отправка", () -> c.sendAnim, v -> c.sendAnim = v, "Текст сразу летит на своё место в окне чата и встаёт туда без второй анимации");
		slider("Время полёта", 0.1f, 0.8f, c.sendAnimTime, v -> c.sendAnimTime = v, v -> String.format(Locale.ROOT, "%.2f с", v));
		gap();
		header("Панели и окна мода", null);
		toggle("Плавные панели", () -> c.popupAnim, v -> c.popupAnim = v, "Эмодзи, палитра, «отправить позже» раскрываются плавно");
		slider("Время раскрытия панелей", 0.05f, 0.6f, c.popupTime, v -> c.popupTime = v, v -> String.format(Locale.ROOT, "%.2f с", v));
		toggle("Плавная смена разделов настроек", () -> c.settingsAnim, v -> c.settingsAnim = v, null);
		gap();
		perfBlock(c);
		gap();
		button("Плавность по умолчанию", Style.SECONDARY, p -> {
			ChatConfig d = new ChatConfig();
			c.animations = d.animations; c.animSpeed = d.animSpeed; c.ease = d.ease; c.openAnimation = d.openAnimation; c.openSpeed = d.openSpeed;
			c.tabSpeed = d.tabSpeed; c.slideIn = d.slideIn; c.appear = d.appear; c.appearTime = d.appearTime; c.slideDistance = d.slideDistance;
			c.pushSpeed = d.pushSpeed; c.smoothScroll = d.smoothScroll; c.scrollSmoothness = d.scrollSmoothness; c.fadeSeconds = d.fadeSeconds; c.fadeOutTime = d.fadeOutTime;
			ChatConfig.save();
			rebuildWidgets();
		}, null);
	}

	private void perfBlock(ChatConfig c) {
		header("Оптимизация чата", "FPS сейчас: " + minecraft.getFps() + (net.primlixe.aurorachat.ui.Perf.lite() ? " · экономный режим" : ""));
		toggle("Экономить при низком FPS", () -> c.perfAuto, v -> c.perfAuto = v, "Когда FPS ниже порога, чат сам отключает анимации и тени, а потом включает обратно");
		slider("Порог FPS", 10, 120, c.perfFps, v -> c.perfFps = Math.round(v / 5f) * 5, v -> Math.round(v / 5f) * 5 + " FPS");
		toggle("Экономный режим всегда", () -> c.perfAlways, v -> c.perfAlways = v, "Без анимаций и теней — меньше всего нагрузки");
	}

	private void initMinecraft() {
		ChatConfig c = cfg();
		note("Плавность для самой игры — работает везде, не только в чате. Каждую можно выключить.");
		header("Экраны", "инвентарь, сундуки, пауза, настройки");
		toggle("Плавное открытие экранов", () -> c.mcScreenAnim, v -> c.mcScreenAnim = v, null);
		ChatConfig.ScreenAnim[] sa = ChatConfig.ScreenAnim.values();
		choice("Как появляются", Arrays.stream(sa).map(x -> x.ru).toArray(String[]::new), () -> c.mcScreenStyle.ordinal(), i -> c.mcScreenStyle = sa[i], null);
		slider("Длительность", 0.05f, 0.6f, c.mcScreenTime, v -> c.mcScreenTime = v, v -> String.format(Locale.ROOT, "%.2f с", v));
		toggle("И главное меню", () -> c.mcScreenTitle, v -> c.mcScreenTitle = v, null);
		gap();
		header("Интерфейс в игре", null);
		if (net.primlixe.aurorachat.compat.Compat.hotbarByOtherMod()) note("Плавный хотбар сейчас делает SmoothScroll (двойное сглаживание давало задержку рамки) — его настройки в Mod Menu");
		else toggle("Плавный выбор слота хотбара", () -> c.mcSmoothHotbar, v -> c.mcSmoothHotbar = v, "Рамка переезжает к слоту, а не прыгает");
		slider("Скорость рамки", 0.3f, 3f, c.mcHotbarSpeed, v -> c.mcHotbarSpeed = v, v -> String.format(Locale.ROOT, "×%.1f", v));
		toggle("Список игроков (Tab) выезжает", () -> c.mcTabAnim, v -> c.mcTabAnim = v, null);
		gap();
		perfBlock(c);
		gap();
		header("Оптимизация игры", null);
		String[][] known = {{"sodium", "Sodium"}, {"lithium", "Lithium"}, {"immediatelyfast", "ImmediatelyFast"}, {"moreculling", "More Culling"},
				{"dynamic_fps", "Dynamic FPS"}, {"krypton", "Krypton"}, {"ferritecore", "FerriteCore"}, {"smoothscroll", "Smooth Scroll"}};
		List<String> have = new ArrayList<>();
		for (String[] k : known) if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(k[0])) have.add(k[1]);
		note(have.isEmpty()
				? "Для FPS самой игры лучше всего подходят Sodium, Lithium, ImmediatelyFast и Dynamic FPS — Aurora Chat с ними совместим."
				: "У тебя уже стоят: " + String.join(", ", have) + ". Они отвечают за FPS игры — Aurora Chat их не дублирует, чтобы не было конфликтов.");
		button("Открыть настройки видео Minecraft", Style.SECONDARY,
				p -> net.primlixe.aurorachat.compat.Mc.setScreen(new net.minecraft.client.gui.screens.options.VideoSettingsScreen(this, minecraft, minecraft.options)), null);
	}

	private void initInput() {
		ChatConfig c = cfg();
		header("Строка ввода", null);
		toggle("Стильная строка ввода", () -> c.styledInput, v -> c.styledInput = v, "Строка ввода в цветах чата");
		toggle("Плашка G / L слева", () -> c.inputChannelChip, v -> c.inputChannelChip = v, "Канал всё равно переключается: Alt+G / Alt+L и в шапке чата");
		toggle("Поиск по чату", () -> c.searchBar, v -> c.searchBar = v, "Выбирается только мышью — стрелки ↑/↓ листают историю");
		toggle("Кнопка истории ☰", () -> c.historyButton, v -> c.historyButton = v, null);
		toggle("Панель эмодзи ☺", () -> c.emojiPicker, v -> c.emojiPicker = v, null);
		toggle("Эмодзи :коды:", () -> c.emojiShortcodes, v -> c.emojiShortcodes = v, ":heart: → ❤ при отправке");
		toggle("Кнопка «отправить позже» ⏱", () -> c.laterButton, v -> c.laterButton = v, null);
		toggle("Палитра цветов «&»", () -> c.colorPicker, v -> c.colorPicker = v, "Клик по цвету, стилю или градиенту вставляет код в строку");
		toggle("Ники по Tab", () -> c.nickComplete, v -> c.nickComplete = v, "Ste + Tab → Steve; ещё Tab — следующий ник, Shift+Tab — назад");
		toggle("Серая подсказка ника", () -> c.nickGhost, v -> c.nickGhost = v, "Пока пишешь ник, справа виден его конец");
		toggle("Счётчик символов", () -> c.charCounter, v -> c.charCounter = v, "Считает уже с цветами и градиентами — сколько уйдёт серверу");
		toggle("Предпросмотр цветов", () -> c.colorPreview, v -> c.colorPreview = v, "Над строкой ввода видно, как будет выглядеть сообщение с &-кодами");
		toggle("Меню по ПКМ", () -> c.copyMenu, v -> c.copyMenu = v, "Копировать, ответить, упомянуть, найти в истории");
		toggle("Черновики", () -> c.drafts, v -> c.drafts = v, "Недописанное сохраняется, даже после перезапуска");
		toggle("Шаблоны {xyz} {ник} {время}…", () -> c.templates, v -> c.templates = v,
				"{xyz} координаты · {x} {y} {z} · {ник} · {время} · {дата} · {сервер} · {мир} · {hp} · {пинг} · {fps}");
		gap();
		header("Быстрые фразы ⚡", "над строкой ввода: клик — отправить сразу, можно жать подряд");
		toggle("Панель быстрых фраз", () -> c.quickPhrases, v -> c.quickPhrases = v,
				"ЛКМ — отправить · Shift+ЛКМ — вставить в строку · ПКМ — удалить · Alt+1…9 — отправить №");
		for (int i = 0; i < c.quickList.size(); i++) {
			int idx = i;
			field("Фраза " + (i + 1) + (i < 9 ? " · Alt+" + (i + 1) : ""), c.quickList.get(i), "сообщение или /команда", s -> {
				if (idx < c.quickList.size() && !s.isBlank()) c.quickList.set(idx, s);
			});
			button("Удалить фразу " + (i + 1), Style.DANGER, p -> { c.quickList.remove(idx); ChatConfig.save(); rebuildWidgets(); }, null);
		}
		button("Добавить фразу", Style.PRIMARY, p -> {
			c.quickList.add("Привет всем!");
			ChatConfig.save();
			rebuildWidgets();
		}, "Или прямо в чате: напиши текст и нажми «＋» на панели");
		gap();
		header("История ↑ / ↓", null);
		toggle("Своя история ввода", () -> c.betterHistory, v -> c.betterHistory = v, "Без повторов, больше строк. Выкл — как в обычной игре");
		toggle("Поиск по началу", () -> c.historyPrefix, v -> c.historyPrefix = v, "Набери «/tp» и жми ↑ — листаются только команды на /tp");
		toggle("↑ + Enter — отправить и не закрывать", () -> c.historyRepeat, v -> c.historyRepeat = v,
				"Строка из истории отправляется, а чат остаётся открытым с тем же текстом — жми Enter ещё. Ctrl+Enter — так же для любого текста");
		toggle("Помнить после перезапуска", () -> c.persistInput, v -> { c.persistInput = v; if (v) net.primlixe.aurorachat.chat.InputHistory.save(); }, "Пароли (/login, /reg…) в файл не попадают");
		slider("Хранить строк", 50, 5000, c.inputHistorySize, v -> c.inputHistorySize = Math.round(v / 50f) * 50, v -> Math.round(v / 50f) * 50 + "");
		int n = net.primlixe.aurorachat.chat.InputHistory.list().size();
		button("Очистить историю ввода (" + n + ")", Style.DANGER, p -> {
			net.primlixe.aurorachat.chat.InputHistory.clear();
			p.setLabel("История ввода очищена");
		}, null);
	}

	private void initColors() {
		ChatConfig c = cfg();
		header("HEX-цвета и градиенты", null);
		toggle("Понимать HEX и градиенты", () -> c.hexColors, v -> c.hexColors = v, "Сервер должен поддерживать HEX-цвета (EssentialsX, CMI, ChatControl и др.)");
		ChatConfig.HexFormat[] fm = ChatConfig.HexFormat.values();
		choice("Формат для сервера", Arrays.stream(fm).map(x -> x.ru).toArray(String[]::new), () -> c.hexFormat.ordinal(), i -> c.hexFormat = fm[i],
				"&#RRGGBB — EssentialsX, CMI и большинство · &x&R&R… — Spigot/Bungee · <#RRGGBB> — MiniMessage");
		toggle("Предпросмотр над строкой ввода", () -> c.colorPreview, v -> c.colorPreview = v, null);
		gap();
		header("Писать градиентом", "всё, что пишешь, уходит выбранным градиентом");
		String[] gnames = new String[c.gradients.size() + 1];
		gnames[0] = "Выкл";
		for (int i = 0; i < c.gradients.size(); i++) gnames[i + 1] = c.gradients.get(i).name;
		choice("Градиент", gnames, () -> {
			for (int i = 0; i < c.gradients.size(); i++) if (c.gradients.get(i).name.equalsIgnoreCase(c.writeGradient)) return i + 1;
			return 0;
		}, i -> c.writeGradient = i == 0 ? "" : c.gradients.get(i - 1).name,
				"Быстро: ПКМ по градиенту в палитре «&» у строки ввода или /achat grad <название>");
		toggle("И в личных сообщениях", () -> c.writeGradientDm, v -> c.writeGradientDm = v, "/m Ник текст → текст градиентом");
		add(new PreviewLine(w(), () -> {
			String gc = net.primlixe.aurorachat.chat.AutoColor.writeGradient();
			return gc == null ? "Выбери градиент — тут будет пример" : gc + "Так будут выглядеть твои сообщения";
		}), UI.rowH() + 4);
		gap();
		note("&#F80 или &#FF8800 — цвет.   &{#FF0000-#0000FF}текст — градиент, цветов сколько угодно.   "
				+ "&{огонь}текст — сохранённый градиент.   Градиент идёт до &{/}, &r или следующего цвета; &l &o &n &m внутри работают. "
				+ "Длинное сообщение само ужимается до 256 символов.");
		gap();
		header("Проверить", null);
		Field test = field("Текст", "&{радуга}Привет всем! &#55FFFFа это HEX", "напиши текст с кодами", s -> {});
		add(new PreviewLine(w(), () -> test.getValue()), UI.rowH() + 4);
		gap();
		header("Сохранённые градиенты", "&{название}текст");
		for (int i = 0; i < c.gradients.size(); i++) {
			int idx = i;
			add(new GradientRow(w(), c.gradients.get(i), () -> { c.gradients.remove(idx); ChatConfig.save(); rebuildWidgets(); }), rowStep());
		}
		gap();
		header("Новый градиент", null);
		Field name = field("Название", "", "например: мой", s -> {});
		Field cols = field("Цвета", "", "#FF0000 #FFAA00 #FFFF00 — два и больше", s -> {});
		button("Сохранить градиент", Style.PRIMARY, p -> {
			String nm = name.getValue().trim();
			int[] parsed = ColorCodes.colors(cols.getValue());
			if (nm.isEmpty() || parsed == null) { p.setLabel("Нужны название и цвета, например #FF0000 #0000FF"); return; }
			c.gradients.removeIf(g -> g.name.equalsIgnoreCase(nm));
			ChatConfig.Gradient g = new ChatConfig.Gradient();
			g.name = nm;
			for (int v : parsed) g.colors.add(String.format(Locale.ROOT, "#%06X", v));
			c.gradients.add(g);
			ChatConfig.save();
			rebuildWidgets();
		}, null);
		button("Вернуть градиенты по умолчанию", Style.SECONDARY, p -> { c.gradients = ChatConfig.defaultGradients(); ChatConfig.save(); rebuildWidgets(); }, null);
	}

	private void initChannels() {
		ChatConfig c = cfg();
		header("Каналы", "как на серверах: G — глобальный, L — локальный");
		toggle("Каналы G / L", () -> c.channels, v -> c.channels = v, "Переключатель над чатом, вкладки G и L, значки у сообщений");
		choice("Писать в", new String[]{"Глобальный (G)", "Локальный (L)"}, () -> c.channel.equals("G") ? 0 : 1, i -> c.channel = i == 0 ? "G" : "L", "Быстро: Alt+G / Alt+L в открытом чате");
		toggle("Переключатель G / L в шапке", () -> c.headerChannels, v -> c.headerChannels = v, null);
		toggle("Плашка G / L в строке ввода", () -> c.inputChannelChip, v -> c.inputChannelChip = v, null);
		field("Префикс глобального чата", c.globalPrefix, "обычно «!»", s -> c.globalPrefix = s);
		field("Префикс локального чата", c.localPrefix, "обычно пусто", s -> c.localPrefix = s);
		gap();
		header("Вид", null);
		toggle("Фильтр каналов во «Все» (клик по «Все»)", () -> c.allFollowsChannel, v -> c.allFollowsChannel = v,
				"Клик по вкладке «Все»: только L → только G → оба. Это только то, что видно — куда пишешь, не меняется (G — только если сам ставишь «!»)");
		toggle("Значки G / L у сообщений", () -> c.channelBadges, v -> c.channelBadges = v, null);
		toggle("Подсветка строк G / L", () -> c.channelHighlight, v -> c.channelHighlight = v, "Яркая полоска слева и оттенок фона строки цветом канала — видно, даже когда чат быстро бежит");
		slider("Оттенок строк", 0f, 0.3f, c.channelTint, v -> c.channelTint = v, ChatSettingsScreen::pct);
		toggle("Убирать тег [G] / [L] из текста", () -> c.stripChannelTag, v -> c.stripChannelTag = v, "Тег заменяется аккуратным цветным значком");
		swatches("Цвет G", CH_COLORS, () -> c.globalColor, v -> c.globalColor = v, false);
		swatches("Цвет L", CH_COLORS, () -> c.localColor, v -> c.localColor = v, false);
		gap();
		header("Вид G / L", "метки у сообщений, в шапке и у вкладок");
		field("Надпись глобального", c.gLabel, "G, Глоб, ⚡ — до 6 символов", s -> c.gLabel = s.isBlank() ? "G" : s.strip().length() > 6 ? s.strip().substring(0, 6) : s.strip());
		field("Надпись локального", c.lLabel, "L, Лок, ☘ — до 6 символов", s -> c.lLabel = s.isBlank() ? "L" : s.strip().length() > 6 ? s.strip().substring(0, 6) : s.strip());
		ChatConfig.BadgeStyle[] bst = ChatConfig.BadgeStyle.values();
		choice("Как показывать у сообщений", Arrays.stream(bst).map(x -> x.ru).toArray(String[]::new), () -> c.badgeStyle.ordinal(), i -> { c.badgeStyle = bst[i]; rebuildWidgets(); }, null);
		if (c.badgeStyle == ChatConfig.BadgeStyle.LETTER) {
			ChatConfig.BadgeShape[] shp = ChatConfig.BadgeShape.values();
			choice("Форма метки", Arrays.stream(shp).map(x -> x.ru).toArray(String[]::new), () -> c.badgeShape.ordinal(), i -> c.badgeShape = shp[i], null);
			toggle("Цвет надписи — сам", () -> c.badgeTextColor < 0, v -> { c.badgeTextColor = v ? -1 : 0xFFFFFF; rebuildWidgets(); }, "Тёмная надпись на светлой метке, белая — на тёмной");
			if (c.badgeTextColor >= 0) swatches("Цвет надписи", new int[]{0xFFFFFF, 0x0B0C10}, () -> c.badgeTextColor, v -> c.badgeTextColor = v, false);
		}
		slider("Толщина полоски слева", 0, 6, c.channelStripe, v -> c.channelStripe = Math.round(v), v -> Math.round(v) == 0 ? "нет" : Math.round(v) + " px");
		slider("Текст сообщений цветом канала", 0f, 0.6f, c.channelTextTint, v -> c.channelTextTint = v, v -> v < 0.01f ? "выкл" : pct(v));
		button("Вид G / L по умолчанию", Style.SECONDARY, p -> {
			ChatConfig d = new ChatConfig();
			c.gLabel = d.gLabel; c.lLabel = d.lLabel; c.badgeStyle = d.badgeStyle; c.badgeShape = d.badgeShape; c.badgeTextColor = d.badgeTextColor;
			c.channelStripe = d.channelStripe; c.channelTextTint = d.channelTextTint; c.channelTint = d.channelTint; c.channelHighlight = d.channelHighlight;
			c.globalColor = d.globalColor; c.localColor = d.localColor;
			ChatConfig.save();
			rebuildWidgets();
		}, null);
		gap();
		header("Распознавание", "регулярные выражения по началу строки");
		field("Шаблон глобального", c.globalPattern, "regex", s -> c.globalPattern = s);
		field("Шаблон локального", c.localPattern, "regex", s -> c.localPattern = s);
		button("Шаблоны по умолчанию", Style.SECONDARY, p -> {
			ChatConfig d = new ChatConfig();
			c.globalPattern = d.globalPattern; c.localPattern = d.localPattern;
			ChatConfig.save();
			rebuildWidgets();
		}, null);
	}

	private void initMessages() {
		ChatConfig c = cfg();
		header("Время", null);
		toggle("Время у сообщений", () -> c.timestamps, v -> c.timestamps = v, null);
		choice("Формат", TIME_FORMATS, () -> Math.max(0, Arrays.asList(TIME_FORMATS).indexOf(c.timeFormat)), i -> c.timeFormat = TIME_FORMATS[i], null);
		swatches("Цвет времени", new int[]{0x8C8CA8, 0x6A6F88, 0xB18CFF, 0x6CB4FF, 0xFFFFFF}, () -> c.timestampColor, v -> c.timestampColor = v, false);
		toggle("Дата при наведении на время", () -> c.timestampHoverDate, v -> c.timestampHoverDate = v, null);
		gap();
		header("Сообщения", null);
		toggle("Склейка повторов ×N", () -> c.stacking, v -> c.stacking = v, null);
		toggle("Кликабельные ссылки", () -> c.clickableLinks, v -> c.clickableLinks = v, null);
		toggle("Предпросмотр ссылок", () -> c.linkPreview, v -> c.linkPreview = v,
				"Наведи на ссылку — домен, полный адрес и предупреждение, если похоже на обман (сокращатель, голый IP, «steam» в чужом домене, .exe)");
		toggle("Компактные входы/выходы", () -> c.compactJoins, v -> c.compactJoins = v, null);
		toggle("Скрыть входы/выходы", () -> c.hideJoins, v -> c.hideJoins = v, null);
		gap();
		header("Удобство", null);
		toggle("Кнопка «↩ ответить» у сообщения", () -> c.replyButton, v -> c.replyButton = v, "Появляется при наведении: ЛС — /m Ник, обычное — «Ник, »");
		toggle("Закреплённые сообщения", () -> c.pins, v -> c.pins = v, "ПКМ по сообщению → «Закрепить»; полоска сверху окна");
		gap();
		header("Переводчик", "Google Translate · текст уходит в сеть только при переводе");
		toggle("Переводчик в меню сообщения", () -> c.translate, v -> c.translate = v, "ПКМ по сообщению → «⇄ Перевести»: перевод появится второй строкой, ПКМ → «Показать оригинал» уберёт его");
		choice("Переводить на", TR_TO_LANGS, () -> Math.max(0, Arrays.asList(TR_TO_CODES).indexOf(c.translateTo)), i -> c.translateTo = TR_TO_CODES[i], "«Как в игре» — на язык, выбранный в настройках Minecraft");
		toggle("Автоперевод чужого языка", () -> c.translateAuto, v -> c.translateAuto = v,
				"Сообщения игроков на другой письменности переводятся сами (не больше 20 в минуту). Каждое такое сообщение отправляется в Google");
		choice("Свой текст переводить на", TR_LANGS, () -> Math.max(0, Arrays.asList(TR_CODES).indexOf(c.translateOut)), i -> c.translateOut = TR_CODES[i],
				"/achat tr текст — перевод попадёт в строку ввода, проверь и нажми Enter");
		toggle("Кнопка ⇄ у строки ввода", () -> c.translateButton, v -> c.translateButton = v,
				"Переводит набранное прямо в строке (ЛС «/m ник» и «!» сохраняются); ПКМ по кнопке — другой язык");
		gap();
		header("Свои имена для ников", "видны только тебе · ПКМ по сообщению → «Переименовать»");
		toggle("Показывать свои имена", () -> c.nicknamesEnabled, v -> c.nicknamesEnabled = v, "При наведении на имя виден настоящий ник");
		for (var en : new ArrayList<>(c.nicknames.entrySet())) {
			button("✕ " + en.getKey() + " → " + en.getValue(), Style.SECONDARY, p -> { c.nicknames.remove(en.getKey()); ChatConfig.save(); rebuildWidgets(); }, "Убрать своё имя");
		}
		Field nf = field("Добавить", "", "Steve Стёпа  (ник, пробел, имя)", s -> {});
		button("Сохранить имя", Style.PRIMARY, p -> {
			String v = nf.getValue().trim();
			int sp = v.indexOf(' ');
			if (sp > 0) {
				net.primlixe.aurorachat.chat.Nicknames.set(v.substring(0, sp), v.substring(sp + 1).trim());
				rebuildWidgets();
			} else p.setLabel("Пример: Steve Стёпа");
		}, null);
	}

	private void initWheel() {
		ChatConfig c = cfg();
		header("Круговое меню", "навёлся на игрока + G (клавиша меняется в «Управлении»)");
		toggle("Круговое меню действий", () -> c.playerWheel, v -> c.playerWheel = v, "Держи G и веди мышь к действию, или нажми G и кликни");
		slider("Затемнение экрана", 0f, 0.9f, c.wheelDim, v -> c.wheelDim = v, v -> Math.round(v * 100) + "%");
		slider("Дальность наведения", 3, 64, c.wheelRange, v -> c.wheelRange = Math.round(v), v -> Math.round(v) + " бл.");
		gap();
		header("Спецэффекты", "когда применяешь действие");
		toggle("Эффекты на экране", () -> c.wheelFx, v -> c.wheelFx = v, "Вспышка, волна, разлетающиеся значки и надпись «Обнять → Ник»");
		toggle("Частицы у игрока", () -> c.wheelFxWorld, v -> c.wheelFxWorld = v, "Сердечки/искры/ноты над игроком в мире — видишь только ты");
		toggle("Звук", () -> c.wheelFxSound, v -> c.wheelFxSound = v, "Короткий аккорд из трёх нот");
		gap();
		header("Действия", "{ник} — ник игрока · «?» перед командой — вставить в строку чата, не отправлять");
		ChatConfig.WheelFx[] fxs = ChatConfig.WheelFx.values();
		String[] fxNames = new String[fxs.length];
		for (int i = 0; i < fxs.length; i++) fxNames[i] = fxs[i].ru;
		for (int i = 0; i < c.wheelItems.size(); i++) {
			ChatConfig.WheelAction a = c.wheelItems.get(i);
			int idx = i;
			toggle((i + 1) + ". " + a.name, () -> a.enabled, v -> a.enabled = v, "Выкл — действие спрятано из круга, но не удалено");
			field("Название", a.name, "например: Обнять", s -> a.name = s.isBlank() ? "Действие" : s.trim());
			field("Команда", a.command, "/hug {ник}", s -> a.command = s);
			choice("Эффект", fxNames, () -> a.fx.ordinal(), k -> a.fx = fxs[k], null);
			if (i > 0) button("↑ Выше", Style.SECONDARY, p -> {
				java.util.Collections.swap(c.wheelItems, idx, idx - 1);
				ChatConfig.save();
				rebuildWidgets();
			}, null);
			button("Удалить «" + a.name + "»", Style.DANGER, p -> { c.wheelItems.remove(a); ChatConfig.save(); rebuildWidgets(); }, null);
			gap();
		}
		if (c.wheelItems.size() < 12) button("Добавить действие", Style.PRIMARY, p -> {
			c.wheelItems.add(new ChatConfig.WheelAction("Действие " + (c.wheelItems.size() + 1), "/", ChatConfig.WheelFx.STARS));
			ChatConfig.save();
			rebuildWidgets();
		}, "До 12 действий в круге");
		button("Вернуть по умолчанию (Обнять, Погладить)", Style.SECONDARY, p -> {
			c.wheelItems = ChatConfig.defaultWheelActions();
			ChatConfig.save();
			rebuildWidgets();
		}, null);
	}

	private void initTabs() {
		ChatConfig c = cfg();
		header("Вкладки", "Все · G · L · ЛС · Клан · Персонал · свои");
		toggle("Показывать вкладки", () -> c.tabs, v -> c.tabs = v, null);
		toggle("Счётчики непрочитанного", () -> c.unreadCounters, v -> c.unreadCounters = v, null);
		if (!PUB) toggle("Вкладка «Персонал» вместо «Система»", () -> c.staffTab, v -> c.staffTab = v, null);
		toggle("Пауза, пока мышь над чатом", () -> c.pauseOnHover, v -> c.pauseOnHover = v, "Выкл — чат останавливается, только когда отмотал вверх (новые копятся под «↓ N нов.»). Вкл — ещё и от наведения мыши");
		toggle("Фильтр каналов во «Все» (клик по «Все»)", () -> c.allFollowsChannel, v -> c.allFollowsChannel = v, "Клик по «Все»: L → G → оба. Куда пишешь — не меняется");
		gap();
		header("Клан", "вместо вкладки «@»");
		toggle("Вкладка «Клан» (выкл — вернуть «@»)", () -> c.clanTab, v -> { c.clanTab = v; rebuildWidgets(); }, null);
		toggle("Убирать клан из остального чата", () -> c.clanHide, v -> c.clanHide = v, null);
		field("Начало кланового сообщения", c.clanPrefix, "КЛАН:", s -> c.clanPrefix = s.isBlank() ? "КЛАН:" : s.trim());
		gap();
		header("Свои вкладки", "сообщения с любым из слов");
		for (int i = 0; i < c.customTabs.size(); i++) {
			ChatConfig.CustomTab t = c.customTabs.get(i);
			int idx = i;
			field("Вкладка " + (i + 1) + " — название", t.name, "название", s -> t.name = s.isBlank() ? "Вкладка" : s);
			field("Слова через запятую", String.join(", ", t.keywords), "например: продам, куплю", s -> t.keywords = split(s));
			button("Удалить вкладку " + (i + 1), Style.DANGER, p -> { c.customTabs.remove(t); ChatConfig.save(); rebuildWidgets(); }, null);
			gap();
		}
		button("Добавить вкладку", Style.PRIMARY, p -> {
			ChatConfig.CustomTab t = new ChatConfig.CustomTab();
			t.name = "Вкладка " + (c.customTabs.size() + 1);
			c.customTabs.add(t);
			ChatConfig.save();
			rebuildWidgets();
		}, null);
	}

	private void initNotify() {
		ChatConfig c = cfg();
		header("Упоминания", "звук и уведомление, когда пишут твой ник");
		toggle("Подсвечивать свой ник цветом", () -> c.highlightSelf, v -> c.highlightSelf = v, null);
		toggle("Упоминания", () -> c.mentions, v -> c.mentions = v, null);
		toggle("Звук упоминания", () -> c.mentionSound, v -> c.mentionSound = v, null);
		swatches("Цвет упоминания", new int[]{0xFFD24A, 0xFF7EC8, 0x6CB4FF, 0x5BE39A, 0xFF6B7A}, () -> c.mentionColor, v -> c.mentionColor = v, false);
		field("Дополнительные слова", String.join(", ", c.mentionWords), "через запятую", s -> c.mentionWords = split(s));
		gap();
		header("Уведомления", null);
		toggle("Карточки ЛС и упоминаний", () -> c.toasts, v -> c.toasts = v, "Всплывают справа сверху");
		toggle("Звук личных сообщений", () -> c.dmSound, v -> c.dmSound = v, null);
	}

	private void initHistory() {
		ChatConfig c = cfg();
		header("История чата", null);
		slider("Хранить сообщений", 100, 16384, c.historySize, v -> c.historySize = Math.round(v / 100f) * 100, v -> Math.round(v / 100f) * 100 + "");
		toggle("Не чистить при выходе с сервера", () -> c.keepOnDisconnect, v -> c.keepOnDisconnect = v, null);
		toggle("Помнить после перезапуска", () -> c.persistHistory, v -> c.persistHistory = v, null);
		slider("Сохранять последних", 0, 5000, c.persistLimit, v -> c.persistLimit = Math.round(v / 50f) * 50, v -> Math.round(v / 50f) * 50 + "");
		toggle("Разделитель при входе на сервер", () -> c.sessionSeparator, v -> c.sessionSeparator = v, null);
		gap();
		header("Журнал", "config/aurorachat/chatlogs");
		toggle("Писать журнал в файлы", () -> c.logging, v -> c.logging = v, null);
		toggle("Системные сообщения в журнал", () -> c.logSystem, v -> c.logSystem = v, null);
		button("Открыть историю (H)", Style.PRIMARY, p -> net.primlixe.aurorachat.compat.Mc.setScreen(new ChatLogScreen(this, "")), null);
		gap();
		header("Консоль", "клавиша Ё · /achat console");
		toggle("Записывать события", () -> c.consoleLog, v -> c.consoleLog = v, "Чат, команды, скрытое, входы, персонал");
		button("Открыть консоль", Style.SECONDARY, p -> net.primlixe.aurorachat.compat.Mc.setScreen(new ConsoleScreen(this)), null);
		slider("Строк в консоли", 500, 20000, c.consoleLimit, v -> c.consoleLimit = Math.round(v / 500f) * 500, v -> Math.round(v / 500f) * 500 + "");
		button("Папка журнала", Style.SECONDARY, p -> {
			try { java.nio.file.Files.createDirectories(ChatLog.root()); } catch (Exception ignored) { }
			net.minecraft.util.Util.getPlatform().openPath(ChatLog.root());
		}, null);
	}

	private void initFilter() {
		ChatConfig c = cfg();
		header("Фильтр", null);
		field("Игнорировать игроков", String.join(", ", c.ignored), "ники через запятую", s -> c.ignored = split(s));
		field("Скрывать сообщения, если найдено", String.join(", ", c.hidePatterns), "слова или regex через запятую", s -> c.hidePatterns = split(s));
	}

	private void initUiSettings() {
		ChatConfig c = cfg();
		header("Окно настроек", "как выглядит это окно");
		String[] langV = {"auto", "ru", "en"};
		choice("Язык / Language", new String[]{"Как в игре / Game", "Русский", "English"}, () -> Math.max(0, Arrays.asList(langV).indexOf(c.uiLanguage)),
				i -> { c.uiLanguage = langV[i]; ChatConfig.save(); rebuildWidgets(); }, null);
		if (PUB) toggle("Проверять обновления", () -> c.updateCheck, v -> c.updateCheck = v, "Раз за запуск спрашивает Modrinth, нет ли новой версии");
		slider("Размер", 0.6f, 1.25f, c.uiScale, v -> c.uiScale = Math.round(v * 20) / 20f, ChatSettingsScreen::pct)
				.tip("Применяется, когда отпустишь ползунок");
		fontChoice("Шрифт", () -> c.uiFont, v -> c.uiFont = v);
		toggle("Плотные строки", () -> c.uiCompact, v -> { c.uiCompact = v; rebuildWidgets(); }, null);
		toggle("Предпросмотр чата справа", () -> c.uiPreview, v -> { c.uiPreview = v; rebuildWidgets(); }, "Виден, когда окно достаточно широкое");
		slider("Затемнение игры", 0.3f, 1f, c.uiDim, v -> c.uiDim = v, ChatSettingsScreen::pct);
		gap();
		header("Цвет выделения", null);
		toggle("Как акцент чата", () -> c.uiFollowAccent, v -> c.uiFollowAccent = v, "Выкл — свой спокойный цвет ниже");
		swatches("Свой цвет", UI_ACCENTS, () -> c.uiAccent, v -> { c.uiAccent = v; c.uiTheme = "Своя"; }, false);
		gap();
		header("Темы окна настроек", "клик — применить, ниже можно подправить любой цвет");
		for (net.primlixe.aurorachat.config.UiThemes.Theme t : net.primlixe.aurorachat.config.UiThemes.ALL) add(new UiThemeCard(w(), t), 28);
		gap();
		header("Свои цвета окна", "тема станет «Своя»");
		swatches("Фон под окном", new int[]{0x08090C, 0x050816, 0x1E1F22, 0xF2F3F7}, () -> c.uiBg, v -> { c.uiBg = v; c.uiTheme = "Своя"; }, false);
		swatches("Панели", new int[]{0x0E1015, 0x0B1230, 0x2B2D31, 0xFFFFFF}, () -> c.uiPanel, v -> { c.uiPanel = v; c.uiTheme = "Своя"; }, false);
		slider("Непрозрачность панелей", 0.2f, 1f, c.uiPanelAlpha, v -> { c.uiPanelAlpha = v; c.uiTheme = "Своя"; }, ChatSettingsScreen::pct);
		swatches("Текст", new int[]{0xF2F3FA, 0xFFFFFF, 0x1B1E28}, () -> c.uiText, v -> { c.uiText = v; c.uiTheme = "Своя"; }, false);
		swatches("Подписи", new int[]{0x9EA3BC, 0xB5BAC1, 0x4A5068}, () -> c.uiSub, v -> { c.uiSub = v; c.uiTheme = "Своя"; }, false);
		swatches("Блёклый текст", new int[]{0x6A6F88, 0x80848E, 0x8A90A6}, () -> c.uiMuted, v -> { c.uiMuted = v; c.uiTheme = "Своя"; }, false);
		toggle("Светлая тема (тёмные линии и строки)", () -> c.uiInk == 0x000000, v -> { c.uiInk = v ? 0x000000 : 0xFFFFFF; c.uiTheme = "Своя"; },
				"Включи, если панели светлые — иначе строки и рамки будут не видны");
		slider("Скругление панелей", 0, 10, c.uiRadius, v -> { c.uiRadius = Math.round(v); c.uiTheme = "Своя"; }, v -> Math.round(v) + "");
		gap();
		button("Вид окна по умолчанию", Style.SECONDARY, p -> {
			ChatConfig d = new ChatConfig();
			c.uiScale = d.uiScale; c.uiFont = d.uiFont; c.uiCompact = d.uiCompact; c.uiFollowAccent = d.uiFollowAccent;
			c.uiAccent = d.uiAccent; c.uiDim = d.uiDim; c.uiPreview = d.uiPreview;
			c.uiTheme = d.uiTheme; c.uiBg = d.uiBg; c.uiPanel = d.uiPanel; c.uiPanelAlpha = d.uiPanelAlpha;
			c.uiText = d.uiText; c.uiSub = d.uiSub; c.uiMuted = d.uiMuted; c.uiInk = d.uiInk; c.uiRadius = d.uiRadius;
			ChatConfig.save();
			rebuildWidgets();
		}, null);
	}

	// ====================================================================== отрисовка и ввод в масштабе

	private void layoutScroll() {
		for (AbstractWidget wd : scrollables) {
			int y = baseY.get(wd) - Math.round(smooth);
			wd.setY(y);
			int h = wd instanceof Field f ? f.boxH() : wd.getHeight();
			wd.visible = y + h >= conY0 - 2 && y <= conY1 + 2;
		}
	}

	private static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		UI.round(g, x0, y0, x1, y1, UI.RADIUS, UI.PANEL);
		UI.roundOutline(g, x0, y0, x1, y1, UI.RADIUS, UI.ink(0x16));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
		var win = minecraft.getWindow();
		g.fill(0, 0, win.getGuiScaledWidth(), win.getGuiScaledHeight(), UI.rgba(cfg().uiBg, cfg().uiDim * intro()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int rmx, int rmy, float partial) {
		if (Math.abs(cfg().uiScale - sc) > 0.001f && !draggingSlider) { rebuildWidgets(); }
		int mx = Math.round(rmx / sc), my = Math.round(rmy / sc);
		long t = System.nanoTime();
		float dt = Math.min(0.1f, (t - lastFrame) / 1e9f);
		lastFrame = t;
		smooth = UI.damp(smooth, scroll, 16, dt);
		layoutScroll();
		float in = intro();

		UI.beginSettings();
		g.pose().pushMatrix();
		try {
			g.pose().scale(sc, sc);
			g.pose().translate(0, (1 - in) * 6);

			UI.text(g, font, "Aurora Chat", navX0 + 2, 12, UI.TEXT, false);
			String sub = net.primlixe.aurorachat.ui.I18n.t("настройки · всё сохраняется сразу");
			int sx = navX0 + 2 + UI.width(font, "Aurora Chat") + 8;
			if (sx + UI.width(font, sub) < width - 40) UI.text(g, font, sub, sx, 12, UI.MUTED, false);
			g.fill(0, 30, width, 31, UI.ink(0x14));

			panel(g, navX0, 36, navX1, height - 8);
			panel(g, conX0 - 5, 36, conX1 + 5, height - 6);
			UI.text(g, font, net.primlixe.aurorachat.ui.I18n.t(section.ru), conX0, 42, UI.TEXT, false);
			g.fill(conX0, 52, conX0 + UI.width(font, net.primlixe.aurorachat.ui.I18n.t(section.ru)), 53, UI.alpha(UI.accent(), 0.8f));

			if (showPreview) {
				panel(g, prevX0, 36, prevX1, height - 8);
				UI.text(g, font, net.primlixe.aurorachat.ui.I18n.t("ПРЕДПРОСМОТР"), prevX0 + 7, 42, UI.MUTED, false);
				UI.endSettings();
				ChatHud.renderPreview(g, font, prevX0 + 4, 54, prevX1 - 4, height - 12);
				UI.beginSettings();
			}

			for (AbstractWidget wd : fixed) wd.extractRenderState(g, mx, my, partial);
			g.enableScissor(conX0 - 5, conY0 - 2, conX1 + 5, conY1);
			float se = cfg().settingsAnim ? net.primlixe.aurorachat.ui.Perf.appear(sectionAt, 0.18f) : 1;
			if (se < 1) { g.pose().pushMatrix(); g.pose().translate((1 - se) * 10, 0); }
			for (AbstractWidget wd : scrollables) if (wd.visible) wd.extractRenderState(g, mx, my, partial);
			if (se < 1) g.pose().popMatrix();
			g.disableScissor();
			if (maxScroll > 0) {
				int top = conY0, h = conY1 - top;
				int bar = Math.max(16, (int) (h * h / (h + maxScroll)));
				int y = top + (int) ((h - bar) * (smooth / maxScroll));
				g.fill(conX1 + 2, y, conX1 + 4, y + bar, UI.alpha(UI.accent(), 0.6f));
			}
			// подсказку рисуем сами — внутри того же масштаба
			GuiGraphicsAccessor acc = (GuiGraphicsAccessor) g;
			Runnable tip = acc.aurorachat$tooltip();
			if (tip != null) {
				acc.aurorachat$setTooltip(null);
				g.nextStratum();
				tip.run();
			}
		} finally {
			g.pose().popMatrix();
			UI.endSettings();
		}
	}

	private boolean draggingSlider;
	private long sectionAt;

	private MouseButtonEvent scaled(MouseButtonEvent e) { return new MouseButtonEvent(e.x() / sc, e.y() / sc, e.buttonInfo()); }

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		MouseButtonEvent s = scaled(e);
		boolean r = super.mouseClicked(s, dbl);
		draggingSlider = getFocused() instanceof Slider;
		return r;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		boolean r = super.mouseReleased(scaled(e));
		draggingSlider = false;
		return r;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		return super.mouseDragged(scaled(e), dx / sc, dy / sc);
	}

	@Override
	public void mouseMoved(double mx, double my) { super.mouseMoved(mx / sc, my / sc); }

	@Override
	public boolean mouseScrolled(double rmx, double rmy, double sx, double sy) {
		double mx = rmx / sc, my = rmy / sc;
		if (mx >= conX0 - 6 && mx < conX1 + 6 && maxScroll > 0) {
			scroll = (float) Math.max(0, Math.min(maxScroll, scroll - sy * 24));
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public void removed() { ChatConfig.flush(); }

	@Override
	public void onClose() {
		ChatConfig.flush();
		net.primlixe.aurorachat.compat.Mc.setScreen(parent);
	}

	// ====================================================================== свои виджеты

	/** Пояснение в несколько строк. */
	private final class Note extends AbstractWidget {
		private final List<FormattedCharSequence> lines;

		Note(int w, String text) {
			super(0, 0, w, 10, Component.literal(text));
			this.lines = font.split(UI.lit(net.primlixe.aurorachat.ui.I18n.t(text)), w - 8);
			this.height = lines.size() * 10 + 4;
			this.active = false;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
			for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), getX() + 2, getY() + 2 + i * 10, UI.SUB, false);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
	}

	/** Как будет выглядеть текст с кодами + сколько символов уйдёт серверу. */
	private final class PreviewLine extends AbstractWidget {
		private final Supplier<String> text;
		private String lastV;
		private ChatConfig.HexFormat lastFmt;
		private int outLen;
		private Component shown = Component.empty();

		PreviewLine(int w, Supplier<String> text) {
			super(0, 0, w, UI.rowH() + 2, Component.empty());
			this.text = text;
			this.active = false;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
			int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
			g.fill(x0, y0, x1, y1, 0x50000000);
			String v = text.get();
			if (!v.equals(lastV) || cfg().hexFormat != lastFmt) { // пересчёт градиента — только когда текст поменялся
				lastV = v;
				lastFmt = cfg().hexFormat;
				outLen = ColorCodes.expand(v, lastFmt).length();
				shown = ColorCodes.preview(v);
			}
			String len = outLen + "/256";
			int lw = UI.width(font, len);
			g.enableScissor(x0 + 4, y0, x1 - lw - 10, y1);
			g.text(font, shown, x0 + 5, y0 + (height - 8) / 2, 0xFFFFFFFF, true);
			g.disableScissor();
			UI.text(g, font, len, x1 - lw - 5, y0 + (height - 8) / 2, outLen > 256 ? UI.BAD : UI.MUTED, false);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
	}

	/** Строка с результатом (текст с §-цветами). */
	private final class ResultLine extends AbstractWidget {
		private final Supplier<String> text;
		private String cached;
		private long cachedAt;

		ResultLine(int w, Supplier<String> text) {
			super(0, 0, w, UI.rowH() + 2, Component.empty());
			this.text = text;
			this.active = false;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
			int x0 = getX(), y0 = getY();
			g.fill(x0, y0, x0 + width, y0 + height, 0x50000000);
			long now = System.currentTimeMillis();
			if (cached == null || now - cachedAt > 250) { cached = text.get(); cachedAt = now; } // проверка правил — 4 раза в секунду, не каждый кадр
			String t = cached;
			UI.text(g, font, UI.trim(font, t.isEmpty() ? net.primlixe.aurorachat.ui.I18n.t("§8результат появится здесь") : t, width - 10), x0 + 5, y0 + (height - 8) / 2, 0xFFFFFFFF, false);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
	}

	/** Сохранённый градиент: название, полоска цветов, удалить. */
	private final class GradientRow extends AbstractWidget {
		private final ChatConfig.Gradient gr;
		private final Runnable remove;
		private float hover;
		private int[] cols;
		private Component name;

		GradientRow(int w, ChatConfig.Gradient gr, Runnable remove) {
			super(0, 0, w, UI.rowH(), Component.literal(gr.name));
			this.gr = gr;
			this.remove = remove;
			setTooltip(UI.tip("&{" + gr.name + "}текст · " + String.join(" ", gr.colors) + " · ✕ — удалить"));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
			hover = UI.approach(hover, isHovered() ? 1 : 0, 0.3f);
			int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
			UI.row(g, x0, y0, x1, y1, hover);
			if (name == null) { // разбор цветов и подписи — один раз, а не каждый кадр
				cols = ColorCodes.colors(String.join(">", gr.colors));
				name = ColorCodes.preview("&{" + gr.name + "}" + gr.name);
			}
			g.text(font, name, x0 + 6, y0 + (height - 8) / 2, 0xFFFFFFFF, true);
			int bx0 = x0 + Math.max(90, width / 3), bx1 = x1 - 22;
			if (cols != null && bx1 > bx0) {
				// полоска кусками по 4 px (раньше — по пикселю: сотни заливок на каждую строку)
				for (int x = bx0; x < bx1; x += 4) g.fill(x, y0 + 5, Math.min(x + 4, bx1), y1 - 5, 0xFF000000 | ColorCodes.at(cols, (x + 2 - bx0) / (float) (bx1 - bx0)));
			}
			boolean xh = mx >= x1 - 18 && mx < x1 && my >= y0 && my < y1;
			UI.centered(g, font, "✕", x1 - 9, y0 + (height - 8) / 2, xh ? UI.BAD : UI.MUTED);
		}

		@Override
		public void onClick(MouseButtonEvent e, boolean dbl) {
			if (e.x() >= getX() + width - 18) remove.run();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
	}

	/** Карточка темы: мини-окно в её цветах, название, описание. */
	private final class ThemeCard extends AbstractWidget {
		private final ChatThemes.Theme t;
		private float hover;

		ThemeCard(int w, ChatThemes.Theme t) {
			super(0, 0, w, 26, Component.literal(t.name()));
			this.t = t;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
			hover = UI.approach(hover, isHovered() ? 1 : 0, 0.3f);
			boolean sel = t.name().equals(cfg().theme);
			int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
			UI.row(g, x0, y0, x1, y1, Math.max(hover, sel ? 0.6f : 0));
			int acc = t.accent() < 0 ? UI.hsb(0.72f + 0.12f * (float) Math.sin(UI.time() * 0.8f), 0.62f, 1f, 1f) : 0xFF000000 | t.accent();
			int px = x0 + 5, py = y0 + 4;
			UI.roundGradient(g, px, py, px + 38, py + 18, 2, 0xFF000000 | t.bg2(), 0xFF000000 | t.bg());
			UI.roundOutline(g, px, py, px + 38, py + 18, 2, UI.alpha(acc, 0.8f));
			g.fill(px + 4, py + 5, px + 26, py + 6, 0xC0FFFFFF);
			g.fill(px + 4, py + 9, px + 32, py + 10, 0x90FFFFFF);
			g.fill(px + 4, py + 13, px + 20, py + 14, UI.alpha(acc, 0.9f));
			UI.text(g, font, (sel ? "✔ " : "") + net.primlixe.aurorachat.ui.I18n.t(t.name()), x0 + 50, y0 + 4, sel ? UI.TEXT : UI.mix(UI.SUB, UI.TEXT, hover), false);
			UI.text(g, font, UI.trim(font, net.primlixe.aurorachat.ui.I18n.t(t.desc()), width - 56), x0 + 50, y0 + 15, UI.MUTED, false);
		}

		@Override
		public void onClick(MouseButtonEvent e, boolean dbl) {
			ChatThemes.apply(t);
			AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
	}

	/** Карточка темы окна настроек: мини-окно в её цветах, название, описание. */
	private final class UiThemeCard extends AbstractWidget {
		private final net.primlixe.aurorachat.config.UiThemes.Theme t;
		private float hover;

		UiThemeCard(int w, net.primlixe.aurorachat.config.UiThemes.Theme t) {
			super(0, 0, w, 26, Component.literal(t.name()));
			this.t = t;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
			hover = UI.approach(hover, isHovered() ? 1 : 0, 0.3f);
			boolean sel = t.name().equals(cfg().uiTheme);
			int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
			UI.row(g, x0, y0, x1, y1, Math.max(hover, sel ? 0.6f : 0));
			// мини-окно: фон, панель, строки текста её цветами, полоска акцента
			int px = x0 + 5, py = y0 + 4;
			UI.round(g, px, py, px + 38, py + 18, Math.min(4, t.radius()), 0xFF000000 | t.bg());
			UI.round(g, px + 3, py + 3, px + 35, py + 15, Math.min(3, t.radius()), UI.rgba(t.panel(), Math.max(0.6f, t.panelAlpha())));
			g.fill(px + 6, py + 6, px + 26, py + 7, 0xFF000000 | t.text());
			g.fill(px + 6, py + 9, px + 30, py + 10, 0xFF000000 | t.sub());
			g.fill(px + 6, py + 12, px + 18, py + 13, 0xFF000000 | t.accent());
			UI.text(g, font, (sel ? "✔ " : "") + net.primlixe.aurorachat.ui.I18n.t(t.name()) + (t.light() ? "  ☀" : ""), x0 + 50, y0 + 4, sel ? UI.TEXT : UI.mix(UI.SUB, UI.TEXT, hover), false);
			UI.text(g, font, UI.trim(font, net.primlixe.aurorachat.ui.I18n.t(t.desc()), width - 56), x0 + 50, y0 + 15, UI.MUTED, false);
		}

		@Override
		public void onClick(MouseButtonEvent e, boolean dbl) {
			net.primlixe.aurorachat.config.UiThemes.apply(t);
			AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
			rebuildWidgets();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
	}

	/** Пункт навигации слева. */
	private final class NavItem extends AbstractWidget {
		private final Section s;
		private float hover;

		NavItem(int x, int y, int w, int h, Section s) {
			super(x, y, w, h, Component.literal(s.ru));
			this.s = s;
			if (compact) setTooltip(UI.tip(s.ru));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
			boolean sel = s == section;
			hover = UI.approach(hover, isHoveredOrFocused() ? 1 : 0, 0.3f);
			int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
			if (sel) g.fill(x0, y0, x1, y1, UI.ink(0x22));
			else if (hover > 0.02f) g.fill(x0, y0, x1, y1, UI.alpha(UI.ink(0x14), hover));
			if (sel) g.fill(x0, y0 + 2, x0 + 2, y1 - 2, UI.accent());
			int col = sel ? UI.TEXT : UI.mix(UI.SUB, UI.TEXT, hover);
			int ty = y0 + (height - 8) / 2;
			String sr = net.primlixe.aurorachat.ui.I18n.t(s.ru);
			if (compact) UI.centered(g, font, sr.substring(0, 1), x0 + width / 2, ty, col);
			else UI.text(g, font, UI.trim(font, sr, width - 12), x0 + 7, ty, col, false);
		}

		@Override
		public void onClick(MouseButtonEvent e, boolean dbl) {
			switchSection(s);
			AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
	}
}
