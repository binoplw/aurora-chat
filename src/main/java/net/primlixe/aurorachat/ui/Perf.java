package net.primlixe.aurorachat.ui;

import net.minecraft.client.Minecraft;
import net.primlixe.aurorachat.config.ChatConfig;

/**
 * Экономный режим: когда FPS проседает ниже порога, чат перестаёт рисовать тени и анимации,
 * а когда FPS вернулся (с запасом +10) — включает обратно. Можно включить навсегда.
 */
public final class Perf {
	// замеры Aurora Lib: /auroralib prof
	public static final net.primlixe.auroralib.perf.Prof.Section HUD = net.primlixe.auroralib.perf.Prof.section("чат: окно");
	public static final net.primlixe.auroralib.perf.Prof.Section INPUT = net.primlixe.auroralib.perf.Prof.section("чат: строка ввода");
	public static final net.primlixe.auroralib.perf.Prof.Section INCOMING = net.primlixe.auroralib.perf.Prof.section("чат: новое сообщение");
	public static final net.primlixe.auroralib.perf.Prof.Section TICK = net.primlixe.auroralib.perf.Prof.section("чат: тик");
	public static final net.primlixe.auroralib.perf.Prof.Section LAYOUT = net.primlixe.auroralib.perf.Prof.section("чат: окно · раскладка");
	public static final net.primlixe.auroralib.perf.Prof.Section DRAW = net.primlixe.auroralib.perf.Prof.section("чат: окно · строки");
	public static final net.primlixe.auroralib.perf.Prof.Section REFLOW = net.primlixe.auroralib.perf.Prof.section("чат: пересборка строк истории");

	public static boolean lite() {
		ChatConfig c = ChatConfig.get();
		if (c.perfAlways) return true;
		if (!c.perfAuto) return false;
		return net.primlixe.auroralib.perf.Fps.low(c.perfFps); // общий FPS-монитор Aurora Lib
	}

	/** Анимации чата сейчас включены. */
	public static boolean anim() { return ChatConfig.get().animations && !lite(); }

	/** Плавное появление за время t секунд: 0 → 1. */
	public static float appear(long since, float seconds) {
		if (since == 0) return 1;
		return UI.ease((System.nanoTime() - since) / 1e9f / Math.max(0.01f, seconds));
	}

	private Perf() {}
}
