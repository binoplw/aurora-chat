package net.primlixe.aurorachat.chat;

/** Заглушка: эта функция входит в Aurora Chat+, её код не публикуется. Полная версия — modrinth.com/mod/aurora-ryizen */
@SuppressWarnings("all")
public final class Translator {
	public static final java.lang.String[] LANGS = new java.lang.String[0];
	public static final java.lang.String[] LANG_NAMES = new java.lang.String[0];
	public static java.lang.String target() { return ""; }
	public static java.lang.String normLang(java.lang.String a0, java.lang.String a1) { return a0; }
	public static java.util.concurrent.CompletableFuture<net.primlixe.aurorachat.chat.Translator.Result> translate(java.lang.String a0, java.lang.String a1) { return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Aurora Chat+")); }
	public static net.primlixe.aurorachat.chat.Translator.Result parse(java.lang.String a0) { return null; }
	public static boolean isTranslated(net.minecraft.network.chat.Component a0) { return false; }
	public static net.minecraft.network.chat.Component originalOf(net.minecraft.network.chat.Component a0) { return a0; }
	public static java.lang.String bodyOf(java.lang.String a0, java.lang.String a1) { return a0; }
	public static void translateMessage(net.minecraft.client.multiplayer.chat.GuiMessage a0) { }
	public static void showOriginal(net.minecraft.client.multiplayer.chat.GuiMessage a0) { }
	public static void maybeAuto(net.minecraft.network.chat.Component a0, net.primlixe.aurorachat.chat.ChatFormat.Meta a1) { }
	public static boolean looksForeign(java.lang.String a0, java.lang.String a1) { return false; }
	public static boolean apply(net.minecraft.network.chat.Component a0, java.lang.String a1, net.primlixe.aurorachat.chat.Translator.Result a2, java.lang.String a3) { return false; }
	public static java.lang.String translateInput(java.lang.String a0, java.util.function.Consumer<java.lang.String> a1) { return a0; }
	public static void translateOutgoing(java.lang.String a0, java.util.function.Consumer<java.lang.String> a1) { }
	public static java.lang.String nextLang(java.lang.String a0) { return a0; }

	public record Result(java.lang.String text, java.lang.String from) {
	}
}
