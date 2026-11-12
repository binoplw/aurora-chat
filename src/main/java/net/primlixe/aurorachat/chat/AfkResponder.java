package net.primlixe.aurorachat.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.primlixe.aurorachat.config.ChatConfig;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * АФК: автоответчик на личные (если игрок неактивен N минут или включил АФК вручную) и сводка
 * «пока тебя не было»: кто писал в ЛС, кто упоминал, нарушения, кто из персонала заходил.
 * Сводка работает и без автоответчика — по простою.
 */
public final class AfkResponder {
	private static long lastActive = System.currentTimeMillis();
	private static double lx, ly, lz;
	private static float lyaw, lpitch;
	private static boolean manual, wasAway;
	private static long awaySince;
	private static final Map<String, Long> LAST = new HashMap<>();

	// ---- сводка за время отсутствия
	private static final Map<String, Integer> DMS = new LinkedHashMap<>();       // ник → сколько ЛС
	private static final Map<String, String> DM_LAST = new HashMap<>();          // ник → последнее ЛС
	private static final Map<String, Integer> MENTIONS = new LinkedHashMap<>();  // ник → сколько упоминаний
	private static final Set<String> STAFF = new LinkedHashSet<>();              // «Хелпер Ник»
	private static int violations;
	private static String lastViolation;

	/** Включён автоответчик и игрок сейчас «АФК». */
	public static boolean isAfk() {
		ChatConfig c = ChatConfig.get();
		if (!c.enabled || !c.afk) return manual;
		return manual || (c.afkMinutes > 0 && System.currentTimeMillis() - lastActive > c.afkMinutes * 60_000L);
	}

	/** Отошёл (для сводки): вручную или простой дольше порога (без автоответчика — 5 мин). */
	public static boolean away() {
		ChatConfig c = ChatConfig.get();
		long limit = (c.afkMinutes > 0 ? c.afkMinutes : 5) * 60_000L;
		return manual || System.currentTimeMillis() - lastActive > limit;
	}

	public static boolean toggleManual() {
		manual = !manual;
		if (!manual) activity();
		return manual;
	}

	public static void activity() { lastActive = System.currentTimeMillis(); }

	/** Каждый тик: движение, повороты, открытые окна — это активность. */
	public static void tick() {
		var mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null) return;
		boolean moved = Math.abs(p.getX() - lx) + Math.abs(p.getY() - ly) + Math.abs(p.getZ() - lz) > 0.05
				|| Math.abs(p.getYRot() - lyaw) + Math.abs(p.getXRot() - lpitch) > 0.5f;
		lx = p.getX(); ly = p.getY(); lz = p.getZ(); lyaw = p.getYRot(); lpitch = p.getXRot();
		if (moved || net.primlixe.aurorachat.compat.Mc.screen() != null) {
			// ручной АФК снимается только самим игроком
			if (!manual) lastActive = System.currentTimeMillis();
		}
		boolean away = away();
		if (away && !wasAway) awaySince = lastActive; // отошёл тогда, когда последний раз что-то делал
		if (wasAway && !away) summary();
		wasAway = away;
	}

	// ------------------------------------------------------------ события для сводки

	private static boolean collecting() { return wasAway && ChatConfig.get().afkSummary; }

	static void noteMention(String from) {
		if (collecting() && from != null) MENTIONS.merge(from, 1, Integer::sum);
	}

	static void noteViolation(String who, String rule) {
		if (!collecting()) return;
		violations++;
		lastViolation = who + " — «" + rule + "»";
	}

	static void noteStaff(String role, String nick) {
		if (collecting()) STAFF.add(role + " " + nick);
	}

	/** Пришло личное сообщение от игрока: в сводку и, если АФК, — автоответ. */
	public static void onDm(String sender, String text) {
		ChatConfig c = ChatConfig.get();
		var mc = Minecraft.getInstance();
		if (sender == null || mc.player == null || mc.getConnection() == null) return;
		if (sender.equalsIgnoreCase(mc.player.getName().getString())) return;
		if (collecting()) {
			DMS.merge(sender, 1, Integer::sum);
			if (text != null) DM_LAST.put(sender, text.length() > 80 ? text.substring(0, 80) + "…" : text);
		}
		if (!isAfk()) return;
		long now = System.currentTimeMillis();
		String key = sender.toLowerCase(Locale.ROOT);
		if (now - LAST.getOrDefault(key, 0L) < c.afkCooldown * 1000L) return;
		LAST.put(key, now);
		String cmd = c.afkCommand.replace("{name}", sender).replace("{text}", c.afkMessage);
		cmd = AutoColor.apply(cmd);
		if (cmd.startsWith("/")) mc.getConnection().sendCommand(cmd.substring(1));
		else mc.getConnection().sendChat(Channels.applyOutgoing(cmd));
		ConsoleLog.add(ConsoleLog.Level.MOD, "§dАФК-ответ " + sender + ": §f" + cmd);
		net.primlixe.aurorachat.compat.Mc.chat().addClientSystemMessage(Component.literal("☾ АФК-ответ отправлен " + sender).withStyle(ChatFormatting.DARK_GRAY));
	}

	// ------------------------------------------------------------ сводка

	private static void summary() {
		int dms = DMS.values().stream().mapToInt(Integer::intValue).sum();
		int ments = MENTIONS.values().stream().mapToInt(Integer::intValue).sum();
		boolean any = dms > 0 || ments > 0 || violations > 0 || !STAFF.isEmpty();
		var mc = Minecraft.getInstance();
		if (any && ChatConfig.get().afkSummary && mc.player != null) {
			long mins = Math.max(1, (System.currentTimeMillis() - awaySince) / 60_000);
			String away = mins >= 60 ? (mins / 60) + " ч " + (mins % 60) + " мин" : mins + " мин";
			MutableComponent card = Component.literal("☾ Пока тебя не было (" + away + "):").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
			if (dms > 0) {
				StringBuilder tip = new StringBuilder("Последние сообщения:");
				for (var e : DMS.entrySet()) tip.append("\n").append(e.getKey()).append(" ×").append(e.getValue()).append(": ").append(DM_LAST.getOrDefault(e.getKey(), ""));
				card.append(line("✉ ЛС: " + dms + " от " + names(DMS), ChatFormatting.LIGHT_PURPLE, tip.toString()));
			}
			if (ments > 0) card.append(line("@ Упоминали: " + ments + " — " + names(MENTIONS), ChatFormatting.GOLD, null));
			if (violations > 0) card.append(line("⚑ Нарушений: " + violations + (lastViolation != null ? " · последнее: " + lastViolation : ""), ChatFormatting.RED,
					"Подробно — /achat punish"));
			if (!STAFF.isEmpty()) card.append(line("★ Персонал заходил: " + String.join(", ", STAFF), ChatFormatting.AQUA, null));
			net.primlixe.aurorachat.compat.Mc.chat().addClientSystemMessage(card);
			ChatToasts.push("С возвращением!", (dms > 0 ? dms + " ЛС · " : "") + (ments > 0 ? ments + " упом. · " : "") + (violations > 0 ? violations + " наруш." : ""),
					DMS.isEmpty() ? null : DMS.keySet().iterator().next(), 0xFFB18CFF);
		}
		DMS.clear();
		DM_LAST.clear();
		MENTIONS.clear();
		STAFF.clear();
		violations = 0;
		lastViolation = null;
	}

	private static Component line(String text, ChatFormatting color, String hover) {
		MutableComponent l = Component.literal("\n  " + text).withStyle(color);
		if (hover != null) l = l.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
		return l;
	}

	private static String names(Map<String, Integer> m) {
		StringBuilder sb = new StringBuilder();
		int n = 0;
		for (var e : m.entrySet()) {
			if (n++ == 4) { sb.append(" и ещё ").append(m.size() - 4); break; }
			if (sb.length() > 0) sb.append(", ");
			sb.append(Nicknames.display(e.getKey())).append(e.getValue() > 1 ? " ×" + e.getValue() : "");
		}
		return sb.toString();
	}

	private AfkResponder() {}
}
