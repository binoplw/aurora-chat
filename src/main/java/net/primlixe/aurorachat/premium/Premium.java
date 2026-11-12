package net.primlixe.aurorachat.premium;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.primlixe.aurorachat.Edition;
import net.primlixe.aurorachat.config.ChatConfig;

/**
 * Aurora Chat+ — платная часть мода. Без ключа работает базовый чат: окно, 3 темы, каналы G / L,
 * вкладки «Все» / G / L. Ключ привязан к нику и подписан Ed25519: подделать или переписать на другой ник нельзя.
 * Это версия для GitHub: код платных функций и проверки ключей не публикуется — вместо них заглушки.
 */
public final class Premium {
	/** Где купить: раздел сайта. */
	public static final String SHOP_URL = "https://binoplw.github.io/aurora-chat/#premium";
	public static final String NAME = "Aurora Chat+";
	/** В этой сборке есть код платных функций (в версии с GitHub — нет, там заглушки). */
	public static final boolean BUILT_IN = false;
	/** Цена на экране магазина (переводится через i18n). */
	public static final String PRICE = "199 ₽ — навсегда";

	/** Темы, доступные без ключа. */
	public static final Set<String> FREE_THEMES = Set.of("Деловой", "Discord", "Aurora");

	/** Переключатели платных функций: без ключа они выключены. */
	public static final List<String> PAID_FLAGS = List.of(
			"translate", "translateAuto", "translateButton", "linkPreview", "emojiShortcodes", "emojiPicker",
			"persistHistory", "logging", "logSystem", "searchBar", "hexColors", "colorPicker", "gradientsV2", "writeGradientDm",
			"autoColor", "autoGreet", "greetMirror", "greetAll", "afk", "afkSummary", "antiSpam", "antiCaps",
			"remindersEnabled", "laterSend", "laterButton", "templates", "drafts", "pins", "nickComplete", "nickGhost",
			"nicknamesEnabled", "playerWheel", "wheelFx", "quickPhrases", "autoJoin", "consoleLog", "clanTab",
			"betterHistory", "historyButton", "historyRepeat", "persistInput", "sendFxV2");

	private static volatile String licensedNick;   // ник из проверенного ключа, в нижнем регистре
	private static volatile String until = "-";    // срок ключа: "-" — навсегда
	private static volatile LocalDate untilDate;   // null — навсегда
	private static volatile String loadedKey = "";

	private Premium() {}

	/** Версия с GitHub: платные функции не собраны. */
	public static boolean has() { return false; }

	public static boolean paid(String flag) { return PAID_FLAGS.contains(flag); }

	public static boolean themeFree(String name) { return FREE_THEMES.contains(name); }

	/** Кому выдан ключ и до какого числа — для экрана магазина. */
	public static String status() {
		if (!Edition.PUBLIC) return "личная сборка";
		if (licensedNick == null) return null;
		return licensedNick + ("-".equals(until) ? " · навсегда" : " · до " + until);
	}

	public static String currentNick() {
		try {
			return Minecraft.getInstance().getUser().getName();
		} catch (Throwable t) {
			return null;
		}
	}

	private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("aurorachat-key.txt"); }

	public static void load() { }

	/** Версия с GitHub: ключи вводятся в полной версии с Modrinth. */
	public static String activate(String key) { return "Это сборка с GitHub без Aurora Chat+ — скачай полную версию с Modrinth"; }

	/** Без ключа платные переключатели выключены, тема — из бесплатных (вызывается при загрузке и раз в секунду). */
	public static void enforce(ChatConfig c) {
		if (has()) return;
		boolean changed = false;
		for (String n : PAID_FLAGS) {
			try {
				Field f = ChatConfig.class.getField(n);
				if (f.getBoolean(c)) { f.setBoolean(c, false); changed = true; }
			} catch (ReflectiveOperationException ignored) {
			}
		}
		if (!themeFree(c.theme)) {
			net.primlixe.aurorachat.config.ChatThemes.ALL.stream().filter(t -> t.name().equals("Деловой")).findFirst()
					.ifPresent(t -> t.apply().accept(c));
			changed = true;
		}
		if (changed) ChatConfig.save();
	}

}
