package net.primlixe.aurorachat.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.primlixe.aurorachat.ui.UI;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Контекстное меню сообщения (ПКМ в открытом чате). */
public final class ChatMenu {
	private record Item(String label, Runnable action) {}

	private GuiMessage msg;
	private int x, y, w, h;
	private final List<Item> items = new ArrayList<>();
	private String toast;
	private long toastUntil;

	public boolean isOpen() { return msg != null; }

	public void close() { msg = null; }

	public void open(GuiMessage m, int mx, int my, int screenW, int screenH, EditBox input, Consumer<String> openLog) {
		msg = m;
		items.clear();
		var mc = Minecraft.getInstance();
		Component orig = Translator.originalOf(m.content()); // без строки перевода
		String plain = orig.getString();
		// время «[12:30] » и «×N» дописывает только ванильный режим; тег канала «[ɢ] » — часть сообщения, его не трогаем
		String clean = ChatCore.stripTime(plain).replaceFirst("\\s×\\d+$", "");
		if (ChatCore.cfg().translate) {
			if (Translator.isTranslated(m.content())) items.add(new Item(net.primlixe.aurorachat.ui.I18n.t("⇄ Показать оригинал"), () -> Translator.showOriginal(m)));
			else items.add(new Item(net.primlixe.aurorachat.ui.I18n.f("⇄ Перевести (%s)", Translator.target()), () -> Translator.translateMessage(m)));
		}
		items.add(new Item(net.primlixe.aurorachat.ui.I18n.t("⧉ Копировать текст"), () -> copy(clean)));
		items.add(new Item(net.primlixe.aurorachat.ui.I18n.t("✎ Копировать с цветами (&)"), () -> copy(ChatText.amp(orig))));
		items.add(new Item(net.primlixe.aurorachat.ui.I18n.t("{ } Копировать JSON"), () -> copy(ChatText.json(orig))));
		Long t = ChatCore.timeOf(m.content());
		if (t != null) {
			String when = LocalDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
			items.add(new Item(net.primlixe.aurorachat.ui.I18n.t("⌚ Копировать время (") + when.substring(11) + ")", () -> copy(when)));
		}
		if (ChatCore.cfg().pins) items.add(0, new Item(net.primlixe.aurorachat.ui.I18n.t("📌 Закрепить"), () -> ChatCore.pin(m)));
		String s0 = ChatCore.meta(m.content()).sender();
		if (s0 == null) s0 = ChatCore.senderOf(clean);
		// своё исходящее ЛС — пункты про собеседника, а не про себя
		String selfName = mc.player != null ? mc.player.getName().getString() : null;
		if (s0 != null && s0.equalsIgnoreCase(selfName) && ChatCore.meta(m.content()).cat() == ChatFormat.Category.DM) {
			String peer = ChatFormat.dmPeer(m.content());
			if (peer != null) s0 = peer;
		}
		final String sender = s0;
		if (sender != null) {
			items.add(new Item(net.primlixe.aurorachat.ui.I18n.t("✉ Ответить в ЛС ") + sender, () -> { input.setValue("/" + firstDm() + " " + sender + " "); input.moveCursorToEnd(false); }));
			items.add(new Item(net.primlixe.aurorachat.ui.I18n.f("@ Упомянуть %s", sender), () -> input.insertText(sender + " ")));
			items.add(new Item(net.primlixe.aurorachat.ui.I18n.t("✎ Переименовать у себя"), () -> { input.setValue("/achat nick " + sender + " "); input.moveCursorToEnd(false); }));
			items.add(new Item(net.primlixe.aurorachat.ui.I18n.t("⧉ Копировать ник"), () -> copy(sender)));
			if (!net.primlixe.aurorachat.Edition.PUBLIC && ChatCore.cfg().modMenu) items.add(new Item(net.primlixe.aurorachat.ui.I18n.t("⚖ Наказать по пункту… ▸"), () -> openRules(sender, input, mx, my, screenW, screenH)));
		}
		items.add(new Item(net.primlixe.aurorachat.ui.I18n.t("⌕ Найти в истории"), () -> openLog.accept(clean.length() > 40 ? clean.substring(0, 40) : clean)));
		items.add(new Item(net.primlixe.aurorachat.ui.I18n.t("✕ Убрать из чата"), () -> ChatCore.delete(m)));
		layout(mx, my, screenW, screenH);
	}

	private int cols = 1, perCol, colW;

	/** Размер меню; если пунктов много — в несколько столбцов. */
	private void layout(int mx, int my, int screenW, int screenH) {
		Font f = Minecraft.getInstance().font;
		colW = 0;
		for (Item i : items) colW = Math.max(colW, f.width(i.label()) + 16);
		perCol = Math.max(1, Math.min(items.size(), (screenH - 24) / 13));
		cols = (items.size() + perCol - 1) / perCol;
		perCol = (items.size() + cols - 1) / cols;
		w = colW * cols;
		h = perCol * 13 + 6;
		x = Math.max(2, Math.min(mx, screenW - w - 2));
		y = Math.max(2, Math.min(my, screenH - h - 16));
	}

	/** Подменю: все пункты правил сервера — клик кладёт готовую команду в строку ввода, Enter — выдать. */
	private void openRules(String sender, EditBox input, int mx, int my, int screenW, int screenH) {
		items.clear();
		for (String[] r : Moderation.serverRules()) {
			int was = Moderation.priorCount(sender, r[0]);           // уже выдавали по этому пункту
			int step = Math.min(was + 1, r.length - 1);               // следующая ступень, не выше максимума
			String action = r[step];
			String cmd = Moderation.resolve(action, sender, r[0], was + 1, "");
			String label = r[0] + "  §7· " + Moderation.human(action) + (was > 0 ? net.primlixe.aurorachat.ui.I18n.t("  §c· было ") + was : "");
			items.add(new Item(label, () -> { input.setValue(cmd); input.moveCursorToEnd(false); }));
		}
		layout(mx, my, screenW, screenH);
		reopen = true;
	}

	private boolean reopen;

	/** Команда ЛС из настроек (первая в списке, обычно «m»). */
	static String firstDm() {
		var l = ChatCore.cfg().dmCommands;
		return l.isEmpty() ? "msg" : l.get(0);
	}

	private void copy(String s) {
		Minecraft.getInstance().keyboardHandler.setClipboard(s);
		toast = net.primlixe.aurorachat.ui.I18n.t("Скопировано");
		toastUntil = System.currentTimeMillis() + 1200;
	}

	public void render(GuiGraphicsExtractor g, Font font, int mx, int my) {
		if (toast != null && System.currentTimeMillis() < toastUntil) {
			int tw = font.width(toast) + 12;
			int tx = g.guiWidth() / 2 - tw / 2;
			g.fill(tx, 6, tx + tw, 20, 0xE0141420);
			g.outline(tx, 6, tw, 14, 0xFF60D090);
			g.text(font, "§a" + toast, tx + 6, 9, 0xFFFFFFFF, false);
		}
		if (msg == null) return;
		g.nextStratum();
		float time = (System.nanoTime() % 1_000_000_000_000L) / 1e9f;
		g.fill(x, y, x + w, y + h, 0xF0101018);
		g.outline(x, y, w, h, UI.hsb(time * 0.05f, 0.6f, 1f, 0.9f));
		for (int i = 0; i < items.size(); i++) {
			int ix = x + (i / perCol) * colW, iy = y + 3 + (i % perCol) * 13;
			boolean hover = mx >= ix && mx < ix + colW && my >= iy && my < iy + 13;
			if (hover) g.fill(ix + 1, iy, ix + colW - 1, iy + 13, 0x60FFFFFF);
			g.text(font, items.get(i).label(), ix + 6, iy + 2, hover ? 0xFFFFFFFF : 0xFFDDDDE8, false);
		}
	}

	/** true — клик обработан меню. */
	public boolean click(double mx, double my) {
		if (msg == null) return false;
		if (mx >= x && mx < x + w && my >= y && my < y + h) {
			int row = (int) ((my - y - 3) / 13), col = (int) ((mx - x) / Math.max(1, colW));
			int i = row >= 0 && row < perCol ? col * perCol + row : -1;
			if (i >= 0 && i < items.size()) {
				Runnable r = items.get(i).action();
				reopen = false;
				r.run();
				if (!reopen) msg = null; // подменю — меню остаётся открытым
			}
			return true;
		}
		msg = null;
		return true;
	}

	public static Component hint() { return Component.literal(net.primlixe.aurorachat.ui.I18n.t("§8ПКМ по сообщению — меню")); }
}
