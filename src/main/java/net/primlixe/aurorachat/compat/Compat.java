package net.primlixe.aurorachat.compat;

import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Совместимость с модами сборки. Что делает Aurora Chat, когда рядом стоит:
 * <ul>
 *   <li>TLSkinCape — головы в чате/карточках/круге берут скины TLauncher ({@link Skins});</li>
 *   <li>SmoothScroll — плавный хотбар отдаём ему (иначе двойное сглаживание и задержка рамки);
 *       колесо в чате обрабатываем сами — дробные шаги не теряются;</li>
 *   <li>MoreChatHistory — размер истории берётся из настроек Aurora Chat;</li>
 *   <li>Freecam — круговое меню целится от камеры;</li>
 *   <li>ванильные «Быстрые действия» (G) — круг забирает нажатие только когда навёлся на игрока.</li>
 * </ul>
 */
public final class Compat {
	private static final FabricLoader L = FabricLoader.getInstance();

	public static final boolean SMOOTH_SCROLL = L.isModLoaded("smoothscroll");
	public static final boolean MORE_CHAT_HISTORY = L.isModLoaded("morechathistory");
	public static final boolean TL_SKIN_CAPE = L.isModLoaded("tlskincape");
	public static final boolean FREECAM = L.isModLoaded("freecam");
	public static final boolean PING_WHEEL = L.isModLoaded("pingwheel");
	public static final boolean COMMAND_KEYS = L.isModLoaded("commandkeys");
	public static final boolean ZOOMIFY = L.isModLoaded("zoomify");

	/** Плавный хотбар делает SmoothScroll — свой не включаем. */
	public static boolean hotbarByOtherMod() { return SMOOTH_SCROLL; }

	/** Строки для раздела настроек «Совместимость». */
	public static List<String[]> report() {
		List<String[]> r = new ArrayList<>();
		if (TL_SKIN_CAPE) r.add(new String[]{"TLSkinCape", "головы в чате, карточках и круге — со скинами TLauncher"});
		if (SMOOTH_SCROLL) r.add(new String[]{"SmoothScroll", "плавный хотбар — от SmoothScroll; колесо в чате — от Aurora Chat"});
		if (MORE_CHAT_HISTORY) r.add(new String[]{"MoreChatHistory", "размер истории — из «История» в Aurora Chat"});
		if (FREECAM) r.add(new String[]{"Freecam", "круговое меню целится от свободной камеры"});
		if (PING_WHEEL) r.add(new String[]{"Ping Wheel", "свои клавиши (мышь 5) — не пересекаются с кругом"});
		if (COMMAND_KEYS) r.add(new String[]{"CommandKeys", "макросы на клавишах — проверь, чтобы не совпадали с G/H"});
		if (ZOOMIFY) r.add(new String[]{"Zoomify", "в приближении круг ловит игрока точным лучом"});
		return r;
	}

	private Compat() {}
}
