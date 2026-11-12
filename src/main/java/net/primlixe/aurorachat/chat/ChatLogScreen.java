package net.primlixe.aurorachat.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.primlixe.aurorachat.ui.UI;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Просмотр журнала чата: сервера → дни → сообщения, поиск с подсветкой (по дню или по всем дням),
 * фильтр по типу, копирование строки (ЛКМ — текст, ПКМ — с цветами), удаление дня, открытие папки.
 */
public class ChatLogScreen extends Screen {
	private enum Filter { ALL(net.primlixe.aurorachat.ui.I18n.t("Все")), CHAT(net.primlixe.aurorachat.ui.I18n.t("Игроки")), SYS(net.primlixe.aurorachat.ui.I18n.t("Система"));
		final String ru; Filter(String ru) { this.ru = ru; } }

	private static final int HEADER = 24, ROW = 11;

	private final Screen parent;
	private final String initialQuery;

	private List<String> servers = List.of();
	private List<String> days = List.of();
	private String server, day;
	private boolean allDays;
	private Filter filter = Filter.ALL;

	private List<ChatLog.Entry> loaded = new ArrayList<>();
	private final List<ChatLog.Entry> shown = new ArrayList<>();
	// быстрый поиск: нижний регистр — один раз на загрузку, разбор сообщения — лениво и с запоминанием
	private String[] lower = new String[0];
	private ChatFormat.Meta[] metas = new ChatFormat.Meta[0];
	/** Номера строк loaded, попавших в shown (для сужения поиска при дописывании). */
	private int[] shownIdx = new int[0];
	private int shownN;
	private String lastQuery;
	private Filter lastFilter;
	/** Отложенный поиск на больших журналах: когда запустить (0 — не надо). */
	private long searchAt;

	private EditBox search;
	private int sideX0, sideX1, listX0, listX1, listY0, listY1, serversY1;
	private float scroll, sideScroll;
	private boolean confirmDelete;
	private String toast;
	private long toastUntil;

	public ChatLogScreen(Screen parent, String query) {
		super(Component.literal(net.primlixe.aurorachat.ui.I18n.t("История чата")));
		this.parent = parent;
		this.initialQuery = query == null ? "" : query;
		this.allDays = !this.initialQuery.isEmpty();
	}

	@Override
	protected void init() {
		boolean first = servers.isEmpty() && server == null;
		servers = ChatLog.servers();
		if (first) {
			String cur = ChatLog.serverKey();
			server = servers.contains(cur) ? cur : (servers.isEmpty() ? null : servers.get(0));
			days = server == null ? List.of() : ChatLog.days(server);
			day = days.contains(ChatLog.today()) ? ChatLog.today() : (days.isEmpty() ? null : days.get(0));
		}
		int pad = 6;
		sideX0 = pad;
		sideX1 = pad + Math.max(90, Math.min(150, width / 4));
		listX0 = sideX1 + 8;
		listX1 = width - pad;
		listY0 = HEADER + 28;
		listY1 = height - 30;
		serversY1 = HEADER + 6 + (height - HEADER - 40) / 2;

		String q = search == null ? initialQuery : search.getValue();
		search = new EditBox(font, listX0, HEADER + 6, Math.max(80, listX1 - listX0 - 190), 16, Component.literal(net.primlixe.aurorachat.ui.I18n.t("Поиск")));
		search.setHint(Component.literal(net.primlixe.aurorachat.ui.I18n.t("§8⌕ поиск · ник:Steve канал:G тип:лс дата:вчера после:18:00")));
		search.setTooltip(Tooltip.create(Component.literal(net.primlixe.aurorachat.ui.I18n.t("Умный поиск: ") + SearchQuery.HELP)));
		search.setMaxLength(100);
		search.setValue(q);
		search.setResponder(s -> {
			scroll = 0;
			// на больших журналах ждём паузу в наборе (~0,12 с), чтобы не пересчитывать на каждую букву
			if (loaded.size() > 8000) searchAt = System.currentTimeMillis() + 120;
			else onSearch();
		});
		addRenderableWidget(search);
		int bx = search.getX() + search.getWidth() + 4;
		addRenderableWidget(Button.builder(Component.literal(allDays ? net.primlixe.aurorachat.ui.I18n.t("§aВсе дни") : net.primlixe.aurorachat.ui.I18n.t("§7Один день")), b -> {
			allDays = !allDays;
			reload();
			b.setMessage(Component.literal(allDays ? net.primlixe.aurorachat.ui.I18n.t("§aВсе дни") : net.primlixe.aurorachat.ui.I18n.t("§7Один день")));
		}).bounds(bx, HEADER + 5, 86, 18).tooltip(Tooltip.create(Component.literal(net.primlixe.aurorachat.ui.I18n.t("Искать по всем дням выбранного сервера")))).build());
		addRenderableWidget(Button.builder(Component.literal(net.primlixe.aurorachat.ui.I18n.t("Тип: ") + filter.ru), b -> {
			filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
			b.setMessage(Component.literal(net.primlixe.aurorachat.ui.I18n.t("Тип: ") + filter.ru));
			scroll = 0;
			applyFilter();
		}).bounds(bx + 90, HEADER + 5, 92, 18).build());

		int by = height - 24, bw = Math.min(104, (listX1 - listX0 - 8) / 3);
		addRenderableWidget(Button.builder(Component.literal(net.primlixe.aurorachat.ui.I18n.t("⌂ Папка логов")), b -> {
			try { java.nio.file.Files.createDirectories(ChatLog.root()); } catch (Exception ignored) { }
			net.minecraft.util.Util.getPlatform().openPath(server != null ? ChatLog.root().resolve(server) : ChatLog.root());
		}).bounds(listX0, by, bw, 18).build());
		addRenderableWidget(Button.builder(Component.literal(net.primlixe.aurorachat.ui.I18n.t("⧉ Копировать всё")), b -> {
			StringBuilder sb = new StringBuilder();
			for (ChatLog.Entry e : shown) sb.append('[').append(e.day()).append(' ').append(e.time()).append("] ").append(e.plain()).append('\n');
			Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
			toast(net.primlixe.aurorachat.ui.I18n.t("Скопировано строк: ") + shown.size());
		}).bounds(listX0 + bw + 4, by, bw, 18).build());
		addRenderableWidget(Button.builder(Component.literal(net.primlixe.aurorachat.ui.I18n.t("✕ Удалить день")), b -> {
			if (server == null || day == null || allDays) return;
			if (!confirmDelete) { confirmDelete = true; b.setMessage(Component.literal(net.primlixe.aurorachat.ui.I18n.t("§cТочно удалить?"))); return; }
			ChatLog.deleteDay(server, day);
			confirmDelete = false;
			days = ChatLog.days(server);
			day = days.isEmpty() ? null : days.get(0);
			reload();
			rebuildWidgets();
			toast(net.primlixe.aurorachat.ui.I18n.t("День удалён"));
		}).bounds(listX0 + (bw + 4) * 2, by, bw, 18).build());
		addRenderableWidget(Button.builder(Component.literal(net.primlixe.aurorachat.ui.I18n.t("Готово")), b -> onClose()).bounds(width - pad - 54, 3, 54, 18).build());
		if (first) reload();
		else applyFilter();
	}

	private void reload() {
		loaded = new ArrayList<>();
		if (server != null) {
			if (allDays) {
				List<String> ds = new ArrayList<>(days);
				java.util.Collections.reverse(ds); // от старых к новым
				for (String d : ds) {
					loaded.addAll(ChatLog.read(server, d));
					if (loaded.size() > 60000) break;
				}
			} else if (day != null) loaded = ChatLog.read(server, day);
		}
		lower = new String[loaded.size()];
		for (int i = 0; i < lower.length; i++) lower[i] = loaded.get(i).plain().toLowerCase(Locale.ROOT);
		metas = new ChatFormat.Meta[loaded.size()];
		lastQuery = null; // другой набор строк — сужать нечего
		scroll = Float.MAX_VALUE; // к последним сообщениям
		applyFilter();
	}

	private static final ChatFormat.Meta NO_META = new ChatFormat.Meta(0, ChatFormat.Category.SYSTEM, null, 1, (char) 0);

	private void applyFilter() {
		searchAt = 0;
		String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		SearchQuery sq = SearchQuery.parse(q);
		boolean needMeta = sq.sender != null || sq.channel != 0 || sq.cats != null;
		var mc = Minecraft.getInstance();
		String self = mc.player != null ? mc.player.getName().getString() : null;
		// дописал к прежнему запросу обычный текст — ищем только среди уже найденного
		boolean narrow = lastQuery != null && filter == lastFilter && !lastQuery.isEmpty() && q.startsWith(lastQuery)
				&& q.indexOf(':') < 0 && q.indexOf('-') < 0;
		int[] from = narrow ? java.util.Arrays.copyOf(shownIdx, shownN) : null;
		int count = narrow ? from.length : loaded.size();
		if (shownIdx.length < loaded.size()) shownIdx = new int[loaded.size()];
		shownN = 0;
		shown.clear();
		for (int k = 0; k < count; k++) {
			int i = narrow ? from[k] : k;
			ChatLog.Entry e = loaded.get(i);
			if (filter == Filter.CHAT && !e.type().equals("CHAT")) continue;
			if (filter == Filter.SYS && e.type().equals("CHAT")) continue;
			if (!sq.isEmpty()) {
				ChatFormat.Meta m = NO_META;
				if (needMeta) {
					m = metas[i];
					if (m == null) m = metas[i] = ChatFormat.classify(Component.literal(e.plain()), e.plain(), e.type().equals("CHAT"), self, false);
				}
				if (!sq.matchesLog(lower[i], e.day(), e.time(), m)) continue;
			}
			shownIdx[shownN++] = i;
			shown.add(e);
		}
		lastQuery = q;
		lastFilter = filter;
		clampScroll();
	}

	/** Поиск по дате — сразу открываем нужный день (если он есть). */
	private void onSearch() {
		SearchQuery sq = SearchQuery.parse(search.getValue());
		if (sq.date != null && server != null) {
			String d = sq.date.toString();
			if (days.contains(d) && (!d.equals(day) || allDays)) {
				day = d;
				allDays = false;
				reload();
				return;
			}
		}
		applyFilter();
	}

	private int visibleRows() { return Math.max(1, (listY1 - listY0) / ROW); }

	private void clampScroll() {
		float max = Math.max(0, shown.size() - visibleRows());
		scroll = Math.max(0, Math.min(max, scroll));
	}

	private void toast(String s) { toast = s; toastUntil = System.currentTimeMillis() + 1500; }

	// ====================================================================== отрисовка

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
		if (searchAt != 0 && System.currentTimeMillis() >= searchAt) onSearch(); // отложенный поиск
		float time = (System.nanoTime() % 1_000_000_000_000L) / 1e9f;
		int a1 = UI.hsb(time * 0.05f + 0.5f, 0.6f, 1f, 1f), a2 = UI.hsb(time * 0.05f + 0.8f, 0.6f, 1f, 1f);
		g.fill(0, 0, width, HEADER, 0xC0080810);
		for (int i = 0; i < width; i += 4) g.fill(i, HEADER - 1, i + 4, HEADER + 1, UI.mix(a1, a2, i / (float) width));
		g.pose().pushMatrix();
		g.pose().translate(sideX0 + 2, 6);
		g.pose().scale(1.4f, 1.4f);
		g.text(font, net.primlixe.aurorachat.ui.I18n.t("☰ ИСТОРИЯ ЧАТА"), 0, 0, a1, true);
		g.pose().popMatrix();
		String stat = shown.size() + " / " + loaded.size() + net.primlixe.aurorachat.ui.I18n.t(" сообщ.");
		if (sideX0 + 150 + font.width(stat) < width - 66) g.text(font, "§7" + stat, sideX0 + 150, 8, 0xFFFFFFFF, false);

		panel(g, sideX0 - 3, HEADER + 3, sideX1 + 3, height - 3);
		panel(g, listX0 - 4, HEADER + 3, listX1 + 3, height - 3);

		// сервера
		g.text(font, net.primlixe.aurorachat.ui.I18n.t("§lСервера"), sideX0 + 2, HEADER + 7, 0xFFFFFFFF, true);
		int y = HEADER + 19;
		g.enableScissor(sideX0, y, sideX1, serversY1);
		for (String s : servers) {
			boolean sel = s.equals(server), hov = mx >= sideX0 && mx < sideX1 && my >= y && my < y + 12 && my < serversY1;
			if (sel || hov) g.fill(sideX0, y, sideX1, y + 12, sel ? 0x80503070 : 0x40FFFFFF);
			g.text(font, trim((sel ? "§d▸ §f" : "§7") + s, sideX1 - sideX0 - 4), sideX0 + 2, y + 2, 0xFFFFFFFF, false);
			y += 12;
		}
		if (servers.isEmpty()) g.text(font, net.primlixe.aurorachat.ui.I18n.t("§8пока пусто"), sideX0 + 2, y + 2, 0xFFFFFFFF, false);
		g.disableScissor();
		// дни
		g.text(font, net.primlixe.aurorachat.ui.I18n.t("§lДни"), sideX0 + 2, serversY1 + 4, 0xFFFFFFFF, true);
		y = serversY1 + 16 - (int) sideScroll;
		g.enableScissor(sideX0, serversY1 + 15, sideX1, height - 6);
		for (String d : days) {
			boolean sel = d.equals(day) && !allDays, hov = mx >= sideX0 && mx < sideX1 && my >= y && my < y + 12 && my > serversY1 + 15;
			if (sel || hov) g.fill(sideX0, y, sideX1, y + 12, sel ? 0x80305070 : 0x40FFFFFF);
			String label = d.equals(ChatLog.today()) ? d + net.primlixe.aurorachat.ui.I18n.t(" §a(сегодня)") : d;
			g.text(font, (sel ? "§b▸ §f" : "§7") + label, sideX0 + 2, y + 2, 0xFFFFFFFF, false);
			y += 12;
		}
		g.disableScissor();

		// сообщения
		SearchQuery hl = SearchQuery.parse(search.getValue());
		String q = hl.words.isEmpty() ? "" : hl.words.get(0);
		int rows = visibleRows();
		int start = (int) scroll;
		g.enableScissor(listX0 - 2, listY0, listX1, listY1);
		ChatLog.Entry hovered = null;
		String lastDay = null;
		for (int i = 0; i < rows && start + i < shown.size(); i++) {
			ChatLog.Entry e = shown.get(start + i);
			int ry = listY0 + i * ROW;
			boolean hov = mx >= listX0 && mx < listX1 && my >= ry && my < ry + ROW;
			if (hov) { g.fill(listX0 - 2, ry, listX1, ry + ROW, 0x30FFFFFF); hovered = e; }
			int x = listX0;
			String when = allDays ? e.day().substring(5) + " " + e.time() : e.time();
			g.text(font, "§7" + when, x, ry + 1, 0xFFFFFFFF, false);
			x += font.width(when) + 4;
			String badge = switch (e.type()) {
				case "CHAT" -> "§b●";
				case "SESSION" -> "§d✦";
				case "CLIENT" -> "§8●";
				default -> "§e●";
			};
			g.text(font, badge, x, ry + 1, 0xFFFFFFFF, false);
			x += 9;
			boolean trOn = ChatCore.cfg().translate;
			String tr = trOn ? TR.get(e) : null;
			// кнопка ⇄ у строки под мышью (и у переведённых — всегда, чтобы вернуть оригинал)
			if (trOn && (hov || tr != null)) {
				boolean bh = hov && mx >= listX1 - 14;
				g.text(font, TR_BUSY.contains(e) ? "§7…" : (bh ? "§f⇄" : tr != null ? "§b⇄" : "§8⇄"), listX1 - 11, ry + 1, 0xFFFFFFFF, false);
			}
			int avail = listX1 - x - 2 - (trOn ? 14 : 0);
			String text = trim(tr != null ? "§b§o" + tr : e.legacy(), avail);
			// подсветка найденного
			if (!q.isEmpty() && tr == null) { // в переведённой строке позиции оригинала не совпадают
				int idx = e.plain().toLowerCase(Locale.ROOT).indexOf(q);
				if (idx >= 0) {
					int hx = x + font.width(e.plain().substring(0, idx));
					int hw = font.width(e.plain().substring(idx, Math.min(e.plain().length(), idx + q.length())));
					if (hx < listX1) g.fill(hx - 1, ry, Math.min(listX1, hx + hw + 1), ry + ROW, 0x80C8A000);
				}
			}
			g.text(font, text, x, ry + 1, 0xFFFFFFFF, false);
			lastDay = e.day();
		}
		g.disableScissor();
		if (shown.isEmpty()) g.centeredText(font, Component.literal(loaded.isEmpty() ? net.primlixe.aurorachat.ui.I18n.t("§8Здесь появятся сообщения чата") : net.primlixe.aurorachat.ui.I18n.t("§8Ничего не найдено")), (listX0 + listX1) / 2, (listY0 + listY1) / 2, 0xFFFFFFFF);
		// полоса прокрутки
		if (shown.size() > rows) {
			int h = listY1 - listY0, bar = Math.max(12, h * rows / shown.size());
			int yb = listY0 + (int) ((h - bar) * (scroll / Math.max(1, shown.size() - rows)));
			g.fill(listX1 - 2, yb, listX1, yb + bar, 0x90FFFFFF);
		}
		super.extractRenderState(g, mx, my, partial);
		String hovTr = hovered != null ? TR.get(hovered) : null;
		if (hovered != null && hovered.equals(shownAt(mx, my)) && mx >= listX1 - 14 && ChatCore.cfg().translate) {
			g.setTooltipForNextFrame(font, Component.literal(hovTr != null ? net.primlixe.aurorachat.ui.I18n.t("Показать оригинал") : net.primlixe.aurorachat.ui.I18n.t("Перевести на") + " " + Translator.target()), mx, my);
		} else if (hovTr != null) {
			List<FormattedCharSequence> lines = font.split(Component.literal(hovered.legacy() + "\n§b⇄ §f" + hovTr), Math.min(320, width - 20));
			g.setTooltipForNextFrame(font, lines, mx, my);
		} else if (hovered != null && font.width(hovered.plain()) > listX1 - listX0 - 60) {
			List<FormattedCharSequence> lines = font.split(Component.literal(hovered.legacy()), Math.min(320, width - 20));
			g.setTooltipForNextFrame(font, lines, mx, my);
		}
		if (toast != null && System.currentTimeMillis() < toastUntil) {
			int tw = font.width(toast) + 12, tx = width / 2 - tw / 2;
			g.fill(tx, HEADER + 30, tx + tw, HEADER + 44, 0xE0141420);
			g.outline(tx, HEADER + 30, tw, 14, 0xFF60D090);
			g.text(font, "§a" + toast, tx + 6, HEADER + 33, 0xFFFFFFFF, false);
		}
		int hintX = listX0 + 3 * (Math.min(104, (listX1 - listX0 - 8) / 3) + 4);
		String hint = ChatCore.cfg().translate ? net.primlixe.aurorachat.ui.I18n.t("§8ЛКМ — копировать · ПКМ — с цветами · ⇄ — перевод") : net.primlixe.aurorachat.ui.I18n.t("§8ЛКМ — копировать · ПКМ — с цветами");
		if (hintX + font.width(hint) < listX1) g.text(font, hint, hintX, height - 19, 0xFFFFFFFF, false);
	}

	/** Переводы строк журнала (на время жизни игры) и строки, которые сейчас переводятся. */
	private static final java.util.Map<ChatLog.Entry, String> TR = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.Set<ChatLog.Entry> TR_BUSY = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private ChatLog.Entry shownAt(double mx, double my) {
		if (mx < listX0 || mx >= listX1 || my < listY0 || my >= listY1) return null;
		int i = (int) scroll + (int) ((my - listY0) / ROW);
		return i >= 0 && i < shown.size() ? shown.get(i) : null;
	}

	private void translateRow(ChatLog.Entry en) {
		if (TR.remove(en) != null || TR_BUSY.contains(en)) return; // второй клик — оригинал
		String body = Translator.bodyOf(en.plain(), ChatCore.senderOf(en.plain()));
		if (body.isBlank()) { toast(net.primlixe.aurorachat.ui.I18n.t("Нечего переводить")); return; }
		String to = Translator.target();
		TR_BUSY.add(en);
		Translator.translate(body, to).whenComplete((r, err) -> Minecraft.getInstance().execute(() -> {
			TR_BUSY.remove(en);
			if (err != null) { toast(net.primlixe.aurorachat.ui.I18n.t("Перевод не удался — нет связи")); return; }
			if (r.from().equalsIgnoreCase(to)) { toast(net.primlixe.aurorachat.ui.I18n.t("Уже на этом языке")); return; }
			TR.put(en, r.text());
			if (TR.size() > 500) TR.clear(); // не копим бесконечно
		}));
	}

	private void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.fill(x0, y0, x1, y1, 0xA00C0C14);
		g.outline(x0, y0, x1 - x0, y1 - y0, 0x30FFFFFF);
	}

	private String trim(String s, int w) {
		if (font.width(s) <= w) return s;
		while (s.length() > 3 && font.width(s + "…") > w) s = s.substring(0, s.length() - 1);
		return s + "…";
	}

	// ====================================================================== ввод

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (super.mouseClicked(e, dbl)) return true;
		double mx = e.x(), my = e.y();
		if (mx >= sideX0 && mx < sideX1) {
			if (my >= HEADER + 19 && my < serversY1) {
				int i = (int) ((my - HEADER - 19) / 12);
				if (i >= 0 && i < servers.size()) {
					server = servers.get(i);
					days = ChatLog.days(server);
					day = days.isEmpty() ? null : days.get(0);
					sideScroll = 0;
					reload();
					return true;
				}
			}
			if (my > serversY1 + 15) {
				int i = (int) ((my - serversY1 - 16 + sideScroll) / 12);
				if (i >= 0 && i < days.size()) {
					day = days.get(i);
					allDays = false;
					confirmDelete = false;
					reload();
					rebuildWidgets();
					return true;
				}
			}
		}
		if (mx >= listX0 && mx < listX1 && my >= listY0 && my < listY1) {
			int i = (int) scroll + (int) ((my - listY0) / ROW);
			if (i >= 0 && i < shown.size()) {
				ChatLog.Entry en = shown.get(i);
				if (ChatCore.cfg().translate && mx >= listX1 - 14 && e.button() == 0) {
					translateRow(en);
					return true;
				}
				Minecraft.getInstance().keyboardHandler.setClipboard(e.button() == 1 ? ChatText.ampFromLegacy(en.legacy()) : en.plain());
				toast(e.button() == 1 ? net.primlixe.aurorachat.ui.I18n.t("Скопировано с цветами") : net.primlixe.aurorachat.ui.I18n.t("Скопировано"));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (mx >= listX0 && mx < listX1) {
			scroll -= (float) sy * 3;
			clampScroll();
			return true;
		}
		if (mx >= sideX0 && mx < sideX1 && my > serversY1) {
			sideScroll = Math.max(0, Math.min(Math.max(0, days.size() * 12 - (height - serversY1 - 25)), sideScroll - (float) sy * 12));
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == GLFW.GLFW_KEY_PAGE_UP) { scroll -= visibleRows(); clampScroll(); return true; }
		if (e.key() == GLFW.GLFW_KEY_PAGE_DOWN) { scroll += visibleRows(); clampScroll(); return true; }
		if (e.key() == GLFW.GLFW_KEY_END && !search.isFocused()) { scroll = Float.MAX_VALUE; clampScroll(); return true; }
		return super.keyPressed(e);
	}

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public void onClose() { net.primlixe.aurorachat.compat.Mc.setScreen(parent); }
}
