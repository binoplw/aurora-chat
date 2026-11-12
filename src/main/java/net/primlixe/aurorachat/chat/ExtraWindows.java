package net.primlixe.aurorachat.chat;

/** Заглушка: эта функция входит в Aurora Chat+, её код не публикуется. Полная версия — modrinth.com/mod/aurora-ryizen */
@SuppressWarnings("all")
public final class ExtraWindows {
	public static final java.lang.String[] KINDS = new java.lang.String[0];
	public static final java.lang.String[] KINDS_RU = new java.lang.String[0];
	static boolean matches(net.primlixe.aurorachat.config.ChatConfig.ExtraWindow a0, net.primlixe.aurorachat.chat.ChatFormat.Meta a1, net.minecraft.client.multiplayer.chat.GuiMessage a2) { return false; }
	public static boolean takesExclusive(net.minecraft.client.multiplayer.chat.GuiMessage a0, net.primlixe.aurorachat.chat.ChatFormat.Meta a1) { return false; }
	public static net.minecraft.client.multiplayer.chat.GuiMessage messageAt(double a0, double a1) { return null; }
	public static boolean over(double a0, double a1) { return false; }
	public static void render(net.minecraft.client.gui.GuiGraphicsExtractor a0, net.minecraft.client.gui.Font a1, boolean a2, int a3, int a4) { }
	public static boolean click(double a0, double a1, int a2) { return false; }
	public static boolean dragging() { return false; }
	public static void dragTo(int a0, int a1, double a2, double a3) { }
	public static void endDrag() { }
	public static boolean scroll(double a0, double a1, double a2) { return false; }
	public static net.primlixe.aurorachat.config.ChatConfig.ExtraWindow addDefault(java.lang.String a0) { return null; }
}
