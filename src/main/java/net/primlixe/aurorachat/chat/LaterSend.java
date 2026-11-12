package net.primlixe.aurorachat.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.primlixe.aurorachat.config.ChatConfig;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Отложенная отправка: сообщение уходит через N минут (или в чч:мм). Шаблоны вроде {xyz}
 * подставляются в момент отправки, канал G/L — тот, что был выбран при написании.
 */
public final class LaterSend {

	public static String add(String text, long at) {
		ChatConfig c = ChatConfig.get();
		if (text == null || text.isBlank() || at <= 0) return null;
		ChatConfig.Scheduled s = new ChatConfig.Scheduled();
		s.at = at;
		s.text = text.trim();
		s.channel = c.channel;
		c.scheduled.add(s);
		ChatConfig.save();
		return describe(s);
	}

	/** «10m текст» / «20:30 текст». */
	public static String add(String args) {
		args = args.trim();
		int sp = args.indexOf(' ');
		if (sp <= 0) return null;
		long at = Reminders.parseWhen(args.substring(0, sp));
		return at < 0 ? null : add(args.substring(sp + 1), at);
	}

	public static String describe(ChatConfig.Scheduled s) {
		LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(s.at), ZoneId.systemDefault());
		return t.format(DateTimeFormatter.ofPattern("HH:mm")) + " [" + s.channel + "] " + s.text;
	}

	public static void tick() {
		ChatConfig c = ChatConfig.get();
		if (c.scheduled.isEmpty()) return;
		var mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null) return; // ждём, пока зайдём на сервер
		long now = System.currentTimeMillis();
		boolean changed = false;
		for (var it = c.scheduled.iterator(); it.hasNext(); ) {
			ChatConfig.Scheduled s = it.next();
			if (s.at > now) continue;
			it.remove();
			changed = true;
			send(s);
		}
		if (changed) ChatConfig.save();
	}

	private static void send(ChatConfig.Scheduled s) {
		var mc = Minecraft.getInstance();
		ChatConfig c = ChatConfig.get();
		String msg = s.text;
		if (c.emojiShortcodes) msg = ChatFormat.emojify(msg);
		msg = Templates.apply(msg);
		String saved = c.channel;
		c.channel = s.channel == null ? saved : s.channel; // канал на момент написания
		try {
			msg = AutoColor.apply(msg);
			msg = ColorCodes.expand(msg);
			msg = Channels.applyOutgoing(msg);
		} finally {
			c.channel = saved;
		}
		if (msg.startsWith("/")) mc.getConnection().sendCommand(msg.substring(1));
		else mc.getConnection().sendChat(msg);
		ConsoleLog.add(ConsoleLog.Level.MOD, "§dОтложенное: §f" + ConsoleLog.mask(msg));
		net.primlixe.aurorachat.compat.Mc.chat().addRecentChat(s.text);
		net.primlixe.aurorachat.compat.Mc.chat().addClientSystemMessage(Component.literal(net.primlixe.aurorachat.ui.I18n.t("⏱ Отложенное отправлено")).withStyle(ChatFormatting.DARK_GRAY));
	}

	private LaterSend() {}
}
