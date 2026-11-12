package net.primlixe.aurorachat.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.ui.Perf;
import net.primlixe.aurorachat.ui.UI;

import java.util.HashSet;
import java.util.Set;

/**
 * Плавная отправка: текст из строки ввода (уже в своих цветах) сразу летит на своё место — в нижнюю строку
 * окна чата. Когда сервер присылает это сообщение обратно, оно встаёт в чат без второй анимации,
 * а летящий текст растворяется — получается одно непрерывное движение без ощущения задержки.
 */
public final class SendFx {
	private static Component text;
	private static float x0, y0;
	private static long born, arrived;
	private static boolean command;
	/** Время (ms) своих сообщений, которые надо показать без анимации появления. */
	private static final Set<Long> INSTANT = new HashSet<>();

	public static void start(String typed, float x, float y) {
		ChatConfig c = ChatConfig.get();
		if (!c.enabled || !c.sendAnim || !Perf.anim() || typed == null || typed.isBlank()) { text = null; return; }
		command = typed.startsWith("/");
		String shown = command ? typed : AutoColor.apply(typed);
		text = ColorCodes.hasAnyCode(shown) ? ColorCodes.preview(shown) : Component.literal(shown);
		x0 = x;
		y0 = y;
		born = System.nanoTime();
		arrived = 0;
	}

	/** Пришло своё сообщение: если мы его только что «отправили полётом» — показать сразу, без въезда. */
	public static void claim(long messageTime) {
		if (text == null || command || arrived != 0) return;
		if ((System.nanoTime() - born) / 1e9f > 3f) return;
		arrived = System.nanoTime();
		INSTANT.add(messageTime);
		if (INSTANT.size() > 50) INSTANT.clear();
	}

	public static boolean instant(long messageTime) { return INSTANT.contains(messageTime); }

	private static float targetX() {
		ChatConfig c = ChatConfig.get();
		if (!c.customHud) return 4;
		return c.winLeft + 6 + (c.timestamps && c.timePos == ChatConfig.TimePos.LEFT ? 30 : 0) + (c.heads ? c.headSize + 4 : 0);
	}

	private static float targetY(int guiH) {
		ChatConfig c = ChatConfig.get();
		return guiH - (c.customHud ? c.winBottom : 40) - 10 - c.rowPadding;
	}

	public static void render(GuiGraphicsExtractor g) {
		if (text == null) return;
		long now = System.nanoTime();
		float life = ChatConfig.get().sendAnimTime;
		float t = Math.min(1, (now - born) / 1e9f / life);
		float e = 1 - (float) Math.pow(1 - t, 3);
		float alpha;
		float x, y;
		if (command) {
			// команда в чат не возвращается — лёгкий подъём и растворение на месте
			x = x0;
			y = y0 - e * 12;
			alpha = 1 - t;
		} else {
			x = x0 + (targetX() - x0) * e;
			y = y0 + (targetY(g.guiHeight()) - y0) * e;
			// растворяется, когда сообщение уже стоит в чате; не пришло — тает на месте после прилёта
			float fadeFrom = arrived != 0 ? Math.max(arrived, born + (long) (life * 0.6f * 1e9f)) : born + (long) ((life + 0.6f) * 1e9f);
			alpha = now < fadeFrom ? 1 : 1 - Math.min(1, (now - fadeFrom) / 1e9f / 0.12f);
		}
		if (alpha <= 0.01f) { text = null; return; }
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.text(Minecraft.getInstance().font, text, 0, 0, UI.rgba(0xFFFFFF, alpha), true);
		g.pose().popMatrix();
	}

	private SendFx() {}
}
