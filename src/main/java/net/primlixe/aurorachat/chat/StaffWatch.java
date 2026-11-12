package net.primlixe.aurorachat.chat;

import net.minecraft.network.chat.Component;
import net.primlixe.aurorachat.config.ChatConfig;

import java.util.List;
import java.util.Set;

/** Публичная сборка: слежки за персоналом нет — заглушка с тем же API. */
public final class StaffWatch {
	public static ChatConfig.Staff find(String nick) { return null; }
	public static int onlineCount() { return 0; }
	public static Set<String> online() { return Set.of(); }
	public static List<Component> onlineLines() { return List.of(); }
	public static void reset() {}
	public static void tick() {}
	public static void fromChat(ChatFormat.Meta m) {}
	public static int rankIndex(String role) { return -1; }
	public static int rankColor(String rank) { return 0xFFFFFFFF; }
	public static void learn(String legacy, boolean live) {}
	public static void scanLogs() {}

	private StaffWatch() {}
}
