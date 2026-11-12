package net.primlixe.aurorachat.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.primlixe.aurorachat.chat.ChatLogScreen;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.gui.ChatSettingsScreen;

/**
 * Публичная сборка (gradlew runClientGameTest -Pedition=public): мод грузится, чат, все разделы настроек, журнал,
 * консоль и переводчик работают без классов под Mineblaze (вместо них заглушки). В личной сборке — пропускается.
 */
public class PublicSmokeTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if ("1".equals(System.getenv("AURORA_SHOWCASE"))) return; // снимаем витрину — только ShowcaseTest
		if (!net.primlixe.aurorachat.Edition.PUBLIC) return;
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(true).create()) {
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> {
				ChatConfig.get().resetWindow();
				var chat = net.primlixe.aurorachat.compat.Mc.chat();
				chat.addClientSystemMessage(Component.literal("[ʟ] [Игрок] Bob ✪ → hello everyone, how are you?"));
				chat.addClientSystemMessage(Component.literal("[ʟ] <Alex> всем привет"));
				chat.addClientSystemMessage(Component.literal("Chat Game » Решите пример: 5 + 41. Ответ напишите в чат. Награда: 5000$"));
			});
			sp.getServer().runCommand("say [Хелпер] Steve → тест персонала");
			ctx.waitTicks(5);
			ctx.runOnClient(mc -> {
				var all = ((net.primlixe.aurorachat.mixin.ChatComponentAccessor) net.primlixe.aurorachat.compat.Mc.chat()).aurorachat$all();
				for (var m : all) if (m.content().getString().contains("[= 46]")) throw new AssertionError("В публичной сборке есть ответ на викторину");
				var bob = all.stream().filter(m -> m.content().getString().contains("hello everyone")).findFirst().orElseThrow();
				var r = net.primlixe.aurorachat.chat.Translator.parse("[[[\"Всем привет, как дела?\",\"x\",null,null,10]],null,\"en\"]");
				if (!net.primlixe.aurorachat.chat.Translator.apply(bob.content(), "hello everyone, how are you?", r, "ru")) throw new AssertionError("перевод не применился");
			});
			ctx.setScreen(() -> new ChatScreen("", false));
			ctx.waitTicks(15);
			ctx.takeScreenshot("public_chat");
			ctx.setScreen(() -> null);
			for (String sec : new String[]{"FEATURES", "THEMES", "WINDOW", "BACKGROUND", "LOOK", "ANIM", "MINECRAFT", "INPUT", "COLORS", "CHANNELS", "AUTO", "WHEEL",
					"MODERATION", "MESSAGES", "TABS", "WINDOWS", "STAFF", "AUTOJOIN", "NOTIFY", "HISTORY", "FILTER", "UI_SETTINGS"}) {
				ctx.runOnClient(mc -> {
					try {
						var f = ChatSettingsScreen.class.getDeclaredField("last");
						f.setAccessible(true);
						f.set(null, Enum.valueOf((Class) Class.forName("net.primlixe.aurorachat.gui.ChatSettingsScreen$Section"), sec));
					} catch (Exception e) { throw new RuntimeException(e); }
				});
				ctx.setScreen(() -> new ChatSettingsScreen(null));
				ctx.waitTicks(6);
				ctx.takeScreenshot("public_settings_" + sec.toLowerCase());
			}
			ctx.setScreen(() -> null);
			// ---- английский интерфейс
			ctx.runOnClient(mc -> {
				if (net.primlixe.aurorachat.ui.I18n.size() < 900) throw new AssertionError("Словарь en.json не загрузился: " + net.primlixe.aurorachat.ui.I18n.size());
				ChatConfig.get().uiLanguage = "en";
				String a = net.primlixe.aurorachat.ui.I18n.t("§7Все функции ");
				if (!a.equals("§7All features ")) throw new AssertionError("I18n: " + a);
				String f = net.primlixe.aurorachat.ui.I18n.f("@ Упоминание от %s", "Bob");
				if (!f.equals("@ Mention from Bob")) throw new AssertionError("I18n.f: " + f);
				if (!net.primlixe.aurorachat.ui.I18n.t("привет от игрока").equals("привет от игрока")) throw new AssertionError("Незнакомый текст должен остаться как есть");
				// обновления: сравнение версий и ответ Modrinth
				if (!net.primlixe.aurorachat.chat.UpdateCheck.isNewer("2.26.0+mc26.1.2", "2.25.0+mc26.1.2-public")) throw new AssertionError("2.26 > 2.25");
				if (net.primlixe.aurorachat.chat.UpdateCheck.isNewer("2.25.0+mc26.1.2", "2.25.0+mc26.1.2")) throw new AssertionError("та же версия — не новее");
				if (net.primlixe.aurorachat.chat.UpdateCheck.isNewer("2.9.0", "2.10.0")) throw new AssertionError("2.9 < 2.10");
				String[] l = net.primlixe.aurorachat.chat.UpdateCheck.latest("[{\"version_number\":\"3.0.0-beta\",\"id\":\"b\",\"version_type\":\"beta\"},{\"version_number\":\"2.26.0\",\"id\":\"abc\",\"version_type\":\"release\"}]");
				if (l == null || !l[0].equals("2.26.0") || !l[1].equals("abc")) throw new AssertionError("latest: бета должна пропускаться");
			});
			for (String sec : new String[]{"FEATURES", "THEMES", "MESSAGES", "UI_SETTINGS"}) {
				ctx.runOnClient(mc -> {
					try {
						var fl = ChatSettingsScreen.class.getDeclaredField("last");
						fl.setAccessible(true);
						fl.set(null, Enum.valueOf((Class) Class.forName("net.primlixe.aurorachat.gui.ChatSettingsScreen$Section"), sec));
					} catch (Exception e) { throw new RuntimeException(e); }
				});
				ctx.setScreen(() -> new ChatSettingsScreen(null));
				ctx.waitTicks(6);
				ctx.takeScreenshot("public_en_" + sec.toLowerCase());
			}
			ctx.setScreen(() -> new ChatScreen("", false));
			ctx.waitTicks(10);
			ctx.takeScreenshot("public_en_chat");
			ctx.setScreen(() -> null);
			ctx.runOnClient(mc -> ChatConfig.get().uiLanguage = "auto");
			ctx.setScreen(() -> new ChatLogScreen(null, ""));
			ctx.waitTicks(6);
			ctx.setScreen(() -> new net.primlixe.aurorachat.gui.ConsoleScreen(null));
			ctx.waitTicks(6);
			ctx.setScreen(() -> new net.primlixe.aurorachat.gui.PunishLogScreen(null)); // заглушка — сразу закрывается
			ctx.waitTicks(3);
			ctx.setScreen(() -> null);
		}
	}
}
