package net.primlixe.aurorachat.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.primlixe.aurorachat.ui.UI;

import java.util.ArrayList;
import java.util.List;

/** Всплывающие карточки справа сверху: личные сообщения и упоминания. */
public final class ChatToasts {
	private record Toast(String title, String text, String sender, int color, long born) {}

	private static final List<Toast> TOASTS = new ArrayList<>();
	private static final long LIFE = 5000;

	public static void push(String title, String text, String sender, int color) {
		title = net.primlixe.aurorachat.ui.I18n.t(title); // подписи мода; текст сообщения игрока не трогаем
		TOASTS.add(0, new Toast(title, text, sender, color, System.currentTimeMillis()));
		while (TOASTS.size() > 4) TOASTS.remove(TOASTS.size() - 1);
	}

	public static void render(GuiGraphicsExtractor g) {
		if (TOASTS.isEmpty()) return;
		var mc = Minecraft.getInstance();
		if (net.primlixe.aurorachat.compat.Mc.screen() != null && !(net.primlixe.aurorachat.compat.Mc.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
		var font = mc.font;
		long now = System.currentTimeMillis();
		TOASTS.removeIf(t -> now - t.born() > LIFE);
		int w = Math.min(200, g.guiWidth() / 3);
		int y = 6;
		for (Toast t : TOASTS) {
			float age = (now - t.born()) / 1000f;
			float in = UI.ease(age / 0.3f), out = UI.ease((LIFE / 1000f - age) / 0.4f);
			float a = Math.min(in, out);
			int x = g.guiWidth() - 6 - (int) (w * a) + (int) ((1 - a) * 0);
			x = (int) (g.guiWidth() - 6 - w + (1 - in) * (w + 10));
			int h = 30;
			UI.shadow(g, x, y, x + w, y + h, 8);
			UI.round(g, x, y, x + w, y + h, 8, UI.alpha(0xF0141626, a));
			UI.round(g, x, y + 5, x + 3, y + h - 5, 1, UI.alpha(t.color(), a));
			int tx = x + 10;
			PlayerInfo pi = t.sender() == null || mc.getConnection() == null ? null : mc.getConnection().getPlayerInfoIgnoreCase(t.sender());
			if (pi != null) {
				PlayerFaceExtractor.extractRenderState(g, net.primlixe.auroralib.compat.Skins.of(pi), x + 9, y + 7, 16);
				tx += 22;
			}
			g.text(font, UI.trim(font, t.title(), x + w - tx - 6), tx, y + 6, UI.alpha(t.color(), a), false);
			g.text(font, UI.trim(font, t.text(), x + w - tx - 6), tx, y + 17, UI.alpha(UI.TEXT, a), false);
			// полоска времени жизни
			float life = 1 - (now - t.born()) / (float) LIFE;
			g.fill(x + 8, y + h - 2, x + 8 + (int) ((w - 16) * life), y + h - 1, UI.alpha(t.color(), 0.6f * a));
			y += h + 4;
		}
	}

	private ChatToasts() {}
}
