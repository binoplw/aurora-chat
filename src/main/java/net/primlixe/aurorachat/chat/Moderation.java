package net.primlixe.aurorachat.chat;

import net.primlixe.aurorachat.config.ChatConfig.ModRule;

import java.util.List;

/** Публичная сборка (Modrinth): модерации нет — пустая заглушка с тем же API. Настоящая — src/main (личная сборка). */
public final class Moderation {
	public record Hit(long time, String player, String rule, String text, String command, boolean auto) {}

	public static final String[] TYPES = {};
	public static final String[] TYPES_RU = {};
	public static final String[] TEMPLATES = {};

	public static boolean notifying() { return false; }
	public static List<Hit> recent() { return List.of(); }
	public static ModRule fromText(String text) { return null; }
	public static String resolve(String action, String name, String rule, int count, String text) { return ""; }
	public static List<ModRule> template(int i) { return List.of(); }
	public static List<ModRule> mineblaze() { return List.of(); }
	public static List<String[]> serverRules() { return List.of(); }
	public static String ruleNumber(String s) { return ""; }
	public static int priorCount(String player, String ruleName) { return 0; }
	public static String human(String action) { return ""; }
	public static List<ModRule> importText(String text) { return List.of(); }
	public static List<String> defaults(String type) { return List.of(); }
	public static ModRule preset(String type, String name) { return null; }
	public static ModRule test(String body) { return null; }
	public static void check(ChatFormat.Meta meta, String plain) {}
	public static int cancelAll() { return 0; }
	public static int pending() { return 0; }
	public static void tick() {}
	public static void reset() {}

	private Moderation() {}
}
