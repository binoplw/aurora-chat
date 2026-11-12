package net.primlixe.aurorachat.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.config.ChatThemes;
import net.primlixe.aurorachat.gui.ChatSettingsScreen;

/**
 * Кадры для витрины мода (GitHub Pages): смена тем, появление сообщений, переводчик, настройки.
 * Только по запросу: AURORA_SHOWCASE=1 gradlew runClientGameTest. Кадры — в build/run/clientGameTest/screenshots (sc_*).
 */
public class ShowcaseTest implements FabricClientGameTest {
	private static final String[][] TALK = {
			{"L", "Alex", "anyone up for the dungeon run tonight?"},
			{"L", "Steve", "count me in, I'll bring the potions"},
			{"G", "Notch", "server event starts in 10 minutes at spawn!"},
			{"L", "Mia_Craft", "can someone help me with redstone? my door won't close"},
			{"L", "Alex", "sure, coming over"},
			{"G", "Builder42", "check out the new castle at -320 / 1450 :)"},
			{"L", "Steve", "gg, that was close"},
	};
	private static final String[][] LIVE = {
			{"L", "Mia_Craft", "it works now, thanks a lot!"},
			{"G", "Notch", "event: build battle, theme — floating islands"},
			{"L", "Alex", "who has spare iron? need 12 for a hopper"},
			{"L", "Steve", "I do, meet me at the shop"},
			{"G", "Builder42", "trading diamonds for elytra, dm me"},
	};
	private static final String[] THEMES = {"Discord", "Aurora", "Telegram", "Неон", "Закат", "iMessage", "Терминал", "Деловой"};

	private static void say(String[] m) {
		net.primlixe.aurorachat.compat.Mc.chat().addClientSystemMessage(Component.literal("[" + m[0] + "] <" + m[1] + "> " + m[2]));
	}

	private static void theme(String name) {
		for (ChatThemes.Theme t : ChatThemes.ALL) if (t.name().equals(name)) { ChatThemes.apply(t); return; }
		throw new AssertionError("Нет темы " + name);
	}

	private static void section(String sec) {
		try {
			var f = ChatSettingsScreen.class.getDeclaredField("last");
			f.setAccessible(true);
			f.set(null, Enum.valueOf((Class) Class.forName("net.primlixe.aurorachat.gui.ChatSettingsScreen$Section"), sec));
		} catch (Exception e) { throw new RuntimeException(e); }
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (!"1".equals(System.getenv("AURORA_SHOWCASE"))) return;
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(true).create()) {
			ctx.waitTicks(60);
			sp.getServer().runCommand("time set 1000");
			sp.getServer().runCommand("weather clear");
			ctx.runOnClient(mc -> {
				ChatConfig c = ChatConfig.get();
				c.resetWindow();
				c.uiLanguage = "en";
				c.windows.clear();
				c.allView = "GL";
				c.sessionSeparator = false;
				c.draft = ""; // черновик прошлых запусков в строке ввода
				c.drafts = false;
				net.primlixe.aurorachat.chat.ChatHud.setTab(0);
				var all = ((net.primlixe.aurorachat.mixin.ChatComponentAccessor) net.primlixe.aurorachat.compat.Mc.chat()).aurorachat$all();
				all.clear();
				((net.primlixe.aurorachat.mixin.ChatComponentAccessor) net.primlixe.aurorachat.compat.Mc.chat()).aurorachat$trimmed().clear();
				for (String[] m : TALK) say(m);
			});
			ctx.waitTicks(10);

			// 1. смена тем — открытый чат
			for (int i = 0; i < THEMES.length; i++) {
				final String t = THEMES[i];
				ctx.runOnClient(mc -> theme(t));
				ctx.setScreen(() -> new ChatScreen("", false));
				ctx.waitTicks(14);
				ctx.takeScreenshot("sc_theme_" + i);
				ctx.setScreen(() -> null);
			}

			// 2. новые сообщения в закрытом чате — плавное появление (кадр на тик)
			ctx.runOnClient(mc -> theme("Aurora"));
			ctx.waitTicks(10);
			int frame = 0;
			for (String[] m : LIVE) {
				ctx.runOnClient(mc -> say(m));
				for (int k = 0; k < 7; k++) {
					ctx.takeScreenshot("sc_live_" + (frame < 10 ? "0" : "") + frame++);
					ctx.waitTick();
				}
			}

			// 3. переводчик
			ctx.runOnClient(mc -> {
				var chat = net.primlixe.aurorachat.compat.Mc.chat();
				chat.addClientSystemMessage(Component.literal("[L] <Lucas_BR> alguém quer trocar esmeraldas por livros encantados?"));
				var all = ((net.primlixe.aurorachat.mixin.ChatComponentAccessor) chat).aurorachat$all();
				var r = new net.primlixe.aurorachat.chat.Translator.Result("anyone want to trade emeralds for enchanted books?", "pt");
				net.primlixe.aurorachat.chat.Translator.apply(all.get(0).content(), "alguém quer trocar esmeraldas por livros encantados?", r, "en");
			});
			ctx.setScreen(() -> new ChatScreen("", false));
			ctx.waitTicks(14);
			ctx.takeScreenshot("sc_translate");
			ctx.setScreen(() -> null);

			// 4. настройки
			for (String s : new String[]{"THEMES", "FEATURES", "COLORS"}) {
				ctx.runOnClient(mc -> section(s));
				ctx.setScreen(() -> new ChatSettingsScreen(null));
				ctx.waitTicks(10);
				ctx.takeScreenshot("sc_settings_" + s.toLowerCase());
			}
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> { ChatConfig.get().uiLanguage = "auto"; theme("Деловой"); });
		}
	}
}
