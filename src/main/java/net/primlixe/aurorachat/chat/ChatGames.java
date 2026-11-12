package net.primlixe.aurorachat.chat;

import net.minecraft.network.chat.Component;

/** Публичная сборка: ответов на викторины нет — заглушка с тем же API. */
public final class ChatGames {
	public static String answer(String question) { return null; }
	public static Component onIncoming(Component body, String plain) { return body; }
	public static void tick() {}
	public static int learnedCount() { return 0; }

	private ChatGames() {}
}
