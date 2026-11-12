package net.primlixe.aurorachat.chat;

/** Заглушка: эта функция входит в Aurora Chat+, её код не публикуется. Полная версия — modrinth.com/mod/aurora-ryizen */
@SuppressWarnings("all")
public final class AutoJoin {
	public static java.util.List<net.primlixe.aurorachat.chat.AutoJoin.Entry> entries() { return java.util.List.of(); }
	public static void load() { }
	public static void save() { }
	public static java.lang.String currentServer() { return ""; }
	public static void onJoin() { }
	public static void tick() { }

	public static final class Entry {
		public java.lang.String server;
		public java.util.List<java.lang.String> commands;
		public int delay;
		public boolean enabled;
		public Entry() {  }
	}
}
