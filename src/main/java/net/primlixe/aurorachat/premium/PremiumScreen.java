package net.primlixe.aurorachat.premium;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.primlixe.aurorachat.compat.Mc;
import net.primlixe.aurorachat.ui.I18n;
import net.primlixe.aurorachat.ui.UI;
import net.primlixe.aurorachat.ui.Widgets.Field;
import net.primlixe.aurorachat.ui.Widgets.Pill;
import net.primlixe.aurorachat.ui.Widgets.Style;

/** Магазин: что даёт Aurora Chat+, где купить и куда вставить ключ (/achat shop). */
public class PremiumScreen extends Screen {
	/** Что открывает ключ — по строке на функцию. */
	static final String[] FEATURES = {
			"Переводчик сообщений", "30+ тем и свои темы", "Поиск и история чата", "Вкладки ЛС, Клан, свои",
			"Дополнительные окна", "Палитра «&» и градиенты", "Фон-картинка и все цвета", "Напоминания, отложка",
			"АФК-автоответчик", "Антиспам и фильтр слов", "Меню действий с игроком", "Эмодзи, ники по Tab"};

	private static final int PAD = 10, ROW = 11, BTN = 18;
	private static final int GREEN = 0xFF5BE39A, RED = 0xFFFF7A7A, PINK = 0xFFFF7EC8;

	public static java.util.List<String> features() { return java.util.List.of(FEATURES); }

	private final Screen parent;
	private Field key;
	private String msg;
	private int msgColor;
	private float open;

	public PremiumScreen(Screen parent) {
		super(Component.literal(Premium.NAME));
		this.parent = parent;
	}

	private int featRows() { return (FEATURES.length + 1) / 2; }

	private int cardW() { return Math.min(360, width - 16); }

	private int cardH() {
		int h = PAD + 18 + 12 + featRows() * ROW + 6;
		h += Premium.has() ? 12 + BTN : 14 + ROW + 6 + BTN + 4 + BTN;
		return h + PAD;
	}

	private int cardX() { return (width - cardW()) / 2; }

	private int cardY() { return Math.max(4, (height - cardH()) / 2); }

	@Override
	protected void init() {
		int x0 = cardX(), x = x0 + PAD + 4, w = cardW() - 2 * PAD - 8, yb = cardY() + cardH() - PAD - BTN;
		addRenderableWidget(new Pill(x0 + cardW() - 22, cardY() + 6, 16, 14, "✕", Style.GHOST, p -> onClose()).tip("Закрыть (Esc)"));
		if (Premium.has()) {
			addRenderableWidget(new Pill(x, yb, w, BTN, I18n.t("Отлично!"), Style.PRIMARY, p -> onClose()));
			return;
		}
		int half = (w - 4) / 2;
		int y1 = yb - BTN - 4;
		addRenderableWidget(new Pill(x, y1, half, BTN, I18n.t("♥ Оформить подписку"), Style.PRIMARY, p -> Mc.openUri(Premium.SHOP_URL))
				.tip("Откроется страница подписки на Boosty"));
		addRenderableWidget(new Pill(x + half + 4, y1, w - half - 4, BTN, I18n.t("⧉ Скопировать мой ник"), Style.SECONDARY, p -> {
			String n = Premium.currentNick();
			if (n != null) minecraft.keyboardHandler.setClipboard(n);
			say(I18n.t("Ник скопирован — укажи его при покупке"), UI.SUB);
		}));
		int bw = Math.max(72, font.width(I18n.t("Активировать")) + 14);
		key = new Field(font, x, yb, w - 2 * bw - 8, BTN, I18n.t("ключ AURORA-…"));
		key.setMaxLength(400);
		addRenderableWidget(key);
		addRenderableWidget(new Pill(x + w - 2 * bw - 4, yb, bw, BTN, I18n.t("Вставить"), Style.SECONDARY,
				p -> { key.setValue(minecraft.keyboardHandler.getClipboard().trim()); activate(key.getValue()); })
				.tip("Вставить ключ из буфера обмена и активировать"));
		addRenderableWidget(new Pill(x + w - bw, yb, bw, BTN, I18n.t("Активировать"), Style.PRIMARY, p -> activate(key.getValue())));
	}

	private void activate(String k) {
		String err = Premium.activate(k);
		if (err != null) {
			say(I18n.t(err), RED);
			return;
		}
		rebuildWidgets();
		say(I18n.t("Готово! Все функции открыты ✔"), GREEN);
	}

	private void say(String s, int color) { msg = s; msgColor = color; }

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
		g.fill(0, 0, width, height, 0xC8070910);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
		open = UI.approach(open, 1, 0.18f);
		float e = UI.ease(open);
		int x0 = cardX(), y0 = cardY(), x1 = x0 + cardW(), y1 = y0 + cardH();
		UI.shadow(g, x0, y0, x1, y1, 10, e);
		UI.roundGradient(g, x0, y0, x1, y1, 10, UI.alpha(0xFF1A1730, e), UI.alpha(0xFF0F1222, e));
		UI.roundOutline(g, x0, y0, x1, y1, 10, UI.alpha(UI.accent(UI.time() * 0.08f), 0.55f * e));
		UI.hGradient(g, x0 + 12, y0, x1 - 12, y0 + 2, UI.alpha(0xFFB18CFF, e), UI.alpha(0xFF4FE3C1, e));

		int x = x0 + PAD + 4, y = y0 + PAD;
		UI.logo(g, font, Premium.NAME, x, y, 1.4f);
		y += 18;
		if (Premium.has()) {
			String st = Premium.status();
			UI.text(g, font, I18n.t("✔ Активирован: ") + (st == null ? "" : st), x, y, GREEN, false);
			y += 12;
			UI.text(g, font, I18n.t("Спасибо, что поддерживаешь мод! Открыто всё:"), x, y, UI.SUB, false);
		} else {
			UI.text(g, font, I18n.t("Сейчас бесплатная версия. Ключ открывает всё:"), x, y, UI.SUB, false);
		}
		y += 12;
		int colW = (cardW() - 2 * PAD - 8) / 2;
		for (int i = 0; i < FEATURES.length; i++) {
			int cx = x + (i % 2) * colW, cy = y + (i / 2) * ROW;
			UI.text(g, font, "✦", cx, cy, PINK, false);
			UI.text(g, font, UI.trim(font, I18n.t(FEATURES[i]), colW - 14), cx + 10, cy, UI.TEXT, false);
		}
		y += featRows() * ROW + 6;
		if (!Premium.has()) {
			String price = I18n.t(Premium.PRICE);
			int pw = UI.pill(g, font, x, y, price, PINK, 0xFF1A0B14);
			UI.text(g, font, UI.trim(font, I18n.t("бесплатно навсегда: окно, 3 темы, G / L"), cardW() - 2 * PAD - 16 - pw), x + pw + 6, y + 2, UI.MUTED, false);
			y += 14;
			String nick = Premium.currentNick();
			UI.text(g, font, UI.trim(font, I18n.t("На Boosty укажи ник ") + "§f" + (nick == null ? "?" : nick) + "§r" + I18n.t(", ключ вставь ниже"),
					cardW() - 2 * PAD - 8), x, y, UI.SUB, false);
		}
		if (msg != null) UI.centered(g, font, msg, (x0 + x1) / 2, Math.min(height - 10, y1 + 5), msgColor);
		super.extractRenderState(g, mx, my, pt);
	}

	@Override
	public void onClose() { Mc.setScreen(parent); }

	@Override
	public boolean isPauseScreen() { return false; }
}
