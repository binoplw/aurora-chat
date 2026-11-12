package net.primlixe.aurorachat.chat;

import net.minecraft.client.Minecraft;
import net.primlixe.aurorachat.config.ChatConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Автоприветствия: кто-то пишет «пр Ник», «ку Ник», «qq Ник», «привет, Ник» — через секунду-две
 * в выбранный канал уходит ответ. Пауза на каждого игрока защищает от спама и антиспама сервера.
 */
public final class AutoGreet {
	private static final Map<String, Long> LAST = new HashMap<>();
	private static final List<Object[]> QUEUE = new ArrayList<>(); // {когда, текст}
	private static final Random RND = new Random();
	private static long lastAny;

	/** Входящее сообщение игрока (не своё). */
	public static void onMessage(ChatFormat.Meta meta, String plain) {
		ChatConfig c = ChatConfig.get();
		if (!c.enabled || !c.autoGreet || meta.sender() == null) return;
		if (meta.cat() == ChatFormat.Category.DM || meta.cat() == ChatFormat.Category.SYSTEM) return;
		var mc = Minecraft.getInstance();
		if (mc.player == null) return;
		String self = mc.player.getName().getString();
		String sender = meta.sender();
		if (sender.equalsIgnoreCase(self)) return;

		// клановый чат: отвечать туда же (если задано, как писать в клан), иначе не отвечать вовсе — не в G
		boolean clan = ChatHud.isClanText(plain.toLowerCase(Locale.ROOT));
		String clanCmd = c.greetClan == null ? "" : c.greetClan.trim();
		if (clan && clanCmd.isEmpty()) return;

		// текст после ника отправителя
		String body = Channels.stripTag(plain);
		if (clan) { // «КЛАН: [ИИ-бот] Ник: текст» — убираем приставку клана
			int cp = body.toLowerCase(Locale.ROOT).indexOf(c.clanPrefix.toLowerCase(Locale.ROOT));
			if (cp >= 0) body = body.substring(cp + c.clanPrefix.length());
		}
		int at = body.toLowerCase(Locale.ROOT).indexOf(sender.toLowerCase(Locale.ROOT));
		if (at >= 0) body = body.substring(at + sender.length());
		body = body.replaceFirst("^[\\s>:»|\\]\\-]+", "");
		String[] tokens = body.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_]+");
		String word = null;
		boolean me = false, everyone = false;
		for (String tk : tokens) {
			if (tk.isEmpty()) continue;
			if (tk.equalsIgnoreCase(self)) me = true;
			for (String m : c.mentionWords) if (!m.isBlank() && tk.equalsIgnoreCase(m.trim())) me = true;
			if (tk.equals("всем") || tk.equals("all") || tk.equals("народ") || tk.equals("everyone") || tk.equals("ребята")) everyone = true;
			if (word == null && c.greetWords.stream().anyMatch(g -> g.equalsIgnoreCase(tk))) word = tk;
		}
		if (word == null) return;
		if (!me && !(c.greetAll && everyone)) return;
		if (tokens.length > 8) return; // длинное сообщение — это уже не приветствие

		long now = System.currentTimeMillis();
		String key = sender.toLowerCase(Locale.ROOT);
		if (now - LAST.getOrDefault(key, 0L) < c.greetCooldown * 1000L || now - lastAny < 4000) return;
		LAST.put(key, now);
		lastAny = now;

		String reply;
		if (c.greetMirror) {
			reply = word.length() <= 3 ? word + " " + sender : Character.toUpperCase(word.charAt(0)) + word.substring(1) + ", " + sender + "!";
		} else {
			reply = c.greetReplies.get(RND.nextInt(c.greetReplies.size())).replace("{name}", sender).replace("{word}", word);
		}
		QUEUE.add(new Object[]{now + 1200 + RND.nextInt(1300), reply, clan ? clanCmd : null});
	}

	/** Отправка по таймеру (вызывается каждый тик клиента). */
	public static void tick() {
		if (QUEUE.isEmpty()) return;
		var mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null) { QUEUE.clear(); return; }
		long now = System.currentTimeMillis();
		for (int i = 0; i < QUEUE.size(); i++) {
			Object[] q = QUEUE.get(i);
			if ((long) q[0] > now) continue;
			QUEUE.remove(i--);
			String clanCmd = (String) q[2];
			String out = clanCmd != null ? toClan(clanCmd, (String) q[1]) : outgoing((String) q[1]);
			if (out.startsWith("/")) mc.getConnection().sendCommand(out.substring(1));
			else mc.getConnection().sendChat(out);
			ConsoleLog.add(ConsoleLog.Level.MOD, "§dАвтоприветствие" + (clanCmd != null ? " (клан)" : "") + ": §f" + out);
		}
	}

	/** Ответ в клан: «/c» → «/c текст», «/clan chat {text}» → подстановка, «#» → «#текст». */
	public static String toClan(String cmd, String text) {
		if (cmd.contains("{text}")) return cmd.replace("{text}", text);
		return cmd.startsWith("/") ? cmd + " " + text : cmd + text;
	}

	/** Текст ответа с цветом и префиксом нужного канала. */
	static String outgoing(String text) {
		ChatConfig c = ChatConfig.get();
		String ch = c.greetChannel.equals("CURRENT") ? c.channel : c.greetChannel;
		if (!c.channels) return text;
		String code = ch.equals("G") ? c.colorGlobal : c.colorLocal;
		if (c.autoColor && code != null && !code.isEmpty()) text = code + text;
		String prefix = ch.equals("G") ? c.globalPrefix : c.localPrefix;
		return (prefix == null ? "" : prefix) + text;
	}

	private AutoGreet() {}
}
