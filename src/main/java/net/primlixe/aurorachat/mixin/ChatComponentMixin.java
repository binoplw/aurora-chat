package net.primlixe.aurorachat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.primlixe.aurorachat.AuroraChatMod;
import net.primlixe.aurorachat.chat.ChatCore;
import net.primlixe.aurorachat.chat.ChatHud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Обработка входящих, большая история, свой HUD вместо ванильной отрисовки, чат не чистится при смене сервера. */
// priority 1500: применяемся после MoreChatHistory (он ставит 16384 через ModifyExpressionValue) — наш размер истории главнее
@Mixin(value = ChatComponent.class, priority = 1500)
public abstract class ChatComponentMixin {
	@Shadow @Final private List<GuiMessage> allMessages;

	@Shadow
	private void refreshTrimmedMessages() {}

	@WrapMethod(method = "addMessage")
	private void aurorachat$process(Component content, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, Operation<Void> original) {
		long t0 = net.primlixe.aurorachat.ui.Perf.INCOMING.start();
		try {
			aurorachat$processImpl(content, signature, source, tag, original);
		} finally {
			net.primlixe.aurorachat.ui.Perf.INCOMING.stop(t0);
		}
	}

	@org.spongepowered.asm.mixin.Unique
	private void aurorachat$processImpl(Component content, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, Operation<Void> original) {
		ChatCore.Result r;
		try {
			r = ChatCore.incoming(content, source, allMessages);
		} catch (Throwable t) {
			AuroraChatMod.LOG.warn("Aurora Chat: ошибка обработки сообщения", t);
			r = new ChatCore.Result(content, false, false);
		}
		if (r.hidden()) return;
		GuiMessage previous = r.replaceLast() && !allMessages.isEmpty() ? allMessages.get(0) : null;
		original.call(r.component(), signature, source, tag);
		long t0 = net.primlixe.aurorachat.ui.Perf.REFLOW.start();
		// Раньше здесь была ванильная refreshTrimmedMessages — она заново переносит по строкам ВСЮ историю
		// (2000 сообщений ≈ 20–45 мс) на каждом повторе ×N. Теперь убираем только строки того, что удалили.
		if (previous != null) {
			int at = -1;
			for (int i = 0; i < Math.min(4, allMessages.size()); i++) if (allMessages.get(i) == previous) { at = i; break; } // по ссылке, не equals
			if (at < 0) at = aurorachat$indexOf(previous);
			if (at >= 0) {
				allMessages.remove(at);
				aurorachat$dropLines(previous);
			}
		}
		// размер истории — по настройке Aurora Chat, даже если другой мод (MoreChatHistory) расширил лимит
		if (ChatCore.active()) {
			int max = ChatCore.cfg().historySize;
			if (allMessages.size() > max + 100) { // с запасом — не на каждом сообщении
				List<GuiMessage> old = allMessages.subList(max, allMessages.size());
				java.util.Set<GuiMessage> gone = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
				gone.addAll(old);
				old.clear();
				// строки удалённых — в самом конце списка строк
				for (int i = trimmedMessages.size() - 1; i >= 0 && gone.contains(trimmedMessages.get(i).parent()); i--) trimmedMessages.remove(i);
			}
		}
		net.primlixe.aurorachat.ui.Perf.REFLOW.stop(t0);
	}

	@Shadow @Final private List<GuiMessage.Line> trimmedMessages;

	@org.spongepowered.asm.mixin.Unique
	private int aurorachat$indexOf(GuiMessage m) {
		for (int i = 0; i < allMessages.size(); i++) if (allMessages.get(i) == m) return i;
		return -1;
	}

	/** Убрать строки одного сообщения: они идут подряд у начала списка (новые — сверху). */
	@org.spongepowered.asm.mixin.Unique
	private void aurorachat$dropLines(GuiMessage m) {
		boolean found = false;
		for (int i = 0; i < trimmedMessages.size(); i++) {
			if (trimmedMessages.get(i).parent() == m) { trimmedMessages.remove(i--); found = true; }
			else if (found) break;
		}
	}

	@com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = {"addMessageToQueue", "addMessageToDisplayQueue", "addRecentChat"}, at = @At(value = "CONSTANT", args = "intValue=100"), require = 1)
	private int aurorachat$history(int vanilla) {
		return ChatCore.active() ? ChatCore.cfg().historySize : vanilla;
	}

	/** В историю ↑/↓ — то, что набрал игрок (без префикса канала, автоцвета и развёрнутых градиентов). */
	@ModifyVariable(method = "addRecentChat", at = @At("HEAD"), argsOnly = true)
	private String aurorachat$recent(String sent) {
		if (!ChatCore.active()) return sent;
		String typed = net.primlixe.aurorachat.chat.InputHistory.takePending(sent);
		net.primlixe.aurorachat.chat.InputHistory.add(typed);
		return typed;
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
			at = @At("HEAD"), cancellable = true)
	private void aurorachat$render(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int ticks, int mx, int my,
	                               ChatComponent.DisplayMode mode, boolean cursor, CallbackInfo ci) {
		if (!ChatHud.enabled()) { ChatCore.refreshVanillaIfStale(); return; }
		ci.cancel();
		// в настройках чат показывается в окне предпросмотра — второй раз не рисуем
		var scr = net.primlixe.aurorachat.compat.Mc.screen();
		if (scr instanceof net.primlixe.aurorachat.gui.ChatSettingsScreen || scr instanceof net.primlixe.aurorachat.gui.PunishLogScreen) return;
		long t0 = net.primlixe.aurorachat.ui.Perf.HUD.start();
		try {
			ChatHud.render(g, font, mode.foreground ? ChatHud.Mode.OPEN : ChatHud.Mode.HUD, mx, my);
			if (net.primlixe.aurorachat.premium.Premium.has()) net.primlixe.aurorachat.chat.ExtraWindows.render(g, font, mode.foreground, mx, my);
		} catch (Throwable t) {
			AuroraChatMod.LOG.error("Aurora Chat: ошибка отрисовки, временно включаю ванильный чат", t);
			ChatHud.disableAfterError();
		} finally {
			net.primlixe.aurorachat.ui.Perf.HUD.stop(t0);
		}
	}

	@Inject(method = "captureClickableText", at = @At("HEAD"), cancellable = true)
	private void aurorachat$capture(CallbackInfo ci) {
		if (ChatHud.enabled()) ci.cancel();
	}

	@Inject(method = "scrollChat", at = @At("HEAD"), cancellable = true)
	private void aurorachat$scroll(int lines, CallbackInfo ci) {
		if (!ChatHud.enabled()) return;
		ChatHud.scroll(lines);
		ci.cancel();
	}

	@Inject(method = "resetChatScroll", at = @At("HEAD"))
	private void aurorachat$resetScroll(CallbackInfo ci) {
		if (ChatHud.enabled()) ChatHud.resetScroll();
	}

	/** clearMessages(true) зовётся при выходе с сервера; F3+D передаёт false и по-прежнему очищает. */
	@Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true)
	private void aurorachat$keep(boolean clearSentHistory, CallbackInfo ci) {
		if (clearSentHistory && ChatCore.active() && ChatCore.cfg().keepOnDisconnect) ci.cancel();
	}
}
