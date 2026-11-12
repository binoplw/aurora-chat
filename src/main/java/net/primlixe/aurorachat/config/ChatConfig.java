package net.primlixe.aurorachat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.primlixe.aurorachat.AuroraChatMod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Все настройки Aurora Chat: config/aurorachat.json. */
public final class ChatConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ChatConfig instance = new ChatConfig();

	public static ChatConfig get() { return instance; }

	private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("aurorachat.json"); }

	public enum BgStyle {
		PANEL("Панель"), BUBBLES("Пузыри"), MINIMAL("Без фона");
		public final String ru;
		BgStyle(String ru) { this.ru = ru; }
	}

	public enum Appear {
		SLIDE("Выезд слева"), RISE("Всплытие снизу"), FADE("Проявление"), POP("Увеличение"), NONE("Сразу");
		public final String ru;
		Appear(String ru) { this.ru = ru; }
	}

	public enum Ease {
		SMOOTH("Плавная"), SPRING("Пружина"), SOFT("Мягкая"), LINEAR("Линейная");
		public final String ru;
		Ease(String ru) { this.ru = ru; }
	}

	public enum TabStyle {
		PILLS("Плашки"), UNDERLINE("Подчёркивание"), TEXT("Только текст");
		public final String ru;
		TabStyle(String ru) { this.ru = ru; }
	}

	public enum BadgeStyle {
		LETTER("Значок G/L"), TAG("Текст [G]"), BAR("Цвет полоски");
		public final String ru;
		BadgeStyle(String ru) { this.ru = ru; }
	}

	public enum Layout {
		COMPACT("Компактная"), COZY("Как в Discord"), MESSENGER("Мессенджер");
		public final String ru;
		Layout(String ru) { this.ru = ru; }
	}

	public enum TimePos {
		RIGHT("Справа"), LEFT("Слева");
		public final String ru;
		TimePos(String ru) { this.ru = ru; }
	}

	public boolean enabled = true;
	/** Название выбранной темы (после ручных правок — «Своя»). */
	public String theme = "Aurora";
	/** Свои сохранённые темы: название + код темы (см. ThemeCodec). */
	public List<SavedTheme> myThemes = new ArrayList<>();

	public static final class SavedTheme {
		public String name = "Моя тема";
		public String code = "";
	}
	/** Свой чат вместо ванильного (окно, фон, анимации). Выкл — остаются только улучшения текста. */
	public boolean customHud = true;

	// ===== окно =====
	public int winLeft = 4;
	/** Расстояние от низа экрана до низа окна (в пикселях интерфейса). */
	public int winBottom = 40;
	public int winW = 330;
	public int winH = 180;
	/** Какая часть высоты видна, когда чат закрыт (0.2..1). */
	public float closedHeight = 0.55f;
	public float textScale = 1f;
	public int lineSpacing = 1;
	public boolean locked = false;
	/** Свёрнутое окно: в открытом чате видно только пару строк. */
	public boolean collapsed = false;

	// ===== фон =====
	public BgStyle bgStyle = BgStyle.PANEL;
	public int bgColor = 0x0B0C16;
	/** Прозрачность фона в открытом чате и когда чат закрыт. */
	public float bgOpacity = 0.78f;
	public float bgOpacityClosed = 0.35f;
	public int radius = 7;
	public boolean outline = true;
	/** -1 — переливающийся «сияние», иначе RGB. */
	public int accentColor = -1;
	public boolean textShadow = true;
	/** Градиент фона: сверху bgColor2, снизу bgColor. */
	public boolean bgGradient = false;
	public int bgColor2 = 0x1E0F2E;
	/** Картинка на фоне окна: имя файла из config/aurorachat/backgrounds (png/jpg); пусто — без картинки. */
	public String bgImage = "";
	public float bgImageOpacity = 0.45f;
	public ImageMode bgImageMode = ImageMode.COVER;

	public enum ImageMode {
		COVER("Заполнить (обрезать)"), STRETCH("Растянуть"), TILE("Плиткой");
		public final String ru;
		ImageMode(String ru) { this.ru = ru; }
	}
	public float outlineOpacity = 0.4f;
	public boolean shadow = true;
	/** Цвет обычного (неокрашенного) текста и его непрозрачность. */
	public int textColor = 0xFFFFFF;
	public float textOpacity = 1f;

	// ===== вид сообщений =====
	/** Раскладка: компактная строка / ник над сообщением (Discord) / пузыри, свои справа (мессенджер). */
	public Layout layout = Layout.COMPACT;
	public boolean stripes = true;
	public int stripeWidth = 2;
	public boolean zebra = false;
	public boolean separators = false;
	public int rowPadding = 1;
	public int messageGap = 1;
	public int bubbleRadius = 5;
	public int headSize = 8;
	public boolean icons = true;
	public boolean boldNames = false;
	public TimePos timePos = TimePos.RIGHT;
	public BadgeStyle badgeStyle = BadgeStyle.LETTER;
	public TabStyle tabStyle = TabStyle.PILLS;
	public boolean unreadCounters = true;
	public boolean hoverHighlight = true;

	// ===== анимации и плавность =====
	public boolean animations = true;
	/** Общий множитель скорости всех анимаций. */
	public float animSpeed = 1f;
	public boolean slideIn = true;
	public boolean smoothScroll = true;
	public boolean openAnimation = true;
	public float fadeSeconds = 10f;
	public Appear appear = Appear.SLIDE;
	public Ease ease = Ease.SMOOTH;
	/** Скорость раскрытия окна при открытии чата. */
	public float openSpeed = 1f;
	/** Время появления нового сообщения, с. */
	public float appearTime = 0.3f;
	/** Насколько далеко выезжает/всплывает новое сообщение, px. */
	public float slideDistance = 14f;
	/** Плавность прокрутки: 0 — мгновенно, 1 — очень мягко. */
	public float scrollSmoothness = 0.55f;
	/** Скорость, с которой старые сообщения уступают место новым. */
	public float pushSpeed = 1f;
	/** Длительность затухания старых сообщений, с. */
	public float fadeOutTime = 1f;
	/** Скорость переезда выделения вкладки. */
	public float tabSpeed = 1f;

	// ===== каналы G / L =====
	public boolean channels = true;
	/** Текущий канал для отправки: "G" или "L". */
	public String channel = "L";
	public String globalPrefix = "!";
	public String localPrefix = "";
	/** Регулярки для распознавания канала входящих сообщений (по началу строки). */
	public String globalPattern = "^\\s*(\\[\\s*(G|Г|ɢ|Global|Глобал\\S*)\\s*]|\\((G|Г|ɢ)\\)|Ⓖ|G\\s*[|»])";
	public String localPattern = "^\\s*(\\[\\s*(L|Л|ʟ|Local|Локал\\S*)\\s*]|\\((L|Л|ʟ)\\)|Ⓛ|L\\s*[|»])";
	public boolean channelBadges = true;
	/** Убирать тег «[G]»/«[L]» из текста, если показан значок. */
	public boolean stripChannelTag = true;
	public int globalColor = 0xFFA21F;
	public int localColor = 0x3EF06A;
	/** Вкладка «Все» показывает только канал, в который пишешь (G или L); система и ЛС — всегда. */
	public boolean allFollowsChannel = true;
	/** Старое (до 2.23.3): «Все» показывает оба канала. Заменено allView. */
	public boolean allBoth = false;
	/** Что показывает «Все»: «L», «G» или «GL» (оба). Клик по «Все» меняет только это — НЕ канал отправки. */
	public String allView = "GL";
	/** Разово: «Все» отвязана от канала отправки; отправка возвращена в L (пользователь не просил писать в G). */
	public boolean viewSplitV1 = false;
	/** Строки G/L: полоска и оттенок фона цветом канала. */
	public boolean channelHighlight = true;
	/** Насколько заметен оттенок строки, 0…0,3. */
	public float channelTint = 0.10f;
	/** Надписи на метках каналов (вместо «G» и «L»): «Глоб», «Лок», «⚡»… до 6 символов. */
	public String gLabel = "G";
	public String lLabel = "L";
	/** Форма метки канала. */
	public BadgeShape badgeShape = BadgeShape.PILL;
	/** Цвет надписи на метке; -1 — сам (тёмный на светлом, белый на тёмном). */
	public int badgeTextColor = -1;
	/** Толщина цветной полоски слева у строк G/L, px (0 — без полоски). */
	public int channelStripe = 3;
	/** Подкрашивать сам текст сообщений цветом канала, 0…0,6. */
	public float channelTextTint = 0f;

	public enum BadgeShape {
		PILL("Пилюля"), SQUARE("Квадрат"), CIRCLE("Круг"), OUTLINE("Контур");
		public final String ru;
		BadgeShape(String ru) { this.ru = ru; }
	}
	/** Разово: старые бледные цвета G/L по умолчанию → насыщенные. */
	public boolean chVividV1 = false;

	// ===== сообщения =====
	public boolean timestamps = true;
	public String timeFormat = "HH:mm";
	public int timestampColor = 0x8C8CA8;
	public boolean timestampHoverDate = true;
	public boolean stacking = true;
	public boolean heads = true;
	public boolean colorNames = true;
	public boolean clickableLinks = true;
	/** Наведение на ссылку: адрес, домен и предупреждение, если ссылка подозрительная. */
	public boolean linkPreview = true;
	/** Переводчик: ПКМ по сообщению → «Перевести» (текст уходит в Google Translate только по клику). */
	public boolean translate = true;
	/** Язык перевода (код ISO: ru, en, uk, de…). */
	public String translateTo = "auto";
	/** Сам переводить сообщения игроков на чужом языке (по умолчанию выкл — каждое такое сообщение уходит в сеть). */
	public boolean translateAuto = false;
	/** Язык для «перевести и отправить» (/achat tr, кнопка в меню ввода). */
	public String translateOut = "en";
	/** Язык интерфейса мода: auto (как в игре), ru, en. */
	public String uiLanguage = "auto";
	/** Проверять обновления на Modrinth (публичная сборка; раз за запуск, в фоне). */
	public boolean updateCheck = true;
	/** Кнопка ⇄ у строки ввода: перевести набранное. */
	public boolean translateButton = true;
	public boolean compactJoins = true;
	public boolean hideJoins = false;
	public boolean emojiShortcodes = true;
	public boolean emojiPicker = true;
	public boolean charCounter = true;

	// ===== вкладки =====
	public boolean tabs = true;
	/** Вкладка «Клан» вместо «@»: сообщения, начинающиеся с clanPrefix. */
	public boolean clanTab = true;
	public String clanPrefix = "КЛАН:";
	/** Убирать клановые сообщения из остальных вкладок и свёрнутого чата. */
	public boolean clanHide = true;
	/** Отдельное окно «Клан» создано один раз (дальше пользователь может удалить его сам). */
	public boolean clanWinV1 = false;
	/** Шаблон «п.?» заменён правилами Mineblaze 4.x. */
	public boolean mbRulesV1 = false;
	/** В шаблоны G/L добавлены «маленькие» буквы Mineblaze: [ɢ] и [ʟ]. */
	public boolean smallCapsV1 = false;
	/** Подсвечивать свой ник цветом (упоминания и имя в своих сообщениях). */
	public boolean highlightSelf = false;
	public List<CustomTab> customTabs = new ArrayList<>();

	// ===== упоминания и уведомления =====
	public boolean mentions = true;
	public boolean mentionSound = true;
	public int mentionColor = 0xFFD24A;
	public List<String> mentionWords = new ArrayList<>();
	public boolean toasts = true;
	public boolean dmSound = true;

	// ===== история =====
	public int historySize = 2000;
	public boolean keepOnDisconnect = true;
	public boolean persistHistory = true;
	public int persistLimit = 500;
	public boolean sessionSeparator = true;
	public boolean logging = true;
	public boolean logSystem = true;

	// ===== автоцвет исходящих =====
	public boolean autoColor = true;
	/** Код цвета/стиля, например «&a», «&e&l»; пусто — без цвета. */
	public String colorDm = "&a";
	public String colorGlobal = "";
	public String colorLocal = "";
	/** Команды личных сообщений: у первых — ник, потом текст; у reply-команд — сразу текст. */
	public List<String> dmCommands = new ArrayList<>(List.of("msg", "m", "w", "tell", "pm", "whisper", "t", "r", "reply"));
	public List<String> replyCommands = new ArrayList<>(List.of("r", "reply"));

	// ===== автоприветствия =====
	public boolean autoGreet = true;
	public List<String> greetWords = new ArrayList<>(List.of("пр", "прив", "привет", "приветик", "приветствую", "ку", "куку", "qq", "q",
			"hi", "hello", "hey", "хай", "хеллоу", "здарова", "здорова", "дарова", "здравствуй", "здравствуйте", "салам", "йо", "ky", "pr"));
	/** Шаблоны ответа ({name} — ник, {word} — слово приветствия). */
	public List<String> greetReplies = new ArrayList<>(List.of("Привет, {name}!", "ку, {name} :)", "Здарова, {name}!", "Привет-привет, {name}"));
	/** Отвечать тем же словом: «qq Ник» → «qq Ник». */
	public boolean greetMirror = true;
	/** Отвечать и на «всем привет» без ника. */
	public boolean greetAll = false;
	public int greetCooldown = 60;
	/** Куда отвечать: G, L или CURRENT. */
	public String greetChannel = "G";
	/** Как писать в клан для ответа на приветствие в клановом чате («/c», «/clan chat {text}», «#»); пусто — в клане не отвечать. */
	public String greetClan = "";

	// ===== прокрутка и заголовок =====
	public float scrollSpeed = 1f;
	public boolean scrollbar = true;
	public boolean titleBar = true;
	public int themeVersion = 0;

	// ===== ответ, черновики, шаблоны =====
	public boolean replyButton = true;
	public boolean drafts = true;
	public String draft = "";
	public boolean templates = true;

	// ===== закреплённые =====
	public boolean pins = true;
	public List<Pin> pinned = new ArrayList<>();

	public static final class Pin {
		public String json = "\"\"";
		public long time;
		public String sender;
	}

	// ===== дополнительные окна =====
	public List<ExtraWindow> windows = new ArrayList<>();

	/** Отдельное окно чата: свой фильтр, положение и размер. kind: DM, GLOBAL, LOCAL, MENTIONS, SYSTEM, CUSTOM. */
	public static final class ExtraWindow {
		public String name = "ЛС";
		public String kind = "DM";
		public List<String> keywords = new ArrayList<>();
		/** Сообщения этого окна не показываются в основном. */
		public boolean exclusive = false;
		/** Видно, когда чат закрыт. */
		public boolean showClosed = true;
		/** -1 — у правого края экрана. */
		public int left = -1;
		public int bottom = 60;
		public int w = 220;
		public int h = 110;
	}

	// ===== АФК =====
	public boolean afk = true;
	public int afkMinutes = 5;
	public String afkMessage = "Я сейчас АФК, отвечу позже :)";
	/** Как отвечать: {name} — ник, {text} — текст. */
	public String afkCommand = "/msg {name} {text}";
	public int afkCooldown = 300;
	/** Сводка «пока тебя не было»: ЛС, упоминания, нарушения, персонал — когда вернулся. */
	public boolean afkSummary = true;

	// ===== антиспам =====
	public boolean antiSpam = true;
	/** Одинаковое сообщение от игрока повторно в течение N секунд — скрыть. */
	public int spamRepeatSeconds = 60;
	/** Больше N сообщений за M секунд от одного игрока — скрыть лишние. */
	public int floodLimit = 5;
	public int floodSeconds = 10;
	public boolean antiCaps = false;

	// ===== напоминания =====
	public List<Reminder> reminders = new ArrayList<>();
	public boolean reminderSound = true;

	public static final class Reminder {
		public long at;
		public String text = "";
	}

	// ===== отложенная отправка =====
	public List<Scheduled> scheduled = new ArrayList<>();

	public static final class Scheduled {
		public long at;
		public String text = "";
		/** Канал на момент написания: G / L. */
		public String channel = "L";
	}

	// ===== свои имена для ников (только у себя) =====
	public boolean nicknamesEnabled = true;
	/** ник в нижнем регистре → как показывать */
	public java.util.Map<String, String> nicknames = new java.util.LinkedHashMap<>();

	// ===== стиль ников и времени =====
	public boolean nameItalic = false;
	public boolean nameUnderline = false;
	public boolean nameCaps = false;
	/** Размер времени: 0.6..1. */
	public float timeScale = 1f;
	public boolean timeBold = false;

	// ===== слежка за персоналом =====
	public boolean staffWatch = true;
	public List<Staff> staff = new ArrayList<>();
	public boolean staffSound = true;
	/** Предупреждать и о выходе. */
	public boolean staffLeave = true;

	public static final class Staff {
		public String nick = "";
		/** Модератор, Хелпер, Владелец, Админ… */
		public String role = "Модератор";
		public int color = 0xFF6B7A;
		/** Найден сам по рангу в чате (роль обновляется, если ранг сменился). */
		public boolean auto = false;
	}

	/** Ранги персонала: «Название» или «Название=цвет» (цвет тега, &a → a). На Mineblaze [Хелпер] зелёный — персонал, другого цвета — донат; [Модер], [Админ], Владелец и т.д. — донат. */
	public List<String> staffRanks = new ArrayList<>(List.of("Хелпер=a", "Модератор", "Гл.Модератор"));
	/** Найденный раньше без учёта цвета персонал (там были донатные жёлтые [Хелпер]) пересобран. */
	public boolean staffColorV1 = false;
	public boolean staffAuto = true;
	/** Значок ⚑ перед сообщениями персонала. */
	public boolean staffBadge = true;
	/** Вкладка «Персонал» вместо «Система». */
	public boolean staffTab = true;
	/** Пауза под мышью: пока курсор над чатом, новые сообщения не сдвигают текст. */
	public boolean pauseOnHover = false;
	/** Разово: пауза под мышью мешала (чат «застывал» от наведения) — выключаем; остановка остаётся только при прокрутке вверх. */
	public boolean hoverPauseV2 = false;
	/** ПКМ по сообщению → «Наказать по пункту…». */
	public boolean modMenu = true;

	// ===== викторины в чате (Chat Game) =====
	public boolean chatGames = true;
	/** Кнопка [= ответ] у вопроса. */
	public boolean chatGameHint = true;
	/** Отвечать самому (по умолчанию выкл: на некоторых серверах за это наказывают). */
	public boolean chatGameAuto = false;
	/** Задержка перед автоответом, секунд (0 — сразу, в момент вопроса). */
	public float chatGameDelay = 2f;
	/** С чего начинаются сообщения викторины. */
	public String chatGamePrefix = "Chat Game »";
	/** Что ставить перед ответом (например «!» — в глобальный). */
	public String chatGameAnswerPrefix = "";

	// ===== консоль =====
	public int consoleLimit = 5000;

	// ===== фильтр =====
	public List<String> hidePatterns = new ArrayList<>();
	public List<String> ignored = new ArrayList<>();

	// ===== интерфейс открытого чата =====
	public boolean searchBar = true;
	public boolean copyMenu = true;
	public boolean styledInput = true;
	/** Плашка G / L слева в строке ввода. */
	public boolean inputChannelChip = false;
	/** Кнопки ⏱ (отложить) и ☰ (история) справа от строки ввода. */
	public boolean laterButton = true;

	// ===== быстрые фразы над строкой ввода =====
	/** Панель сохранённых сообщений и команд: клик — сразу отправить, чат остаётся открытым (можно жать подряд). */
	public boolean quickPhrases = false;
	/** Разово: панель фраз пользователю не понадобилась — выключаем у тех, у кого она включилась сама. */
	public boolean quickV2 = false;
	/** ↑ до строки из истории + Enter — отправить и оставить чат открытым с тем же текстом (Ctrl+Enter — для любого текста). */
	public boolean historyRepeat = true;
	/** Панель раскрыта (кнопка ⚡ сворачивает). */
	public boolean quickBarOpen = true;
	public List<String> quickList = new ArrayList<>();
	public boolean historyButton = true;
	/** Над строкой ввода — как будет выглядеть сообщение с &-кодами и градиентами. */
	public boolean colorPreview = true;

	// ===== история ввода (↑ / ↓) =====
	/** Своя история ввода: больше строк, без повторов, помнится после перезапуска. */
	public boolean betterHistory = true;
	/** Набрал начало («/tp») и жмёшь ↑ — листаются только строки, которые так начинаются. */
	public boolean historyPrefix = true;
	public boolean persistInput = true;
	public int inputHistorySize = 1000;

	// ===== шапка окна чата =====
	public boolean headerChannels = true;
	public boolean headerCollapse = true;
	public boolean headerGear = true;
	/** Плашка «↓ N нов.» при прокрутке вверх. */
	public boolean newMessagesHint = true;

	// ===== функции целиком =====
	public boolean remindersEnabled = true;
	public boolean laterSend = true;
	public boolean autoJoin = true;
	public boolean consoleLog = true;

	// ===== HEX-цвета и градиенты =====
	public enum HexFormat {
		AMP_HASH("&#RRGGBB"), AMP_X("&x&R&R&G&G&B&B"), MINI("<#RRGGBB>");
		public final String ru;
		HexFormat(String ru) { this.ru = ru; }
	}

	/** Понимать &#RGB, &#RRGGBB и градиенты &{…}текст при отправке. */
	public boolean hexColors = true;
	/** В каком виде отправлять HEX-цвет серверу. */
	public HexFormat hexFormat = HexFormat.AMP_HASH;
	public List<Gradient> gradients = defaultGradients();
	/** Разово: добавлены новые готовые градиенты. */
	public boolean gradientsV2 = false;

	/** Свой градиент: &{название}текст. */
	public static final class Gradient {
		public String name = "Градиент";
		public List<String> colors = new ArrayList<>();

		public Gradient() {}

		public Gradient(String name, String... colors) {
			this.name = name;
			this.colors = new ArrayList<>(List.of(colors));
		}
	}

	public static List<Gradient> defaultGradients() {
		return new ArrayList<>(List.of(
				new Gradient("радуга", "#FF5555", "#FFAA00", "#FFFF55", "#55FF55", "#55FFFF", "#5555FF", "#FF55FF"),
				new Gradient("огонь", "#FF2A00", "#FFD000"),
				new Gradient("океан", "#00C6FF", "#0050FF"),
				new Gradient("закат", "#FF5F6D", "#FFC371"),
				new Gradient("неон", "#FF00CC", "#3333FF"),
				new Gradient("лёд", "#E0FFFF", "#5DADE2"),
				new Gradient("токсик", "#A8FF00", "#00FF87"),
				new Gradient("аврора", "#B18CFF", "#6CB4FF", "#5BE39A"),
				// ---- тёплые
				new Gradient("рассвет", "#FF9A8B", "#FF6A88", "#FF99AC"),
				new Gradient("персик", "#FFB88C", "#DE6262"),
				new Gradient("лава", "#FF0000", "#FF7B00", "#FFD000"),
				new Gradient("вишня", "#EB3349", "#F45C43"),
				new Gradient("малина", "#FF0055", "#FF66CC"),
				new Gradient("клубника", "#FF5E62", "#FF9966"),
				new Gradient("арбуз", "#FF4E50", "#3EF06A"),
				new Gradient("солнце", "#F9D423", "#FF4E50"),
				new Gradient("лето", "#FFE259", "#FFA751"),
				new Gradient("янтарь", "#FFB347", "#FFCC33"),
				new Gradient("золото", "#FFD700", "#FFA500", "#FFF6A3"),
				new Gradient("карамель", "#C77D3A", "#F2C57C"),
				new Gradient("кофе", "#A0785A", "#E6C9A8"),
				new Gradient("осень", "#F7971E", "#FFD200", "#C0392B"),
				new Gradient("дракон", "#DC281E", "#F00000", "#FFD200"),
				new Gradient("кровь", "#8A0303", "#FF1E1E"),
				new Gradient("ад", "#FF3D00", "#B00020", "#FF8A00"),
				// ---- розовые и фиолетовые
				new Gradient("фламинго", "#FF7EB3", "#FF758C"),
				new Gradient("сакура", "#FFB7C5", "#FF8FB1", "#FFE4EC"),
				new Gradient("бабл-гам", "#FF9A9E", "#FAD0C4"),
				new Gradient("единорог", "#FFB3FF", "#B3E5FF", "#C2FFD8"),
				new Gradient("пастель", "#FBC2EB", "#A6C1EE"),
				new Gradient("лаванда", "#E0C3FC", "#8EC5FC"),
				new Gradient("фиалка", "#8E2DE2", "#4A00E0"),
				new Gradient("галактика", "#7F00FF", "#E100FF"),
				new Gradient("космос", "#00D2FF", "#3A7BD5", "#8E2DE2"),
				new Gradient("демон", "#FF416C", "#8E0E00"),
				// ---- синие и голубые
				new Gradient("небо", "#56CCF2", "#2F80ED"),
				new Gradient("море", "#1BFFFF", "#2E3192"),
				new Gradient("электро", "#00FFF0", "#0066FF"),
				new Gradient("павлин", "#00B4DB", "#0083B0", "#7F00FF"),
				new Gradient("ночь", "#6A82FB", "#2B3C8F"),
				new Gradient("зима", "#FFFFFF", "#B3E5FC", "#4FC3F7"),
				new Gradient("призрак", "#E0EAFC", "#CFDEF3"),
				new Gradient("сталь", "#E6E9F0", "#8A9BB5"),
				new Gradient("серебро", "#FFFFFF", "#C0C0C0", "#9E9E9E"),
				// ---- зелёные
				new Gradient("бирюза", "#11998E", "#38EF7D"),
				new Gradient("мята", "#A8FFCE", "#00F5A0"),
				new Gradient("лайм", "#DCE35B", "#45B649"),
				new Gradient("лес", "#3EF06A", "#1B8A3A"),
				new Gradient("весна", "#A8E063", "#56AB2F", "#FFDDE1"),
				new Gradient("эльф", "#A8FF78", "#78FFD6"),
				new Gradient("хамелеон", "#00C9FF", "#92FE9D"),
				new Gradient("яд", "#B4FF00", "#00FF6A"),
				new Gradient("сияние", "#00F260", "#0575E6"),
				// ---- яркие и смешанные
				new Gradient("киберпанк", "#FF00FF", "#00FFFF"),
				new Gradient("вапорвейв", "#FF71CE", "#01CDFE", "#05FFA1", "#B967FF"),
				new Gradient("ретро", "#F8B500", "#FC466B", "#3F5EFB"),
				new Gradient("тропики", "#F7FF00", "#DB36A4"),
				new Gradient("огонь-лёд", "#FF4E00", "#FFFFFF", "#00C6FF"),
				new Gradient("король", "#FFD700", "#FFFFFF", "#FFD700"),
				new Gradient("монохром", "#FFFFFF", "#9E9E9E"),
				// ---- камни
				new Gradient("изумруд", "#50C878", "#00A86B"),
				new Gradient("сапфир", "#0F52BA", "#6CB4FF"),
				new Gradient("рубин", "#E0115F", "#FF6B81"),
				new Gradient("аметист", "#9966CC", "#E0B0FF"),
				new Gradient("топаз", "#FFC87C", "#FF9B3D"),
				new Gradient("алмаз", "#B9F2FF", "#FFFFFF", "#7FDBFF")));
	}

	// ===== палитра и ники =====
	/** Кнопка «&» у строки ввода: цвета, стили, спектр, градиенты — клик вставляет код. */
	public boolean colorPicker = true;
	/** Tab дописывает ник (повторный Tab — следующий подходящий). */
	public boolean nickComplete = true;
	/** Серая подсказка ника прямо в строке ввода. */
	public boolean nickGhost = true;

	// ===== плавность интерфейса мода =====
	/** Панели (эмодзи, палитра, «отправить позже») раскрываются плавно. */
	public boolean popupAnim = true;
	public float popupTime = 0.16f;
	/** Плавная смена разделов в настройках. */
	public boolean settingsAnim = true;

	// ===== плавность Minecraft =====
	public enum ScreenAnim {
		RISE("Всплытие"), ZOOM("Увеличение"), SLIDE("Выезд сбоку");
		public final String ru;
		ScreenAnim(String ru) { this.ru = ru; }
	}

	/** Плавное открытие экранов игры: инвентарь, пауза, сундуки, меню. */
	public boolean mcScreenAnim = true;
	public ScreenAnim mcScreenStyle = ScreenAnim.RISE;
	public float mcScreenTime = 0.2f;
	/** И главное меню тоже. */
	public boolean mcScreenTitle = false;
	/** Рамка выбранного слота хотбара переезжает плавно. */
	public boolean mcSmoothHotbar = true;
	public float mcHotbarSpeed = 1f;
	/** Список игроков (Tab) плавно выезжает сверху. */
	public boolean mcTabAnim = true;

	// ===== оптимизация =====
	/** Когда FPS падает ниже порога, чат сам отключает анимации и тени. */
	public boolean perfAuto = true;
	public int perfFps = 30;
	/** Облегчённый чат всегда. */
	public boolean perfAlways = false;

	// ===== письмо градиентом =====
	/** Название сохранённого градиента, которым пишутся все твои сообщения; пусто — выключено. */
	public String writeGradient = "";
	/** И в личных сообщениях (/m, /msg …). */
	public boolean writeGradientDm = false;

	// ===== плавная отправка =====
	/** Отправленное сообщение плавно улетает вверх из строки ввода. */
	public boolean sendAnim = true;
	public float sendAnimTime = 0.22f;
	/** Разовый перевод на новую быструю отправку (старое значение 0.45 ощущалось задержкой). */
	public boolean sendFxV2 = false;

	// ===== круговое меню действий с игроком =====
	/** Навёлся на игрока + клавиша (G) — круг действий по центру экрана. */
	public boolean playerWheel = true;
	/** Действия круга. {ник} — ник игрока; команда с «?» в начале не отправляется, а встаёт в строку чата. */
	public List<WheelAction> wheelItems = defaultWheelActions();
	/** Затемнение экрана под меню, 0…1. */
	public float wheelDim = 0.55f;
	/** Дальность наведения, блоков. */
	public int wheelRange = 24;
	/** Спецэффекты при применении: на экране, частицы у игрока в мире (видишь только ты), звук. */
	public boolean wheelFx = true;
	public boolean wheelFxWorld = true;
	public boolean wheelFxSound = true;

	public enum WheelFx {
		HEARTS("Сердечки"), SPARKS("Искры"), STARS("Звёзды"), NOTES("Ноты"), CONFETTI("Конфетти"), NONE("Без эффекта");
		public final String ru;
		WheelFx(String ru) { this.ru = ru; }
	}

	public static class WheelAction {
		public String name = "Действие";
		public String command = "/hug {ник}";
		public WheelFx fx = WheelFx.HEARTS;
		public boolean enabled = true;

		public WheelAction() {}
		public WheelAction(String name, String command, WheelFx fx) { this.name = name; this.command = command; this.fx = fx; }
	}

	public static List<WheelAction> defaultWheelActions() {
		return new ArrayList<>(List.of(new WheelAction("Обнять", "/hug {ник}", WheelFx.HEARTS),
				new WheelAction("Погладить", "/headpat {ник}", WheelFx.SPARKS)));
	}

	// ===== модерация по правилам =====
	public enum ModMode {
		SUGGEST("Предлагать"), AUTO("Выдавать сам");
		public final String ru;
		ModMode(String ru) { this.ru = ru; }
	}

	/** Чат сверяет сообщения игроков с твоими правилами и предлагает (или сам выдаёт) наказание. */
	public boolean modEnabled = false;
	public ModMode modMode = ModMode.SUGGEST;
	/** За сколько часов считать повторные нарушения (1-е, 2-е, 3-е…). */
	public int modWindowHours = 24;
	/** Не наказывать одного игрока чаще, чем раз в N секунд. */
	public int modCooldown = 15;
	public boolean modIgnoreStaff = true;
	public boolean modSound = true;
	public List<String> modWhitelist = new ArrayList<>();
	/** Домены, которые рекламой не считаются (адрес твоего сервера, сайт, дискорд). */
	public List<String> modAllowedDomains = new ArrayList<>();
	public List<ModRule> modRules = new ArrayList<>();

	// ----- защита от ошибок (чтобы самому не получить бан за неверное наказание)
	/** Тренировка: ничего не отправляется, только пишется, что было бы выдано. */
	public boolean modDryRun = false;
	/** Автонаказание уходит не сразу — N секунд можно отменить (клавиша или /achat cancel). */
	public int modConfirmDelay = 5;
	/** Баны (ban/tempban) сам не выдаёт — только кнопкой. */
	public boolean modBanConfirm = true;
	/** Не больше N автонаказаний в час — дальше только кнопкой. */
	public int modMaxPerHour = 15;
	/** Мут/бан дольше N минут сам не выдаёт — только кнопкой. */
	public int modMaxAutoMinutes = 180;

	// ----- синтаксис команд сервера: правило пишет «mute 30m», а сюда подставляется
	public String cmdWarn = "/warn {name} {reason}";
	public String cmdMute = "/mute {name} {time} {reason}";
	public String cmdTempban = "/tempban {name} {time} {reason}";
	public String cmdBan = "/ban {name} {reason}";
	public String cmdKick = "/kick {name} {reason}";
	public String cmdUnmute = "/unmute {name}";
	public String cmdUnban = "/unban {name}";
	/** Журнал наказаний (config/aurorachat/punishments.json). */
	public boolean punishLog = true;

	/** Правило сервера: что ищем и какие команды выдаём за 1-е, 2-е, 3-е… нарушение. */
	public static final class ModRule {
		public boolean enabled = true;
		/** Как в правилах сервера, например «1.3 Реклама» — подставляется в {rule}. */
		public String name = "Правило";
		/** PROFANITY, INSULTS, CAPS, FLOOD, ADS, WORDS, REGEX. */
		public String type = "WORDS";
		public List<String> words = new ArrayList<>();
		/**
		 * Наказание за 1-е, 2-е, 3-е… нарушение. Коротко — «warn», «mute 30m», «kick», «tempban 7d», «ban»
		 * (команда берётся из «Синтаксиса команд сервера»), или целиком «/команда {name} …».
		 * {name} — ник, {rule} — правило, {count} — какое по счёту, {text} — сообщение.
		 */
		public List<String> actions = new ArrayList<>(List.of("warn"));
		public int capsPercent = 70;
		public int floodCount = 3;
		public int floodSeconds = 30;
	}

	// ===== шрифт =====
	/** Шрифт сообщений чата: minecraft:default, minecraft:uniform или свой из ресурспака. */
	public String chatFont = "minecraft:default";

	// ===== окно настроек =====
	/** Размер окна настроек относительно обычного интерфейса. */
	public float uiScale = 0.85f;
	public String uiFont = "minecraft:default";
	/** Плотные строки (меньше высота). */
	public boolean uiCompact = true;
	/** Цвет выделения в настройках: свой или как акцент чата. */
	public boolean uiFollowAccent = false;
	public int uiAccent = 0x4F8EF7;
	/** Затемнение игры под окном настроек. */
	public float uiDim = 0.85f;
	public boolean uiPreview = true;
	/** Тема окна настроек и её цвета (после ручной правки — «Своя»). */
	public String uiTheme = "Графит";
	public int uiBg = 0x08090C;
	public int uiPanel = 0x0E1015;
	public float uiPanelAlpha = 0.69f;
	public int uiText = 0xF2F3FA;
	public int uiSub = 0x9EA3BC;
	public int uiMuted = 0x6A6F88;
	/** Цвет «чернил» строк и рамок: белый для тёмных тем, чёрный для светлых. */
	public int uiInk = 0xFFFFFF;
	public int uiRadius = 3;

	/** Своя вкладка: сообщения, где встречается любое из слов. */
	public static final class CustomTab {
		public String name = "Вкладка";
		public List<String> keywords = new ArrayList<>();
	}

	// ------------------------------------------------------------ загрузка / сохранение

	/**
	 * Загрузка, устойчивая к ошибкам: каждый параметр читается отдельно, поэтому один испорченный
	 * параметр не сбрасывает остальные. Нечитаемый файл не затирается — он сохраняется рядом
	 * (aurorachat.json.broken-…), а настройки берутся из резервной копии aurorachat.json.bak.
	 */
	public static void load() {
		Path f = file();
		if (Files.exists(f)) {
			ChatConfig c = read(f);
			if (c == null) {
				try {
					Files.copy(f, f.resolveSibling("aurorachat.json.broken-" + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
				} catch (Exception ignored) { }
				Path bak = backup();
				if (Files.exists(bak)) {
					c = read(bak);
					if (c != null) AuroraChatMod.LOG.warn("Aurora Chat: aurorachat.json повреждён — настройки восстановлены из резервной копии");
				}
			}
			instance = c != null ? c : new ChatConfig();
		} else {
			migrate();
		}
		instance.sanitize();
		// обновление 2.1: новый деловой стиль по умолчанию (один раз)
		if (instance.themeVersion < 1) {
			instance.themeVersion = 1;
			ChatThemes.ALL.stream().filter(t -> t.name().equals("Деловой")).findFirst().ifPresent(t -> t.apply().accept(instance));
		}
		flush();
	}

	private static Path backup() { return file().resolveSibling("aurorachat.json.bak"); }

	/** Читает файл по одному параметру; null — файл совсем не разобрать. */
	private static ChatConfig read(Path p) {
		JsonObject o;
		try {
			JsonElement root = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
			if (root == null || !root.isJsonObject()) return null;
			o = root.getAsJsonObject();
		} catch (Exception e) {
			AuroraChatMod.LOG.error("Aurora Chat: не удалось прочитать {}: {}", p.getFileName(), e.toString());
			return null;
		}
		ChatConfig c = new ChatConfig();
		for (Field fl : ChatConfig.class.getDeclaredFields()) {
			int m = fl.getModifiers();
			if (Modifier.isStatic(m) || Modifier.isTransient(m)) continue;
			JsonElement el = o.get(fl.getName());
			if (el == null || el.isJsonNull()) continue;
			try {
				Object v = GSON.fromJson(el, fl.getGenericType());
				if (v != null) fl.set(c, v);
			} catch (Exception e) {
				AuroraChatMod.LOG.warn("Aurora Chat: параметр «{}» не прочитан ({}), оставлено значение по умолчанию", fl.getName(), e.getMessage());
			}
		}
		return c;
	}

	/** Перенос старых настроек чата из Aurora Cosmetics (aurora.json → "chat"). */
	private static void migrate() {
		Path old = FabricLoader.getInstance().getConfigDir().resolve("aurora.json");
		if (!Files.exists(old)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(old, StandardCharsets.UTF_8)).getAsJsonObject();
			if (!root.has("chat") || !root.get("chat").isJsonObject()) return;
			JsonObject ch = root.getAsJsonObject("chat").deepCopy();
			// поля с другим типом в старом формате
			for (String k : new String[]{"timestampColor", "mentionColor", "hudStyle", "fancyHud", "bgOpacity"}) ch.remove(k);
			ChatConfig c = GSON.fromJson(ch, ChatConfig.class);
			if (c != null) {
				instance = c;
				AuroraChatMod.LOG.info("Aurora Chat: настройки перенесены из aurora.json");
			}
		} catch (Exception e) {
			AuroraChatMod.LOG.warn("Aurora Chat: не удалось перенести старые настройки: {}", e.toString());
		}
	}

	private static volatile boolean dirty;
	private static volatile long dirtyAt;

	/** Отметить, что настройки изменились: файл запишется через долю секунды (слайдеры не пишут диск на каждый пиксель). */
	public static void save() {
		dirty = true;
		dirtyAt = System.currentTimeMillis();
	}

	/** Вызывается каждый тик клиента. */
	public static void tick() {
		if (dirty && System.currentTimeMillis() - dirtyAt > 300) flush();
	}

	/**
	 * Записать настройки. Снимок (JSON) делается сейчас, а на диск он уходит в фоне через Aurora Lib —
	 * игра не ждёт диск. Запись атомарная (временный файл → подмена), прежняя версия — в .bak.
	 */
	public static void flush() {
		dirty = false;
		try {
			net.primlixe.auroralib.io.AsyncFiles.write(file(), GSON.toJson(instance), true);
		} catch (Exception e) {
			AuroraChatMod.LOG.error("Aurora Chat: не удалось сохранить настройки", e);
		}
	}

	/** То же, но прямо сейчас и дождаться (выход из игры). */
	public static void flushNow() {
		dirty = false;
		try {
			net.primlixe.auroralib.io.AsyncFiles.flushAll(); // сначала то, что уже в очереди
			net.primlixe.auroralib.io.AsyncFiles.writeNow(file(), GSON.toJson(instance), true);
		} catch (Exception e) {
			AuroraChatMod.LOG.error("Aurora Chat: не удалось сохранить настройки", e);
		}
	}

	/** Вернуть окну положение и размер по умолчанию. */
	public void resetWindow() {
		ChatConfig d = new ChatConfig();
		winLeft = d.winLeft; winBottom = d.winBottom; winW = d.winW; winH = d.winH;
		closedHeight = d.closedHeight; textScale = d.textScale;
	}

	public ChatConfig sanitize() {
		if (bgStyle == null) bgStyle = BgStyle.PANEL;
		if (appear == null) appear = Appear.SLIDE;
		if (layout == null) layout = Layout.COMPACT;
		if (ease == null) ease = Ease.SMOOTH;
		if (tabStyle == null) tabStyle = TabStyle.PILLS;
		if (badgeStyle == null) badgeStyle = BadgeStyle.LETTER;
		if (timePos == null) timePos = TimePos.RIGHT;
		if (theme == null) theme = "Своя";
		if (draft == null) draft = "";
		if (pinned == null) pinned = new ArrayList<>();
		pinned.removeIf(p -> p == null || p.json == null);
		if (windows == null) windows = new ArrayList<>();
		windows.removeIf(x -> x == null);
		for (ExtraWindow x : windows) {
			if (x.name == null) x.name = "Окно";
			if (x.kind == null) x.kind = "DM";
			if (x.keywords == null) x.keywords = new ArrayList<>();
			x.w = clamp(x.w, 100, 1200);
			x.h = clamp(x.h, 30, 800);
		}
		if (afkMessage == null) afkMessage = "Я сейчас АФК, отвечу позже :)";
		if (afkCommand == null || afkCommand.isBlank() || afkCommand.equals("/m {name} {text}")) afkCommand = "/msg {name} {text}"; // /msg есть и в ванилле, и на серверах
		afkMinutes = clamp(afkMinutes, 0, 120);
		afkCooldown = clamp(afkCooldown, 10, 3600);
		spamRepeatSeconds = clamp(spamRepeatSeconds, 0, 3600);
		floodLimit = clamp(floodLimit, 2, 50);
		floodSeconds = clamp(floodSeconds, 2, 120);
		if (reminders == null) reminders = new ArrayList<>();
		reminders.removeIf(r -> r == null || r.text == null);
		timeScale = clampF(timeScale, 0.6f, 1f);
		if (staff == null) staff = new ArrayList<>();
		staff.removeIf(x -> x == null || x.nick == null || x.nick.isBlank());
		for (Staff x : staff) if (x.role == null) x.role = "Модератор";
		if (staffRanks == null) staffRanks = new ArrayList<>(List.of("Хелпер=a", "Модератор", "Гл.Модератор"));
		if (!staffColorV1) {
			staffColorV1 = true;
			if (staffRanks.equals(List.of("Хелпер", "Модератор", "Гл.Модератор"))) staffRanks = new ArrayList<>(List.of("Хелпер=a", "Модератор", "Гл.Модератор"));
			staff.removeIf(x -> x.auto); // соберутся заново из журналов и таба, уже с проверкой цвета
		}
		staffRanks.removeIf(x -> x == null || x.isBlank());
		consoleLimit = clamp(consoleLimit, 500, 50000);
		if (scheduled == null) scheduled = new ArrayList<>();
		scheduled.removeIf(x -> x == null || x.text == null || x.text.isBlank());
		if (nicknames == null) nicknames = new java.util.LinkedHashMap<>();
		nicknames.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || e.getValue().isBlank());
		if (colorDm == null) colorDm = "";
		if (colorGlobal == null) colorGlobal = "";
		if (colorLocal == null) colorLocal = "";
		if (dmCommands == null) dmCommands = new ChatConfig().dmCommands;
		if (replyCommands == null) replyCommands = new ChatConfig().replyCommands;
		if (greetWords == null) greetWords = new ChatConfig().greetWords;
		if (greetReplies == null || greetReplies.isEmpty()) greetReplies = new ChatConfig().greetReplies;
		if (greetChannel == null) greetChannel = "G";
		greetCooldown = clamp(greetCooldown, 5, 3600);
		scrollSpeed = clampF(scrollSpeed, 0.3f, 4f);
		stripeWidth = clamp(stripeWidth, 1, 4);
		rowPadding = clamp(rowPadding, 0, 6);
		messageGap = clamp(messageGap, 0, 8);
		bubbleRadius = clamp(bubbleRadius, 0, 10);
		headSize = clamp(headSize, 6, 16);
		outlineOpacity = clampF(outlineOpacity, 0f, 1f);
		textOpacity = clampF(textOpacity, 0.3f, 1f);
		openSpeed = clampF(openSpeed, 0.3f, 3f);
		appearTime = clampF(appearTime, 0.05f, 1.5f);
		slideDistance = clampF(slideDistance, 0f, 60f);
		scrollSmoothness = clampF(scrollSmoothness, 0f, 1f);
		pushSpeed = clampF(pushSpeed, 0.3f, 3f);
		fadeOutTime = clampF(fadeOutTime, 0.1f, 5f);
		tabSpeed = clampF(tabSpeed, 0.3f, 3f);
		if (timeFormat == null || timeFormat.isBlank()) timeFormat = "HH:mm";
		if (channel == null || !(channel.equals("G") || channel.equals("L"))) channel = "L";
		if (globalPrefix == null) globalPrefix = "!";
		if (localPrefix == null) localPrefix = "";
		if (globalPattern == null) globalPattern = new ChatConfig().globalPattern;
		if (localPattern == null) localPattern = new ChatConfig().localPattern;
		if (mentionWords == null) mentionWords = new ArrayList<>();
		if (hidePatterns == null) hidePatterns = new ArrayList<>();
		if (ignored == null) ignored = new ArrayList<>();
		if (customTabs == null) customTabs = new ArrayList<>();
		// пустые элементы в списках (null в json) роняли обработку каждого сообщения
		mentionWords = noNulls(mentionWords);
		hidePatterns = noNulls(hidePatterns);
		ignored = noNulls(ignored);
		dmCommands = noNulls(dmCommands);
		replyCommands = noNulls(replyCommands);
		greetWords = noNulls(greetWords);
		greetReplies = noNulls(greetReplies);
		if (greetReplies.isEmpty()) greetReplies = new ChatConfig().greetReplies;
		if (!greetChannel.equals("G") && !greetChannel.equals("L") && !greetChannel.equals("CURRENT")) greetChannel = "G";
		customTabs.removeIf(x -> x == null);
		for (CustomTab t : customTabs) {
			if (t.name == null || t.name.isBlank()) t.name = "Вкладка";
			t.keywords = t.keywords == null ? new ArrayList<>() : noNulls(t.keywords);
		}
		for (ExtraWindow x : windows) x.keywords = noNulls(x.keywords);
		for (Pin p : pinned) if (p.sender != null && p.sender.isBlank()) p.sender = null;
		if (clanPrefix == null || clanPrefix.isBlank()) clanPrefix = "КЛАН:";
		if (chatGamePrefix == null) chatGamePrefix = "Chat Game »";
		if (chatGameAnswerPrefix == null) chatGameAnswerPrefix = "";
		chatGameDelay = clampF(chatGameDelay, 0f, 30f);
		if (!smallCapsV1) {
			smallCapsV1 = true;
			if (!globalPattern.contains("ɢ")) globalPattern = globalPattern.replace("(G|Г|", "(G|Г|ɢ|");
			if (!localPattern.contains("ʟ")) localPattern = localPattern.replace("(L|Л|", "(L|Л|ʟ|");
		}
		if (!mbRulesV1) {
			mbRulesV1 = true;
			// старый шаблон «п.? …» без номеров → настоящие пункты Mineblaze 4.x (свои правила не трогаем)
			if (modRules != null && !modRules.isEmpty() && modRules.stream().allMatch(r -> r != null && r.name != null && r.name.startsWith("п.?"))) {
				modRules.clear();
				modRules.addAll(net.primlixe.aurorachat.chat.Moderation.mineblaze());
			}
		}
		if (!clanWinV1) {
			clanWinV1 = true;
			if (windows != null && windows.stream().noneMatch(x -> x != null && "CLAN".equals(x.kind))) {
				ExtraWindow w = new ExtraWindow();
				w.kind = "CLAN";
				w.name = "Клан";
				w.bottom = 60 + windows.size() * 120;
				windows.add(w);
			}
		}
		winW = clamp(winW, 100, 1600);
		winH = clamp(winH, 30, 1000);
		winLeft = clamp(winLeft, 0, 4000);
		winBottom = clamp(winBottom, 0, 4000);
		closedHeight = clampF(closedHeight, 0.2f, 1f);
		textScale = clampF(textScale, 0.5f, 1.6f);
		lineSpacing = clamp(lineSpacing, 0, 6);
		radius = clamp(radius, 0, 12);
		bgOpacity = clampF(bgOpacity, 0f, 1f);
		bgOpacityClosed = clampF(bgOpacityClosed, 0f, 1f);
		animSpeed = clampF(animSpeed, 0.3f, 3f);
		fadeSeconds = clampF(fadeSeconds, 2f, 120f);
		historySize = clamp(historySize, 100, 16384);
		persistLimit = clamp(persistLimit, 0, 5000);
		inputHistorySize = clamp(inputHistorySize, 50, 10000);
		if (hexFormat == null) hexFormat = HexFormat.AMP_HASH;
		if (gradients == null) gradients = defaultGradients();
		gradients.removeIf(x -> x == null || x.name == null || x.name.isBlank() || x.colors == null || x.colors.isEmpty());
		if (!gradientsV2) {
			// разово: новые готовые градиенты — к списку игрока (свои и изменённые не трогаем, удалённые старые не возвращаем)
			gradientsV2 = true;
			java.util.Set<String> have = new java.util.HashSet<>();
			for (Gradient x : gradients) have.add(x.name.toLowerCase(java.util.Locale.ROOT));
			List<Gradient> defs = defaultGradients();
			for (int i = 8; i < defs.size(); i++) if (have.add(defs.get(i).name)) gradients.add(defs.get(i));
		}
		if (chatFont == null || chatFont.isBlank()) chatFont = "minecraft:default";
		if (uiFont == null || uiFont.isBlank()) uiFont = "minecraft:default";
		uiScale = clampF(uiScale, 0.6f, 1.25f);
		uiDim = clampF(uiDim, 0.3f, 1f);
		if (uiTheme == null) uiTheme = "Своя";
		uiPanelAlpha = clampF(uiPanelAlpha, 0.2f, 1f);
		uiRadius = clamp(uiRadius, 0, 10);
		if (uiInk != 0x000000) uiInk = 0xFFFFFF;
		popupTime = clampF(popupTime, 0.05f, 0.6f);
		if (mcScreenStyle == null) mcScreenStyle = ScreenAnim.RISE;
		mcScreenTime = clampF(mcScreenTime, 0.05f, 0.6f);
		mcHotbarSpeed = clampF(mcHotbarSpeed, 0.3f, 3f);
		perfFps = clamp(perfFps, 10, 120);
		if (writeGradient == null) writeGradient = "";
		if (!sendFxV2) { sendFxV2 = true; sendAnimTime = 0.22f; }
		sendAnimTime = clampF(sendAnimTime, 0.1f, 0.8f);
		if (!quickV2) { quickV2 = true; quickPhrases = false; }
		if (!hoverPauseV2) { hoverPauseV2 = true; pauseOnHover = false; }
		if (!viewSplitV1) {
			viewSplitV1 = true;
			allView = allBoth ? "GL" : channel; // что было видно — так и остаётся
			channel = "L";                       // а писать — снова в L, «!» только когда сам поставишь
		}
		if (allView == null || !(allView.equals("G") || allView.equals("L") || allView.equals("GL"))) allView = "GL";
		if (!chVividV1) {
			chVividV1 = true;
			if (globalColor == 0xFFB347) globalColor = 0xFFA21F; // свои цвета не трогаем — только бывшие по умолчанию
			if (localColor == 0x6CE38A) localColor = 0x3EF06A;
		}
		channelTint = clampF(channelTint, 0f, 0.3f);
		if (gLabel == null || gLabel.isBlank()) gLabel = "G";
		if (lLabel == null || lLabel.isBlank()) lLabel = "L";
		gLabel = gLabel.strip(); lLabel = lLabel.strip();
		if (gLabel.length() > 6) gLabel = gLabel.substring(0, 6);
		if (lLabel.length() > 6) lLabel = lLabel.substring(0, 6);
		if (badgeShape == null) badgeShape = BadgeShape.PILL;
		channelStripe = clamp(channelStripe, 0, 6);
		channelTextTint = clampF(channelTextTint, 0f, 0.6f);
		if (greetClan == null) greetClan = "";
		if (!"ru".equals(uiLanguage) && !"en".equals(uiLanguage)) uiLanguage = "auto";
		translateTo = "auto".equals(translateTo) ? "auto" : net.primlixe.aurorachat.chat.Translator.normLang(translateTo, "auto");
		translateOut = net.primlixe.aurorachat.chat.Translator.normLang(translateOut, "en");
		if (bgImage == null) bgImage = "";
		if (bgImageMode == null) bgImageMode = ImageMode.COVER;
		bgImageOpacity = clampF(bgImageOpacity, 0.05f, 1f);
		if (myThemes == null) myThemes = new ArrayList<>();
		myThemes.removeIf(x -> x == null || x.code == null || x.code.isBlank());
		for (SavedTheme x : myThemes) if (x.name == null || x.name.isBlank()) x.name = "Моя тема";
		if (quickList == null) quickList = new ArrayList<>();
		quickList.removeIf(x -> x == null || x.isBlank());
		if (wheelItems == null) wheelItems = defaultWheelActions();
		wheelItems.removeIf(x -> x == null);
		for (WheelAction a : wheelItems) {
			if (a.name == null) a.name = "Действие";
			if (a.command == null) a.command = "";
			if (a.fx == null) a.fx = WheelFx.HEARTS;
		}
		wheelDim = clampF(wheelDim, 0f, 0.9f);
		wheelRange = clamp(wheelRange, 3, 64);
		if (modMode == null) modMode = ModMode.SUGGEST;
		modWindowHours = clamp(modWindowHours, 1, 720);
		modCooldown = clamp(modCooldown, 0, 600);
		modWhitelist = modWhitelist == null ? new ArrayList<>() : noNulls(modWhitelist);
		modAllowedDomains = modAllowedDomains == null ? new ArrayList<>() : noNulls(modAllowedDomains);
		if (modRules == null) modRules = new ArrayList<>();
		modRules.removeIf(r -> r == null);
		modConfirmDelay = clamp(modConfirmDelay, 0, 60);
		modMaxPerHour = clamp(modMaxPerHour, 1, 200);
		modMaxAutoMinutes = clamp(modMaxAutoMinutes, 1, 100000);
		ChatConfig d0 = null;
		if (cmdWarn == null || cmdMute == null || cmdTempban == null || cmdBan == null || cmdKick == null || cmdUnmute == null || cmdUnban == null) {
			d0 = new ChatConfig();
			if (cmdWarn == null) cmdWarn = d0.cmdWarn;
			if (cmdMute == null) cmdMute = d0.cmdMute;
			if (cmdTempban == null) cmdTempban = d0.cmdTempban;
			if (cmdBan == null) cmdBan = d0.cmdBan;
			if (cmdKick == null) cmdKick = d0.cmdKick;
			if (cmdUnmute == null) cmdUnmute = d0.cmdUnmute;
			if (cmdUnban == null) cmdUnban = d0.cmdUnban;
		}
		for (ModRule r : modRules) {
			if (r.name == null || r.name.isBlank()) r.name = "Правило";
			if (r.type == null) r.type = "WORDS";
			r.words = r.words == null ? new ArrayList<>() : noNulls(r.words);
			r.actions = r.actions == null ? new ArrayList<>() : noNulls(r.actions);
			r.actions.removeIf(String::isBlank);
			r.capsPercent = clamp(r.capsPercent, 30, 100);
			r.floodCount = clamp(r.floodCount, 2, 20);
			r.floodSeconds = clamp(r.floodSeconds, 3, 600);
		}
		return this;
	}

	/** Изменяемая копия списка без null (List.of и Gson-списки бывают неизменяемыми/с null). */
	private static List<String> noNulls(List<String> l) {
		List<String> out = new ArrayList<>();
		if (l != null) for (String s : l) if (s != null) out.add(s);
		return out;
	}

	private static int clamp(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }

	private static float clampF(float v, float a, float b) { return Float.isNaN(v) ? a : Math.max(a, Math.min(b, v)); }
}
