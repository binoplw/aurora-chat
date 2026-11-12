package net.primlixe.aurorachat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.primlixe.aurorachat.chat.Channels;
import net.primlixe.aurorachat.chat.ChatCore;
import net.primlixe.aurorachat.chat.ChatFormat;
import net.primlixe.aurorachat.chat.ChatHud;
import net.primlixe.aurorachat.chat.ChatLogScreen;
import net.primlixe.aurorachat.chat.ChatMenu;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.gui.ChatSettingsScreen;
import net.primlixe.aurorachat.ui.UI;
import net.primlixe.aurorachat.ui.Widgets;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Открытый чат: стильная строка ввода с каналом G/L, поиск, эмодзи, история, меню ПКМ, клики по окну Aurora Chat. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
	@Shadow protected EditBox input;

	@Shadow
	private boolean handleComponentClicked(Style style, boolean insert) { return false; }

	@Unique private Widgets.Field aurorachat$search;
	@Unique private final ChatMenu aurorachat$menu = new ChatMenu();
	@Unique private boolean aurorachat$emojiOpen;
	@Unique private final List<int[]> aurorachat$emojiRects = new ArrayList<>();
	@Unique private final List<String> aurorachat$emojiValues = new ArrayList<>();
	@Unique private final List<int[]> aurorachat$emojiCatRects = new ArrayList<>();
	@Unique private int aurorachat$emojiCat = -1;
	@Unique private int[] aurorachat$emojiBtn, aurorachat$logBtn, aurorachat$chBtn;
	@Unique private float aurorachat$chAnim = -1;
	@Unique private boolean aurorachat$sent;
	@Unique private int[] aurorachat$laterBtn;
	@Unique private boolean aurorachat$laterOpen;
	// быстрые фразы: {x0, y0, x1, y1, индекс}; индекс -1 — «＋», -2 — «↶ вернуть»
	@Unique private final List<int[]> aurorachat$quickRects = new ArrayList<>();
	@Unique private int[] aurorachat$quickBtn;
	@Unique private int[] aurorachat$trBtn;
	@Unique private boolean aurorachat$trBusy;
	@Unique private int aurorachat$quickTop = Integer.MAX_VALUE;
	@Unique private final java.util.Map<String, long[]> aurorachat$quickFlash = new java.util.HashMap<>(); // текст → {когда, сколько подряд}
	@Unique private String aurorachat$quickRemoved;
	@Unique private int aurorachat$quickRemovedAt;
	@Unique private long aurorachat$quickRemovedTime, aurorachat$quickAddedTime;
	/** Повтор из истории (↑ + Enter): когда отправили и сколько раз подряд — для отметки «✔ отправлено». */
	@Unique private long aurorachat$repeatAt, aurorachat$repeatLast;
	@Unique private int aurorachat$repeatN;
	@Unique private String aurorachat$altKeep;
	@Unique private int aurorachat$altKeepCursor;
	@Unique private final List<int[]> aurorachat$laterRects = new ArrayList<>();
	@Unique private static final int[] AURORACHAT$DELAYS = {1, 5, 15, 30, 60};
	@Unique private final net.primlixe.aurorachat.chat.ColorPicker aurorachat$picker = new net.primlixe.aurorachat.chat.ColorPicker();
	@Unique private boolean aurorachat$pickerOpen;
	@Unique private int[] aurorachat$pickerBtn;
	/** Когда открылась панель — для плавного появления. */
	@Unique private long aurorachat$pickerAt, aurorachat$emojiAt, aurorachat$laterAt;
	/** Tab по никам: варианты, текущий, где вставлено. */
	@Unique private List<String> aurorachat$nicks;
	@Unique private int aurorachat$nickIdx, aurorachat$nickStart, aurorachat$nickEnd;
	@Unique private String aurorachat$nickValue, aurorachat$nickPrefix = "";

	@Unique
	private boolean aurorachat$completeNick(boolean back) {
		String v = input.getValue();
		// повторный Tab сразу после дополнения — следующий ник
		if (aurorachat$nicks != null && v.equals(aurorachat$nickValue)) {
			if (aurorachat$nicks.size() < 2) return true;
			int n = aurorachat$nicks.size();
			aurorachat$nickIdx = ((aurorachat$nickIdx + (back ? -1 : 1)) % n + n) % n;
			aurorachat$putNick(v.substring(0, aurorachat$nickStart), v.substring(aurorachat$nickEnd));
			return true;
		}
		int cur = input.getCursorPosition();
		int start = net.primlixe.aurorachat.chat.NickComplete.wordStart(v, cur);
		if (!net.primlixe.aurorachat.chat.NickComplete.applicable(v, start)) return false;
		String word = v.substring(start, cur);
		aurorachat$nickPrefix = word.startsWith("@") ? "@" : "";
		String pre = word.substring(aurorachat$nickPrefix.length());
		if (pre.isEmpty()) return false;
		List<String> m = net.primlixe.aurorachat.chat.NickComplete.matches(pre);
		if (m.isEmpty()) return false;
		aurorachat$nicks = m;
		aurorachat$nickIdx = 0;
		aurorachat$nickStart = start;
		aurorachat$putNick(v.substring(0, start), v.substring(cur));
		return true;
	}

	@Unique
	private void aurorachat$putNick(String before, String after) {
		String ins = aurorachat$nickPrefix + aurorachat$nicks.get(aurorachat$nickIdx) + " ";
		if (after.startsWith(" ")) after = after.substring(1);
		String nv = before + ins + after;
		aurorachat$nickEnd = before.length() + ins.length();
		input.setValue(nv);
		input.setCursorPosition(aurorachat$nickEnd);
		input.setHighlightPos(aurorachat$nickEnd);
		aurorachat$nickValue = nv;
	}

	/** Плавное появление панели: растёт от точки привязки (ax, ay). Вернуть true — надо сделать popMatrix. */
	@Unique
	private static boolean aurorachat$popIn(GuiGraphicsExtractor g, long since, float ax, float ay) {
		ChatConfig c = ChatConfig.get();
		if (!c.popupAnim || !net.primlixe.aurorachat.ui.Perf.anim()) return false;
		float e = net.primlixe.aurorachat.ui.Perf.appear(since, c.popupTime);
		if (e >= 1) return false;
		float s = 0.9f + 0.1f * e;
		g.pose().pushMatrix();
		g.pose().translate(ax, ay + (1 - e) * 5);
		g.pose().scale(s, s);
		g.pose().translate(-ax, -ay);
		return true;
	}

	protected ChatScreenMixin(Component title) { super(title); }

	@Unique
	private static boolean aurorachat$channelChip() {
		ChatConfig c = ChatConfig.get();
		return c.enabled && c.channels && c.styledInput && c.inputChannelChip;
	}

	@Shadow private net.minecraft.client.gui.components.CommandSuggestions commandSuggestions;
	/** Позиция в своей истории ввода: -1 — «сейчас пишу». */
	@Unique private int aurorachat$hpos = -1;
	/** Что было набрано до того, как начали листать (оно же — начало для поиска). */
	@Unique private String aurorachat$hbuf = "";

	/** ↑ / ↓: своя история без повторов; если что-то набрано — только строки, которые с этого начинаются. */
	@Inject(method = "moveInHistory", at = @At("HEAD"), cancellable = true)
	private void aurorachat$history(int dir, CallbackInfo ci) {
		ChatConfig c = ChatConfig.get();
		if (!ChatCore.active() || !c.betterHistory) return;
		ci.cancel();
		var list = net.primlixe.aurorachat.chat.InputHistory.list();
		int n = list.size();
		int pos = aurorachat$hpos < 0 || aurorachat$hpos > n ? n : aurorachat$hpos;
		if (pos == n) aurorachat$hbuf = input.getValue();
		String prefix = c.historyPrefix ? aurorachat$hbuf : "";
		int i = net.primlixe.aurorachat.chat.InputHistory.find(pos, dir < 0 ? -1 : 1, prefix, input.getValue());
		if (i < 0) return; // старее ничего нет — остаёмся
		if (i >= n) {
			aurorachat$hpos = -1;
			input.setValue(aurorachat$hbuf);
			return;
		}
		aurorachat$hpos = i;
		input.setValue(list.get(i));
		commandSuggestions.setAllowSuggestions(false);
	}

	@Inject(method = "handleChatInput", at = @At("RETURN"))
	private void aurorachat$sentDone(String msg, boolean addToHistory, CallbackInfo ci) {
		net.primlixe.aurorachat.chat.InputHistory.clearPending();
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void aurorachat$init(CallbackInfo ci) {
		if (!ChatCore.active()) return;
		ChatHud.setQuery("");
		ChatHud.setSettingsOpener(() -> net.primlixe.aurorachat.compat.Mc.setScreen(new ChatSettingsScreen((Screen) (Object) this)));
		ChatHud.setInputSetter(text -> {
			input.setValue(text);
			input.moveCursorToEnd(false);
			setFocused(input);
		});
		aurorachat$sent = false;
		// черновик: недописанное в прошлый раз
		if (ChatCore.cfg().drafts && input.getValue().isEmpty() && !ChatCore.cfg().draft.isEmpty()) {
			input.setValue(ChatCore.cfg().draft);
			input.moveCursorToEnd(false);
		}
		if (aurorachat$channelChip()) {
			input.setX(4 + 20);
			input.setWidth(width - 8 - 20);
		}
		if (ChatCore.cfg().searchBar) {
			int w = Math.min(150, width / 3);
			// поиск выбирается только мышью: стрелки ↑/↓ должны листать историю команд, а не прыгать в поиск
			aurorachat$search = new Widgets.Field(font, width - w - 4, height - 32, w, 16, net.primlixe.aurorachat.ui.I18n.t("⌕ поиск · ник: канал: тип: дата:")) {
				@Override
				public net.minecraft.client.gui.ComponentPath nextFocusPath(net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
					return null;
				}
			};
			aurorachat$search.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(net.primlixe.aurorachat.ui.I18n.t("Умный поиск: ") + net.primlixe.aurorachat.chat.SearchQuery.HELP)));
			aurorachat$search.setMaxLength(64);
			aurorachat$search.setResponder(this::aurorachat$filter);
			addRenderableWidget(aurorachat$search);
		}
	}

	@Unique
	private void aurorachat$filter(String q) {
		var chat = net.primlixe.aurorachat.compat.Mc.chat();
		if (ChatHud.enabled()) { ChatHud.setQuery(q); return; }
		String query = q.trim().toLowerCase(Locale.ROOT);
		if (query.isEmpty()) chat.setVisibleMessageFilter(m -> true);
		else chat.setVisibleMessageFilter(m -> m.content().getString().toLowerCase(Locale.ROOT).contains(query));
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void aurorachat$removed(CallbackInfo ci) {
		ChatHud.endDrag();
		net.primlixe.aurorachat.chat.ExtraWindows.endDrag();
		// черновик: сохраняем недописанное (не команды-заготовки вроде одиночного «/»)
		if (ChatCore.active() && ChatCore.cfg().drafts && input != null) {
			String v = aurorachat$sent ? "" : input.getValue();
			if (v.equals("/")) v = "";
			if (!v.equals(ChatCore.cfg().draft)) {
				ChatCore.cfg().draft = v;
				ChatConfig.save();
			}
		}
		ChatHud.setQuery("");
		if (aurorachat$search != null && !aurorachat$search.getValue().isEmpty() && !ChatHud.enabled()) {
			net.primlixe.aurorachat.compat.Mc.chat().setVisibleMessageFilter(m -> true);
		}
	}

	/** :коды: → эмодзи, затем префикс канала G/L. */
	@ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true)
	private String aurorachat$outgoing(String msg) {
		if (!ChatCore.active()) return msg;
		// пустой Enter игра всё равно не отправит — не трогаем историю, АФК, журнал и анимацию отправки
		if (msg == null || msg.isBlank()) return msg;
		aurorachat$sent = true;
		net.primlixe.aurorachat.chat.InputHistory.setPending(msg);
		if (!net.primlixe.aurorachat.Edition.PUBLIC && ChatCore.cfg().punishLog) net.primlixe.aurorachat.chat.PunishLog.onOutgoing(msg);
		if (input != null) net.primlixe.aurorachat.chat.SendFx.start(msg, input.getX(), input.getY());
		net.primlixe.aurorachat.chat.AfkResponder.activity();
		if (ChatCore.cfg().emojiShortcodes) msg = ChatFormat.emojify(msg);
		msg = net.primlixe.aurorachat.chat.Templates.apply(msg);
		msg = net.primlixe.aurorachat.chat.AutoColor.apply(msg);
		msg = net.primlixe.aurorachat.chat.ColorCodes.expand(msg);
		msg = Channels.applyOutgoing(msg);
		net.primlixe.aurorachat.chat.ConsoleLog.add(msg.startsWith("/") ? net.primlixe.aurorachat.chat.ConsoleLog.Level.CMD : net.primlixe.aurorachat.chat.ConsoleLog.Level.OUT,
				msg.startsWith("/") ? "§b" + net.primlixe.aurorachat.chat.ConsoleLog.mask(msg) : msg);
		return msg;
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void aurorachat$click(MouseButtonEvent e, boolean dbl, CallbackInfoReturnable<Boolean> cir) {
		if (!ChatCore.active()) return;
		double mx = e.x(), my = e.y();
		if (aurorachat$menu.isOpen()) {
			aurorachat$menu.click(mx, my);
			cir.setReturnValue(true);
			return;
		}
		if (aurorachat$in(aurorachat$trBtn, mx, my)) {
			var cfg = ChatCore.cfg();
			if (e.button() == 1) {
				cfg.translateOut = net.primlixe.aurorachat.chat.Translator.nextLang(cfg.translateOut);
				ChatConfig.save();
			} else if (!aurorachat$trBusy && input != null) {
				String before = input.getValue();
				var box = input;
				Object self = this;
				String err = net.primlixe.aurorachat.chat.Translator.translateInput(before, tr -> {
					aurorachat$trBusy = false;
					if (tr == null) return; // ошибка — уведомление уже показано
					if (net.primlixe.aurorachat.compat.Mc.screen() == self && box.getValue().equals(before)) {
						box.setValue(tr);
						box.moveCursorToEnd(false);
					} else { // пока ждали, текст поменяли или чат закрыли — не затираем, кладём в буфер
						net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(tr);
						net.primlixe.aurorachat.chat.ChatToasts.push(net.primlixe.aurorachat.ui.I18n.t("⇄ Перевод скопирован"), tr, null, UI.accent());
					}
				});
				if (err != null) net.primlixe.aurorachat.chat.ChatToasts.push(net.primlixe.aurorachat.ui.I18n.t("⇄ Перевод"), err, null, UI.accent());
				else aurorachat$trBusy = true;
			}
			setFocused(input);
			cir.setReturnValue(true);
			return;
		}
		if (aurorachat$in(aurorachat$quickBtn, mx, my)) {
			ChatCore.cfg().quickBarOpen = !ChatCore.cfg().quickBarOpen;
			ChatConfig.save();
			setFocused(input);
			cir.setReturnValue(true);
			return;
		}
		for (int[] r : aurorachat$quickRects) {
			if (!aurorachat$in(r, mx, my)) continue;
			aurorachat$quickClick(r[4], e.button(), e.hasShiftDown());
			setFocused(input);
			cir.setReturnValue(true);
			return;
		}
		if (aurorachat$pickerOpen && aurorachat$picker.contains(mx, my)) {
			String code = aurorachat$picker.click(mx, my, e.button());
			if (code != null) input.insertText(code);
			setFocused(input);
			cir.setReturnValue(true);
			return;
		}
		if (aurorachat$in(aurorachat$pickerBtn, mx, my)) {
			aurorachat$pickerOpen = !aurorachat$pickerOpen;
			aurorachat$picker.reset();
			aurorachat$pickerAt = System.nanoTime();
			aurorachat$emojiOpen = aurorachat$laterOpen = false;
			setFocused(input);
			cir.setReturnValue(true);
			return;
		}
		if (aurorachat$emojiOpen) {
			for (int i = 0; i < aurorachat$emojiCatRects.size(); i++) {
				if (aurorachat$in(aurorachat$emojiCatRects.get(i), mx, my)) { aurorachat$emojiCat = i; cir.setReturnValue(true); return; }
			}
			for (int i = 0; i < aurorachat$emojiRects.size(); i++) {
				if (aurorachat$in(aurorachat$emojiRects.get(i), mx, my)) {
					input.insertText(aurorachat$emojiValues.get(i));
					setFocused(input);
					cir.setReturnValue(true);
					return;
				}
			}
		}
		if (aurorachat$laterOpen) {
			for (int[] r : aurorachat$laterRects) {
				if (aurorachat$in(r, mx, my)) {
					String text = input.getValue();
					aurorachat$laterOpen = false;
					if (!text.isBlank()) {
						String d = net.primlixe.aurorachat.chat.LaterSend.add(text, System.currentTimeMillis() + r[4] * 60_000L);
						net.primlixe.aurorachat.chat.ChatToasts.push(net.primlixe.aurorachat.ui.I18n.t("⏱ Отложено"), d, null, UI.accent());
						input.setValue("");
					}
					setFocused(input);
					cir.setReturnValue(true);
					return;
				}
			}
		}
		if (aurorachat$in(aurorachat$laterBtn, mx, my)) {
			aurorachat$laterOpen = !aurorachat$laterOpen;
			aurorachat$laterAt = System.nanoTime();
			aurorachat$pickerOpen = aurorachat$emojiOpen = false;
			cir.setReturnValue(true);
			return;
		}
		aurorachat$laterOpen = false;
		aurorachat$pickerOpen = false;
		if (aurorachat$in(aurorachat$chBtn, mx, my)) {
			Channels.toggle();
			setFocused(input);
			cir.setReturnValue(true);
			return;
		}
		if (aurorachat$in(aurorachat$emojiBtn, mx, my)) {
			aurorachat$emojiOpen = !aurorachat$emojiOpen;
			aurorachat$emojiAt = System.nanoTime();
			cir.setReturnValue(true);
			return;
		}
		if (aurorachat$in(aurorachat$logBtn, mx, my)) {
			net.primlixe.aurorachat.compat.Mc.setScreen(new ChatLogScreen(null, ""));
			cir.setReturnValue(true);
			return;
		}
		if (e.button() == 1 && ChatCore.cfg().copyMenu) {
			// доп. окна лежат поверх основного чата — сначала они, иначе меню открывалось для сообщения под окном
			boolean overWin = ChatHud.enabled() && net.primlixe.aurorachat.chat.ExtraWindows.over(mx, my);
			GuiMessage m = overWin ? net.primlixe.aurorachat.chat.ExtraWindows.messageAt(mx, my) : ChatCore.messageAt(mx, my);
			if (m != null) {
				aurorachat$menu.open(m, (int) mx, (int) my, width, height, input,
						q -> net.primlixe.aurorachat.compat.Mc.setScreen(new ChatLogScreen(null, q)));
				cir.setReturnValue(true);
				return;
			}
		}
		// кнопки шапки (G/L, вкладки) — раньше доп. окон: окно «Клан» поверх шапки не должно съедать клик по G
		if (ChatHud.enabled() && e.button() == 0 && ChatHud.overControls(mx, my)
				&& ChatHud.click(mx, my, e.button(), st -> handleComponentClicked(st, e.hasShiftDown()))) {
			cir.setReturnValue(true);
			return;
		}
		if (ChatHud.enabled() && net.primlixe.aurorachat.chat.ExtraWindows.click(mx, my, e.button())) {
			cir.setReturnValue(true);
			return;
		}
		if (ChatHud.enabled() && ChatHud.click(mx, my, e.button(), st -> handleComponentClicked(st, e.hasShiftDown()))) {
			cir.setReturnValue(true);
		}
	}

	/** Ctrl+колесо над чатом — размер текста, Alt+колесо — прозрачность фона. */
	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void aurorachat$wheel(double mx, double my, double sx, double sy, CallbackInfoReturnable<Boolean> cir) {
		// палитра «&» открыта — колесо листает её градиенты
		if (aurorachat$pickerOpen && sy != 0 && aurorachat$picker.contains(mx, my)) {
			aurorachat$picker.scroll(mx, my, sy);
			cir.setReturnValue(true);
			return;
		}
		if (ChatHud.enabled() && sy != 0 && net.primlixe.aurorachat.chat.ExtraWindows.scroll(mx, my, sy)) {
			cir.setReturnValue(true);
			return;
		}
		if (!ChatHud.enabled() || sy == 0) return;
		long win = Minecraft.getInstance().getWindow().handle();
		boolean ctrl = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
		boolean alt = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
		ChatConfig c = ChatConfig.get();
		boolean over = ChatHud.over(mx, my);
		if (over && ctrl) {
			c.textScale = Math.max(0.5f, Math.min(1.6f, Math.round((c.textScale + (sy > 0 ? 0.05f : -0.05f)) * 20) / 20f));
		} else if (over && alt) {
			c.bgOpacity = Math.max(0f, Math.min(1f, Math.round((c.bgOpacity + (sy > 0 ? 0.05f : -0.05f)) * 20) / 20f));
		} else {
			// обычное колесо — своя плавная прокрутка (подсказки команд листаются как раньше)
			if (commandSuggestions != null && commandSuggestions.mouseScrolled(Math.max(-1, Math.min(1, sy)))) {
				cir.setReturnValue(true);
				return;
			}
			ChatHud.wheel(sy);
			cir.setReturnValue(true);
			return;
		}
		ChatConfig.save();
		cir.setReturnValue(true);
	}

	/** Перенос и изменение размера окна чата. */
	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (ChatHud.dragging()) {
			ChatHud.dragTo(width, height, e.x(), e.y());
			return true;
		}
		if (net.primlixe.aurorachat.chat.ExtraWindows.dragging()) {
			net.primlixe.aurorachat.chat.ExtraWindows.dragTo(width, height, e.x(), e.y());
			return true;
		}
		return super.mouseDragged(e, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (ChatHud.dragging()) {
			ChatHud.endDrag();
			return true;
		}
		if (net.primlixe.aurorachat.chat.ExtraWindows.dragging()) {
			net.primlixe.aurorachat.chat.ExtraWindows.endDrag();
			return true;
		}
		return super.mouseReleased(e);
	}

	/** Клик по панели быстрых фраз. */
	@Unique
	private void aurorachat$quickClick(int idx, int button, boolean shift) {
		var c = ChatCore.cfg();
		var list = c.quickList;
		if (idx == -1) { // «＋» — сохранить то, что написано
			String v = input.getValue().trim();
			if (v.isEmpty() || v.equals("/")) return;
			list.remove(v);
			list.add(v);
			ChatConfig.save();
			aurorachat$quickAddedTime = System.currentTimeMillis();
			aurorachat$quickFlash.put(v, new long[]{System.currentTimeMillis(), 0});
			return;
		}
		if (idx == -2) { // «↶» — вернуть удалённую
			if (aurorachat$quickRemoved != null) {
				list.add(Math.min(aurorachat$quickRemovedAt, list.size()), aurorachat$quickRemoved);
				ChatConfig.save();
				aurorachat$quickRemoved = null;
			}
			return;
		}
		if (idx < 0 || idx >= list.size()) return;
		String text = list.get(idx);
		if (button == 1) { // ПКМ — удалить (можно вернуть «↶»)
			aurorachat$quickRemoved = list.remove(idx);
			aurorachat$quickRemovedAt = idx;
			aurorachat$quickRemovedTime = System.currentTimeMillis();
			ChatConfig.save();
			return;
		}
		if (button != 0) return;
		if (shift) { // Shift+ЛКМ — вставить в строку, чтобы дописать
			input.insertText(text);
			return;
		}
		aurorachat$quickSend(text);
	}

	/** Отправить фразу сразу (со всеми обработками чата: цвета, канал, эмодзи), чат остаётся открытым. */
	@Unique
	private void aurorachat$quickSend(String text) {
		if (text == null || text.isBlank()) return;
		boolean sentBefore = aurorachat$sent;
		((ChatScreen) (Object) this).handleChatInput(text, false);
		aurorachat$sent = sentBefore; // недописанное в строке — остаётся черновиком
		long now = System.currentTimeMillis();
		long[] f = aurorachat$quickFlash.get(text);
		aurorachat$quickFlash.put(text, new long[]{now, f != null && now - f[0] < 2500 ? f[1] + 1 : 1});
	}

	@Unique
	private static boolean aurorachat$in(int[] r, double x, double y) {
		return r != null && x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void aurorachat$key(KeyEvent e, CallbackInfoReturnable<Boolean> cir) {
		if (!ChatCore.active()) return;
		if (e.key() == GLFW.GLFW_KEY_ESCAPE && (aurorachat$menu.isOpen() || aurorachat$emojiOpen || aurorachat$pickerOpen || aurorachat$laterOpen)) {
			aurorachat$menu.close();
			aurorachat$emojiOpen = aurorachat$pickerOpen = aurorachat$laterOpen = false;
			cir.setReturnValue(true);
			return;
		}
		// Enter на строке из истории (↑) — отправить и НЕ закрывать чат: текст остаётся, жми Enter ещё раз.
		// Ctrl+Enter — так же для любого текста.
		boolean enter = e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER;
		boolean ctrl = (e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
		var hist = net.primlixe.aurorachat.chat.InputHistory.list();
		boolean fromHistory = aurorachat$hpos >= 0 && aurorachat$hpos < hist.size() && hist.get(aurorachat$hpos).equals(input.getValue());
		if (enter && ChatCore.cfg().historyRepeat && getFocused() == input && (fromHistory || ctrl)) {
			String v = input.getValue();
			if (!v.isBlank()) {
				boolean sentBefore = aurorachat$sent;
				((ChatScreen) (Object) this).handleChatInput(v, true);
				aurorachat$sent = sentBefore;
				input.setValue(v);
				input.moveCursorToEnd(false);
				// отправленное ушло в конец истории — дальше ↑ листает от него
				var hl = net.primlixe.aurorachat.chat.InputHistory.list();
				int at = hl.lastIndexOf(v.strip());
				aurorachat$hpos = at >= 0 ? at : -1;
				aurorachat$repeatAt = System.currentTimeMillis(); // видно, что ушло (текст остаётся — можно ещё раз)
				aurorachat$repeatN = System.currentTimeMillis() - aurorachat$repeatLast < 4000 ? aurorachat$repeatN + 1 : 1;
				aurorachat$repeatLast = aurorachat$repeatAt;
				commandSuggestions.setAllowSuggestions(false);
				cir.setReturnValue(true);
				return;
			}
		}
		// Tab — дописать ник; ещё Tab — следующий подходящий
		if (e.key() == GLFW.GLFW_KEY_TAB && (e.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) == 0
				&& ChatCore.cfg().nickComplete && getFocused() == input && aurorachat$completeNick((e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0)) {
			cir.setReturnValue(true);
			return;
		}
		// ↑ / ↓ — история ввода, даже если открыт список подсказок команд (когда в истории есть подходящее)
		ChatConfig hc = ChatCore.cfg();
		if (hc.betterHistory && getFocused() == input && (e.key() == GLFW.GLFW_KEY_UP || e.key() == GLFW.GLFW_KEY_DOWN)) {
			boolean up = e.key() == GLFW.GLFW_KEY_UP;
			var list = net.primlixe.aurorachat.chat.InputHistory.list();
			int n = list.size();
			int pos = aurorachat$hpos < 0 || aurorachat$hpos > n ? n : aurorachat$hpos;
			String prefix = !hc.historyPrefix ? "" : pos == n ? input.getValue() : aurorachat$hbuf;
			boolean can = up ? net.primlixe.aurorachat.chat.InputHistory.find(pos, -1, prefix, input.getValue()) >= 0 : aurorachat$hpos >= 0;
			if (can) {
				((ChatScreen) (Object) this).moveInHistory(up ? -1 : 1);
				cir.setReturnValue(true);
				return;
			}
		}
		// Alt+1…9 — отправить быструю фразу №
		if (ChatCore.cfg().quickPhrases && (e.modifiers() & GLFW.GLFW_MOD_ALT) != 0 && (e.modifiers() & GLFW.GLFW_MOD_CONTROL) == 0) {
			int k = e.key() >= GLFW.GLFW_KEY_1 && e.key() <= GLFW.GLFW_KEY_9 ? e.key() - GLFW.GLFW_KEY_1
					: e.key() >= GLFW.GLFW_KEY_KP_1 && e.key() <= GLFW.GLFW_KEY_KP_9 ? e.key() - GLFW.GLFW_KEY_KP_1 : -1;
			if (k >= 0) {
				if (k < ChatCore.cfg().quickList.size()) aurorachat$quickSend(ChatCore.cfg().quickList.get(k));
				// Windows присылает ещё и символ «1» — на следующем кадре вернём строку как была
				aurorachat$altKeep = input.getValue();
				aurorachat$altKeepCursor = input.getCursorPosition();
				cir.setReturnValue(true);
				return;
			}
		}
		// Alt+G / Alt+L — канал отправки
		if (ChatCore.cfg().channels && (e.modifiers() & GLFW.GLFW_MOD_ALT) != 0 && (e.key() == GLFW.GLFW_KEY_G || e.key() == GLFW.GLFW_KEY_L)) {
			ChatCore.cfg().channel = e.key() == GLFW.GLFW_KEY_G ? "G" : "L";
			ChatConfig.save();
			// как и с Alt+цифрой: Windows присылает ещё символ «g»/«п» — на следующем кадре вернём строку как была
			aurorachat$altKeep = input.getValue();
			aurorachat$altKeepCursor = input.getCursorPosition();
			cir.setReturnValue(true);
			return;
		}
		if (aurorachat$search != null && aurorachat$search.isFocused()) {
			if (e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER || e.key() == GLFW.GLFW_KEY_TAB) {
				setFocused(input);
				cir.setReturnValue(true);
			} else if (e.key() == GLFW.GLFW_KEY_UP || e.key() == GLFW.GLFW_KEY_DOWN) {
				// ↑/↓ в поиске — сразу назад в строку ввода, ванильный чат пролистает историю
				setFocused(input);
			} else if (e.key() == GLFW.GLFW_KEY_ESCAPE && !aurorachat$search.getValue().isEmpty()) {
				aurorachat$search.setValue("");
				setFocused(input);
				cir.setReturnValue(true);
			} else if (aurorachat$search.keyPressed(e)) {
				cir.setReturnValue(true);
			}
		}
	}

	/** Вместо серой полосы под строкой ввода — скруглённая панель в цветах чата. */
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0))
	private void aurorachat$inputBar(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
		ChatConfig c = ChatConfig.get();
		if (!c.enabled || !c.styledInput) { original.call(g, x0, y0, x1, y1, color); return; }
		int bg = 0xFF000000 | c.bgColor;
		UI.shadow(g, x0, y0, x1, y1, 6, 0.8f);
		UI.round(g, x0, y0, x1, y1, Math.min(6, c.radius), UI.alpha(UI.mix(bg, 0xFFFFFFFF, 0.05f), Math.max(0.55f, c.bgOpacity)));
		UI.roundOutline(g, x0, y0, x1, y1, Math.min(6, c.radius), UI.alpha(UI.accent(), 0.55f));
		if (aurorachat$channelChip()) {
			boolean isG = c.channel.equals("G");
			float target = isG ? 1 : 0;
			if (aurorachat$chAnim < 0) aurorachat$chAnim = target;
			aurorachat$chAnim = UI.approach(aurorachat$chAnim, target, 0.25f);
			int col = UI.mix(Channels.color('L'), Channels.color('G'), aurorachat$chAnim);
			int bx = x0 + 2, by = y0 + 2;
			aurorachat$chBtn = new int[]{bx, by, bx + 18, y1 - 2};
			UI.round(g, bx, by, bx + 18, y1 - 2, 4, col);
			UI.centered(g, font, isG ? "G" : "L", bx + 9, by + 2, 0xFF101010);
		} else aurorachat$chBtn = null;
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void aurorachat$render(GuiGraphicsExtractor g, int mx, int my, float partial, CallbackInfo ci) {
		if (!ChatCore.active()) return;
		long t0 = net.primlixe.aurorachat.ui.Perf.INPUT.start();
		try {
			aurorachat$renderImpl(g, mx, my);
		} finally {
			net.primlixe.aurorachat.ui.Perf.INPUT.stop(t0);
		}
	}

	@Unique
	private void aurorachat$renderImpl(GuiGraphicsExtractor g, int mx, int my) {
		var s = ChatCore.cfg();
		if (aurorachat$altKeep != null && input != null) {
			if (!input.getValue().equals(aurorachat$altKeep)) {
				input.setValue(aurorachat$altKeep);
				input.setCursorPosition(Math.min(aurorachat$altKeepCursor, aurorachat$altKeep.length()));
				input.setHighlightPos(input.getCursorPosition());
			}
			aurorachat$altKeep = null;
		}
		// кнопки справа над строкой ввода — только включённые, без дыр
		int bx = (aurorachat$search != null ? width - Math.min(150, width / 3) - 4 : width - 4) - 18;
		int by = height - 32;
		if (s.historyButton) {
			aurorachat$logBtn = new int[]{bx, by, bx + 16, by + 16};
			aurorachat$iconBtn(g, aurorachat$logBtn, "☰", mx, my);
			bx -= 18;
		} else aurorachat$logBtn = null;
		if (s.emojiPicker) {
			aurorachat$emojiBtn = new int[]{bx, by, bx + 16, by + 16};
			aurorachat$iconBtn(g, aurorachat$emojiBtn, "☺", mx, my);
			bx -= 18;
		} else aurorachat$emojiBtn = null;
		// & палитра цветов
		int pbx = bx;
		if (s.colorPicker) {
			aurorachat$pickerBtn = new int[]{bx, by, bx + 16, by + 16};
			aurorachat$iconBtn(g, aurorachat$pickerBtn, "&", mx, my);
			bx -= 18;
			if (aurorachat$in(aurorachat$pickerBtn, mx, my) && !aurorachat$pickerOpen) {
				g.setTooltipForNextFrame(font, Component.literal(net.primlixe.aurorachat.ui.I18n.t("Цвета, стили и градиенты")), mx, my);
			}
		} else {
			aurorachat$pickerBtn = null;
			aurorachat$pickerOpen = false;
		}
		aurorachat$updateCache();
		// серая подсказка ника: Tab — дописать
		if (s.nickComplete && s.nickGhost && input != null && getFocused() == input) {
			String gh = aurorachat$cGhost;
			if (gh != null) {
				int gx = input.getScreenX(input.getValue().length());
				if (gx + font.width(gh) < input.getX() + input.getWidth()) g.text(font, gh, gx, input.getY(), 0x70FFFFFF, false);
			}
		}
		// ⏱ отложенная отправка
		int lx = bx;
		if (s.laterSend && s.laterButton) {
			aurorachat$laterBtn = new int[]{lx, by, lx + 16, by + 16};
			aurorachat$iconBtn(g, aurorachat$laterBtn, "⏱", mx, my);
			bx -= 18;
			if (aurorachat$in(aurorachat$laterBtn, mx, my) && !aurorachat$laterOpen) {
				g.setTooltipForNextFrame(font, Component.literal(net.primlixe.aurorachat.ui.I18n.t("Отправить позже (") + s.scheduled.size() + net.primlixe.aurorachat.ui.I18n.t(" в очереди)")), mx, my);
			}
		} else {
			aurorachat$laterBtn = null;
			aurorachat$laterOpen = false;
		}
		// ⇄ перевести набранное (ЛКМ), ПКМ — сменить язык
		if (s.translate && s.translateButton) {
			aurorachat$trBtn = new int[]{bx, by, bx + 16, by + 16};
			aurorachat$iconBtn(g, aurorachat$trBtn, aurorachat$trBusy ? "…" : "⇄", mx, my);
			if (aurorachat$in(aurorachat$trBtn, mx, my)) {
				g.setTooltipForNextFrame(font, Component.literal(net.primlixe.aurorachat.ui.I18n.t("Перевести набранное на ") + s.translateOut + net.primlixe.aurorachat.ui.I18n.t("  §7(ПКМ — другой язык)")), mx, my);
			}
			bx -= 18;
		} else aurorachat$trBtn = null;
		// ⚡ быстрые фразы: кнопка в ряду иконок, сами фразы — слева над строкой ввода
		aurorachat$quickRects.clear();
		aurorachat$quickTop = Integer.MAX_VALUE;
		if (s.quickPhrases) {
			aurorachat$quickBtn = new int[]{bx, by, bx + 16, by + 16};
			if (s.quickBarOpen) UI.round(g, bx - 1, by - 1, bx + 17, by + 17, 7, UI.alpha(UI.accent(), 0.35f));
			aurorachat$iconBtn(g, aurorachat$quickBtn, "⚡", mx, my);
			if (aurorachat$in(aurorachat$quickBtn, mx, my)) {
				g.setTooltipForNextFrame(font, Component.literal(s.quickBarOpen ? net.primlixe.aurorachat.ui.I18n.t("Скрыть быстрые фразы") : net.primlixe.aurorachat.ui.I18n.t("Быстрые фразы — сохранённые сообщения и команды")), mx, my);
			}
			int right = bx - 4;
			bx -= 18;
			if (s.quickBarOpen) aurorachat$drawQuick(g, mx, my, 4, right, by);
		} else aurorachat$quickBtn = null;
		// предпросмотр цветов: как увидят сообщение с &-кодами и градиентами
		if (s.colorPreview && input != null && !input.getValue().isEmpty()) {
			if (aurorachat$cPreview != null) {
				Component pv = aurorachat$cPreview;
				int px0 = 2, py0 = Math.min(height - 29, aurorachat$quickTop - 15), px1 = Math.max(px0 + 60, bx + 14);
				UI.round(g, px0, py0, px1, py0 + 13, 4, 0xC80E1018);
				g.enableScissor(px0 + 3, py0, px1 - 3, py0 + 13);
				int tw = font.width(pv), avail = px1 - px0 - 8;
				g.text(font, pv, px0 + 4 - Math.max(0, tw - avail), py0 + 3, 0xFFFFFFFF, true);
				g.disableScissor();
			}
		}
		aurorachat$laterRects.clear();
		if (aurorachat$laterOpen && aurorachat$laterBtn != null) {
			g.nextStratum();
			int pw = 92, ph = AURORACHAT$DELAYS.length * 13 + 18;
			int px = Math.max(2, lx + 16 - pw), py = by - ph - 3;
			boolean pop = aurorachat$popIn(g, aurorachat$laterAt, lx + 8, by);
			UI.shadow(g, px, py, px + pw, py + ph, 6);
			UI.round(g, px, py, px + pw, py + ph, 6, 0xF0121424);
			UI.roundOutline(g, px, py, px + pw, py + ph, 6, UI.alpha(UI.accent(), 0.6f));
			g.text(font, input.getValue().isBlank() ? net.primlixe.aurorachat.ui.I18n.t("Сначала напиши текст") : net.primlixe.aurorachat.ui.I18n.t("Отправить через:"), px + 6, py + 5, UI.SUB, false);
			for (int i = 0; i < AURORACHAT$DELAYS.length; i++) {
				int d = AURORACHAT$DELAYS[i];
				int[] r = {px + 3, py + 16 + i * 13, px + pw - 3, py + 29 + i * 13, d};
				boolean h = aurorachat$in(r, mx, my);
				if (h) UI.round(g, r[0], r[1], r[2], r[3], 4, UI.alpha(UI.accent(), 0.8f));
				g.text(font, d >= 60 ? (d / 60) + net.primlixe.aurorachat.ui.I18n.t(" час") : d + net.primlixe.aurorachat.ui.I18n.t(" мин"), r[0] + 5, r[1] + 3, 0xFFFFFFFF, false);
				aurorachat$laterRects.add(r);
			}
			if (pop) g.pose().popMatrix();
		}
		if (aurorachat$pickerOpen && aurorachat$pickerBtn != null) {
			g.nextStratum();
			int pw = net.primlixe.aurorachat.chat.ColorPicker.W, ph = aurorachat$picker.height();
			int ppx = Math.max(2, Math.min(width - pw - 2, pbx + 16 - pw)), ppy = Math.max(2, by - ph - 3);
			boolean pop = aurorachat$popIn(g, aurorachat$pickerAt, pbx + 8, by);
			aurorachat$picker.render(g, font, ppx, ppy, mx, my);
			if (pop) g.pose().popMatrix();
		}
		if (s.charCounter && input != null) {
			int len = aurorachat$cLen;
			if (len > 0) {
				String c = len + "/256";
				int col = len > 230 ? UI.BAD : (len > 180 ? UI.GOLD : UI.MUTED);
				g.text(font, c, width - font.width(c) - 6, height - 11, col, false);
			}
		}
		if (aurorachat$in(aurorachat$chBtn, mx, my)) {
			g.setTooltipForNextFrame(font, Component.literal(net.primlixe.aurorachat.ui.I18n.t("Канал: ") + Channels.name(s.channel.charAt(0)) + net.primlixe.aurorachat.ui.I18n.t(" · клик или Alt+G / Alt+L")), mx, my);
		} else if (aurorachat$in(aurorachat$logBtn, mx, my)) {
			g.setTooltipForNextFrame(font, Component.literal(net.primlixe.aurorachat.ui.I18n.t("История чата")), mx, my);
		}
		// «✔ отправлено» после ↑ + Enter: чат не закрывается и текст остаётся — без отметки кажется, что Enter не сработал
		long since = System.currentTimeMillis() - aurorachat$repeatAt;
		if (aurorachat$repeatAt > 0 && since < 1800 && input != null) {
			float a = since < 1300 ? 1 : 1 - (since - 1300) / 500f;
			String t = net.primlixe.aurorachat.ui.I18n.t("✔ отправлено") + (aurorachat$repeatN > 1 ? " ×" + aurorachat$repeatN : "") + net.primlixe.aurorachat.ui.I18n.t(" · Enter — ещё раз, Esc — закрыть");
			int tw = font.width(t) + 10, tx = Math.max(2, input.getX() + input.getWidth() - tw - 2), ty = input.getY() - 15;
			UI.round(g, tx, ty, tx + tw, ty + 12, 5, UI.alpha(0xFF1E8A4A, 0.92f * a));
			g.text(font, t, tx + 5, ty + 2, UI.alpha(0xFFFFFFFF, a), false);
		}
		if (aurorachat$emojiOpen && s.emojiPicker) aurorachat$drawEmoji(g, mx, my);
		aurorachat$menu.render(g, font, mx, my);
	}

	/** Что зависит только от текста в строке — считаем один раз при изменении, а не каждый кадр. */
	@Unique private String aurorachat$cVal;
	@Unique private int aurorachat$cCursor = -1, aurorachat$cLen;
	@Unique private Component aurorachat$cPreview;
	@Unique private String aurorachat$cGhost;
	@Unique private long aurorachat$cAt;

	@Unique
	private void aurorachat$updateCache() {
		if (input == null) return;
		String v = input.getValue();
		int cur = input.getCursorPosition();
		long now = System.currentTimeMillis();
		// ники в табе меняются — подсказку обновляем и просто раз в полсекунды
		if (v.equals(aurorachat$cVal) && cur == aurorachat$cCursor && now - aurorachat$cAt < 500) return;
		boolean textChanged = !v.equals(aurorachat$cVal) || now - aurorachat$cAt >= 500; // и канал/градиент могли смениться
		aurorachat$cVal = v;
		aurorachat$cCursor = cur;
		aurorachat$cAt = now;
		var s = ChatCore.cfg();
		aurorachat$cGhost = s.nickComplete && s.nickGhost ? net.primlixe.aurorachat.chat.NickComplete.ghost(v, cur) : null;
		if (!textChanged) return;
		if (v.isEmpty()) {
			aurorachat$cLen = 0;
			aurorachat$cPreview = null;
			return;
		}
		String out = net.primlixe.aurorachat.chat.AutoColor.apply(v);
		aurorachat$cLen = Channels.applyOutgoing(net.primlixe.aurorachat.chat.ColorCodes.expand(out)).length();
		aurorachat$cPreview = net.primlixe.aurorachat.chat.ColorCodes.hasAnyCode(out) ? net.primlixe.aurorachat.chat.ColorCodes.preview(out) : null;
	}

	/** Фишки быстрых фраз: «＋», «↶ вернуть», потом сами фразы; ряды растут вверх (до трёх). */
	@Unique
	private void aurorachat$drawQuick(GuiGraphicsExtractor g, int mx, int my, int left, int right, int by) {
		var s = ChatCore.cfg();
		long now = System.currentTimeMillis();
		int h = 14, gapX = 3, rowStep = h + 2, maxRows = 3;
		int x = left, y = by + 1, row = 0;
		List<Object[]> items = new ArrayList<>(); // {подпись, индекс, текст для подсказки}
		String typed = input == null ? "" : input.getValue().trim();
		items.add(new Object[]{s.quickList.isEmpty() ? net.primlixe.aurorachat.ui.I18n.t("＋ сохранить фразу") : "＋", -1, null});
		if (aurorachat$quickRemoved != null && now - aurorachat$quickRemovedTime < 6000) items.add(new Object[]{net.primlixe.aurorachat.ui.I18n.t("↶ вернуть"), -2, null});
		for (int i = 0; i < s.quickList.size(); i++) items.add(new Object[]{s.quickList.get(i), i, s.quickList.get(i)});
		Object[] hovered = null;
		for (Object[] it : items) {
			int idx = (int) it[1];
			String full = (String) it[0];
			String label = idx >= 0 ? UI.trim(font, full, 110) : full;
			String num = idx >= 0 && idx < 9 ? String.valueOf(idx + 1) : "";
			int w = font.width(label) + 10 + (num.isEmpty() ? 0 : font.width(num) + 4);
			if (x + w > right && x > left) { // перенос на ряд выше
				row++;
				if (row >= maxRows) break;
				x = left;
				y -= rowStep;
			}
			if (x + w > right) break;
			int[] r = {x, y, x + w, y + h, idx};
			boolean hv = aurorachat$in(r, mx, my);
			// вспышка после отправки / сохранения
			long[] f = idx >= 0 ? aurorachat$quickFlash.get(full) : null;
			float flash = f != null ? Math.max(0, 1 - (now - f[0]) / 450f) : 0;
			if (idx == -1 && now - aurorachat$quickAddedTime < 450) flash = 1 - (now - aurorachat$quickAddedTime) / 450f;
			int base = idx == -1 ? UI.alpha(UI.accent(), 0.55f) : idx == -2 ? 0xD02A1F14 : 0xD0181A2C;
			int bg = hv ? UI.alpha(UI.accent(), 0.85f) : base;
			if (flash > 0) bg = UI.mix(bg, 0xFFFFFFFF, flash * 0.55f);
			UI.round(g, r[0], r[1], r[2], r[3], 5, bg);
			int tx = x + 5;
			if (!num.isEmpty()) {
				g.text(font, num, tx, y + 3, hv ? 0xC0FFFFFF : 0xFF7C8299, false);
				tx += font.width(num) + 4;
			}
			int col = idx >= 0 && full.startsWith("/") ? 0xFF9CC4FF : 0xFFFFFFFF;
			g.text(font, label, tx, y + 3, col, false);
			// «×3» — сколько раз подряд отправил
			if (f != null && f[1] > 1 && now - f[0] < 2500) {
				String cnt = "×" + f[1];
				int cw = font.width(cnt) + 4;
				UI.round(g, r[2] - cw / 2, r[1] - 5, r[2] + cw / 2 + 1, r[1] + 4, 4, UI.alpha(UI.accent(), 0.95f));
				g.text(font, cnt, r[2] - cw / 2 + 2, r[1] - 4, 0xFFFFFFFF, false);
			}
			aurorachat$quickRects.add(r);
			if (hv) hovered = it;
			x += w + gapX;
		}
		aurorachat$quickTop = y;
		if (hovered != null) {
			int idx = (int) hovered[1];
			List<Component> tip = new ArrayList<>();
			if (idx == -1) {
				if (typed.isEmpty() || typed.equals("/")) {
					tip.add(Component.literal(net.primlixe.aurorachat.ui.I18n.t("Напиши сообщение или команду и нажми ＋")));
					tip.add(Component.literal(net.primlixe.aurorachat.ui.I18n.t("§7Она сохранится здесь — потом отправляется одним кликом")));
				} else tip.add(Component.literal(net.primlixe.aurorachat.ui.I18n.t("Сохранить: §f") + typed));
			} else if (idx == -2) {
				tip.add(Component.literal(net.primlixe.aurorachat.ui.I18n.t("Вернуть удалённую: §f") + aurorachat$quickRemoved));
			} else {
				tip.add(Component.literal("§f" + hovered[2]));
				tip.add(Component.literal(net.primlixe.aurorachat.ui.I18n.t("§7ЛКМ — отправить") + (idx < 9 ? " (Alt+" + (idx + 1) + ")" : "") + net.primlixe.aurorachat.ui.I18n.t(" · Shift+ЛКМ — вставить · ПКМ — удалить")));
			}
			g.setComponentTooltipForNextFrame(font, tip, mx, my);
		}
	}

	@Unique
	private void aurorachat$iconBtn(GuiGraphicsExtractor g, int[] r, String icon, int mx, int my) {
		boolean h = aurorachat$in(r, mx, my);
		UI.round(g, r[0], r[1], r[2], r[3], 6, h ? UI.alpha(UI.accent(), 0.85f) : 0xD0181A2C);
		UI.centered(g, font, icon, (r[0] + r[2]) / 2, r[1] + 4, 0xFFFFFFFF);
	}

	@Unique
	private void aurorachat$drawEmoji(GuiGraphicsExtractor g, int mx, int my) {
		aurorachat$emojiRects.clear();
		aurorachat$emojiValues.clear();
		aurorachat$emojiCatRects.clear();
		var cats = net.primlixe.aurorachat.chat.Symbols.CATS;
		if (aurorachat$emojiCat < 0 || aurorachat$emojiCat >= cats.size()) aurorachat$emojiCat = net.primlixe.aurorachat.chat.Symbols.seasonIndex();
		var cat = cats.get(aurorachat$emojiCat);
		int cell = 16, colsN = 12;
		int w = colsN * cell + 8;
		// раскладка: сетка символов, ниже — стикеры плашками по ширине
		int gridRows = (cat.items().size() + colsN - 1) / colsN;
		List<int[]> chips = new ArrayList<>();
		int cxp = 4, cyp = 0;
		for (String st : cat.stickers()) {
			int cw = Math.min(w - 8, font.width(st) + 8);
			if (cxp + cw > w - 4) { cxp = 4; cyp += 15; }
			chips.add(new int[]{cxp, cyp, cw});
			cxp += cw + 3;
		}
		int stickH = cat.stickers().isEmpty() ? 0 : cyp + 15 + 4;
		int top = 34; // вкладки + заголовок
		int h = top + gridRows * cell + stickH + 4;
		int maxH = height - 60;
		if (h > maxH) h = maxH;
		int x = Math.max(2, width - w - 4), y = Math.max(2, height - 36 - h);
		g.nextStratum();
		boolean pop = aurorachat$popIn(g, aurorachat$emojiAt, aurorachat$emojiBtn != null ? aurorachat$emojiBtn[0] + 8 : x + w, y + h);
		UI.shadow(g, x, y, x + w, y + h, 8);
		UI.round(g, x, y, x + w, y + h, 8, 0xF0121424);
		UI.roundOutline(g, x, y, x + w, y + h, 8, UI.alpha(UI.accent(), 0.6f));
		String hover = null;
		// вкладки наборов
		int season = net.primlixe.aurorachat.chat.Symbols.seasonIndex();
		int tw = Math.min(18, (w - 8) / cats.size());
		for (int i = 0; i < cats.size(); i++) {
			int tx = x + 4 + i * tw, ty = y + 4;
			boolean sel = i == aurorachat$emojiCat, hv = mx >= tx && mx < tx + tw && my >= ty && my < ty + 15;
			if (sel) UI.round(g, tx, ty, tx + tw - 1, ty + 15, 4, UI.alpha(UI.accent(), 0.55f));
			else if (hv) UI.round(g, tx, ty, tx + tw - 1, ty + 15, 4, 0x40FFFFFF);
			UI.centered(g, font, cats.get(i).icon(), tx + tw / 2, ty + 4, sel || hv ? 0xFFFFFFFF : 0xFFB8BCD0);
			if (i == season) g.fill(tx + tw / 2 - 2, ty + 14, tx + tw / 2 + 1, ty + 15, 0xFFFFD166); // «сейчас»
			if (hv) hover = cats.get(i).name() + (i == season ? net.primlixe.aurorachat.ui.I18n.t(" · сейчас") : "");
			aurorachat$emojiCatRects.add(new int[]{tx, ty, tx + tw, ty + 15});
		}
		g.text(font, cat.name() + (aurorachat$emojiCat == season ? net.primlixe.aurorachat.ui.I18n.t("  §e· сейчас") : ""), x + 6, y + 22, UI.SUB, false);
		g.enableScissor(x, y + top - 2, x + w, y + h - 2);
		var codes = net.primlixe.aurorachat.chat.ChatFormat.EMOJI;
		for (int i = 0; i < cat.items().size(); i++) {
			String v = cat.items().get(i);
			int cx = x + 4 + (i % colsN) * cell, cy = y + top + (i / colsN) * cell;
			boolean hv = mx >= cx && mx < cx + cell && my >= cy && my < cy + cell && my < y + h - 2;
			if (hv) {
				UI.round(g, cx, cy, cx + cell, cy + cell, 4, 0x50FFFFFF);
				String code = null;
				for (var en : codes.entrySet()) if (en.getValue().equals(v)) { code = en.getKey(); break; }
				hover = v + (code != null ? "   :" + code + ":" : "");
			}
			UI.centered(g, font, v, cx + cell / 2, cy + 4, 0xFFFFFFFF);
			if (cy + cell <= y + h) { aurorachat$emojiRects.add(new int[]{cx, cy, cx + cell, cy + cell}); aurorachat$emojiValues.add(v); }
		}
		int sy = y + top + gridRows * cell + (gridRows > 0 ? 4 : 0);
		for (int i = 0; i < chips.size(); i++) {
			int[] c = chips.get(i);
			String v = cat.stickers().get(i);
			int cx = x + c[0], cy = sy + c[1];
			if (cy + 13 > y + h - 2) break;
			boolean hv = mx >= cx && mx < cx + c[2] && my >= cy && my < cy + 13;
			UI.round(g, cx, cy, cx + c[2], cy + 13, 4, hv ? UI.alpha(UI.accent(), 0.5f) : 0x30FFFFFF);
			g.text(font, UI.trim(font, v, c[2] - 6), cx + 4, cy + 3, 0xFFFFFFFF, false);
			if (hv && font.width(v) + 8 > c[2]) hover = v;
			aurorachat$emojiRects.add(new int[]{cx, cy, cx + c[2], cy + 13});
			aurorachat$emojiValues.add(v);
		}
		g.disableScissor();
		if (pop) g.pose().popMatrix();
		if (hover != null) g.setTooltipForNextFrame(font, Component.literal(hover), mx, my);
	}
}
