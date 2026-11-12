package net.primlixe.aurorachat.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.primlixe.aurorachat.config.ChatConfig;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Напоминания и таймеры: «10m текст», «1h30m текст», «45s», «20:30 текст».
 * Срабатывают карточкой, звуком и строкой в чате; переживают перезапуск игры.
 */
public final class Reminders {
	private static final Pattern DUR = Pattern.compile("^(?:(\\d+)\\s*(?:h|ч))?\\s*(?:(\\d+)\\s*(?:m|м|мин|min))?\\s*(?:(\\d+)\\s*(?:s|с|сек|sec))?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern CLOCK = Pattern.compile("^(\\d{1,2})[:.](\\d{2})$");

	/** Разобрать время: длительность или «чч:мм». Возвращает момент срабатывания (мс) или -1. */
	public static long parseWhen(String token) {
		token = token.trim().toLowerCase();
		Matcher c = CLOCK.matcher(token);
		if (c.matches()) {
			int h = Integer.parseInt(c.group(1)), m = Integer.parseInt(c.group(2));
			if (h > 23 || m > 59) return -1;
			LocalDateTime at = LocalDateTime.now().with(LocalTime.of(h, m));
			if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1);
			return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		}
		if (token.matches("^\\d+$")) return System.currentTimeMillis() + Long.parseLong(token) * 60_000L; // просто число — минуты
		Matcher d = DUR.matcher(token);
		if (!d.matches() || token.isEmpty()) return -1;
		long ms = 0;
		if (d.group(1) != null) ms += Long.parseLong(d.group(1)) * 3_600_000L;
		if (d.group(2) != null) ms += Long.parseLong(d.group(2)) * 60_000L;
		if (d.group(3) != null) ms += Long.parseLong(d.group(3)) * 1000L;
		return ms <= 0 ? -1 : System.currentTimeMillis() + ms;
	}

	/** «10m текст» → добавить. Возвращает описание для сообщения или null при ошибке. */
	public static String add(String args, String defaultText) {
		args = args.trim();
		int sp = args.indexOf(' ');
		String when = sp < 0 ? args : args.substring(0, sp);
		String text = sp < 0 ? defaultText : args.substring(sp + 1).trim();
		long at = parseWhen(when);
		if (at < 0) return null;
		ChatConfig.Reminder r = new ChatConfig.Reminder();
		r.at = at;
		r.text = text.isEmpty() ? defaultText : text;
		ChatConfig.get().reminders.add(r);
		ChatConfig.save();
		return describe(r);
	}

	public static String describe(ChatConfig.Reminder r) {
		LocalDateTime t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(r.at), ZoneId.systemDefault());
		long left = Math.max(0, (r.at - System.currentTimeMillis()) / 1000);
		String in = left >= 3600 ? (left / 3600) + net.primlixe.aurorachat.ui.I18n.t(" ч ") + (left % 3600 / 60) + net.primlixe.aurorachat.ui.I18n.t(" мин") : left >= 60 ? (left / 60) + net.primlixe.aurorachat.ui.I18n.t(" мин") : left + net.primlixe.aurorachat.ui.I18n.t(" с");
		return t.format(DateTimeFormatter.ofPattern("HH:mm")) + net.primlixe.aurorachat.ui.I18n.t(" (через ") + in + ") — " + r.text;
	}

	public static void tick() {
		var list = ChatConfig.get().reminders;
		if (list.isEmpty()) return;
		long now = System.currentTimeMillis();
		boolean changed = false;
		for (var it = list.iterator(); it.hasNext(); ) {
			ChatConfig.Reminder r = it.next();
			if (r.at > now) continue;
			it.remove();
			changed = true;
			fire(r);
		}
		if (changed) ChatConfig.save();
	}

	private static void fire(ChatConfig.Reminder r) {
		var mc = Minecraft.getInstance();
		ConsoleLog.add(ConsoleLog.Level.MOD, "§e⏰ Напоминание: §f" + r.text);
		ChatToasts.push(net.primlixe.aurorachat.ui.I18n.t("⏰ Напоминание"), r.text, null, 0xFFFFD166);
		if (ChatConfig.get().reminderSound) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f, 0.9f));
		if (mc.player != null) {
			net.primlixe.aurorachat.compat.Mc.chat().addClientSystemMessage(Component.literal("⏰ ").withStyle(ChatFormatting.GOLD)
					.append(Component.literal(r.text).withStyle(ChatFormatting.YELLOW)));
		}
	}

	private Reminders() {}
}
