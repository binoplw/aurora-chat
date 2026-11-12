package net.primlixe.aurorachat.chat;

import java.nio.file.Path;
import java.util.List;

/** Публичная сборка: журнала наказаний нет — заглушка с тем же API. */
public final class PunishLog {
	public static final String SUGGESTED = "", PENDING = "", ISSUED = "", MANUAL = "", DRY = "", CANCELLED = "", UNDONE = "";

	public static final class Entry {
		public long time;
		public String server = "", player = "", rule = "", text = "", command = "", status = "";
	}

	public static List<Entry> list() { return List.of(); }
	public static void load() {}
	public static void save() {}
	public static Entry add(String player, String rule, String text, String command, String status) { return new Entry(); }
	public static void setStatus(Entry e, String status) {}
	public static void onOutgoing(String msg) {}
	public static String undoCommand(Entry e) { return ""; }
	public static String time(Entry e) { return ""; }
	public static Path export() throws Exception { throw new UnsupportedOperationException(); }
	public static void clear() {}

	private PunishLog() {}
}
