package net.primlixe.aurorachat.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.primlixe.aurorachat.chat.ChatHud;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.auroralib.perf.Prof;

/**
 * Нагрузка: полная история (2000), закрытый/открытый чат, прокрутка, спам повторами (×N), поток новых.
 * Печатает строки «PERF|фаза|участок|...» — сколько мс тратит Aurora Chat.
 */
public class ChatPerfTest implements FabricClientGameTest {
	private static final String[] NICKS = {"Steve", "Alex", "Notch", "Herobrine", "Kirill_2010", "xX_Pro_Xx", "Masha", "Dimon"};
	private static final String[] TEXTS = {
			"всем привет, кто на ивент?", "продам алмазы недорого, пишите в лс", "зацени https://primlixe.net :)",
			"кто пойдёт со мной в шахту на нижнем уровне, нужны кирки и факелы, беру всех", "ахахах",
			"&aзелёный &bголубой &cкрасный текст", "го пвп на спавне", "у кого есть элитры? меняю на незеритовый меч и 2 стака изумрудов"
	};

	private static void phase(ClientGameTestContext ctx, String name) {
		ctx.runOnClient(mc -> {
			for (Prof.Section s : Prof.sections()) {
				if (s.worstMs() == 0 && s.callsPerSecond() == 0) continue;
				System.out.printf(java.util.Locale.ROOT, "PERF|%s|%s|avg=%.3f ms|max1s=%.2f ms|worst=%.2f ms|calls/s=%.0f|spikes=%d%n",
						name, s.name, s.avgMs(), s.maxMs(), s.worstMs(), s.callsPerSecond(), s.spikes());
			}
			Prof.resetAll();
		});
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if ("1".equals(System.getenv("AURORA_SHOWCASE"))) return; // снимаем витрину — только ShowcaseTest
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(true).create()) {
			ctx.waitTicks(40); // ждём прогрузку (API ожидания чанков разное в 26.1/26.2)
			ctx.runOnClient(mc -> {
				ChatConfig c = ChatConfig.get();
				c.resetWindow();
				c.historySize = 2000;
				Prof.resetAll();
			});
			// ---- полная история
			long fill = ctx.computeOnClient(mc -> {
				long t = System.nanoTime();
				var chat = net.primlixe.aurorachat.compat.Mc.chat();
				for (int i = 0; i < 2000; i++) {
					String n = NICKS[i % NICKS.length];
					String ch = i % 3 == 0 ? "[L] " : "[G] ";
					chat.addClientSystemMessage(Component.literal(ch + "<" + n + "> " + TEXTS[(i * 7) % TEXTS.length] + " #" + i));
				}
				return (System.nanoTime() - t) / 1_000_000L;
			});
			System.out.println("PERF|fill|2000 сообщений за " + fill + " мс");
			phase(ctx, "заполнение");

			ctx.waitTicks(60);
			phase(ctx, "HUD закрыт");

			ctx.setScreen(() -> new ChatScreen("", false));
			ctx.waitTicks(60);
			phase(ctx, "чат открыт");

			for (int k = 0; k < 40; k++) {
				ctx.runOnClient(mc -> ChatHud.wheel(1));
				ctx.waitTick();
			}
			ctx.waitTicks(20);
			phase(ctx, "прокрутка вверх");

			ctx.runOnClient(mc -> ChatHud.setQuery("алмазы"));
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> ChatHud.setQuery(""));
			phase(ctx, "поиск");

			// ---- спам одинаковыми (склейка ×N)
			ctx.setScreen(() -> null);
			for (int k = 0; k < 60; k++) {
				ctx.runOnClient(mc -> net.primlixe.aurorachat.compat.Mc.chat().addClientSystemMessage(Component.literal("[G] <Spammer> КУПИТЕ ДОНАТ НА САЙТЕ")));
				ctx.waitTick();
			}
			phase(ctx, "спам ×N");

			// ---- поток новых (обрезка истории по лимиту)
			for (int k = 0; k < 60; k++) {
				final int kk = k;
				ctx.runOnClient(mc -> {
					for (int j = 0; j < 5; j++) net.primlixe.aurorachat.compat.Mc.chat().addClientSystemMessage(Component.literal("[G] <" + NICKS[j] + "> поток " + kk + "/" + j));
				});
				ctx.waitTick();
			}
			phase(ctx, "поток 300 новых");

			ctx.setScreen(() -> new ChatScreen("", false));
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				try {
					var f = ChatScreen.class.getDeclaredField("input");
					f.setAccessible(true);
					((net.minecraft.client.gui.components.EditBox) f.get(net.primlixe.aurorachat.compat.Mc.screen())).setValue("&{#ff0000-#0000ff}градиент и &aцвет, привет Steve");
				} catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
			});
			ctx.waitTicks(40);
			phase(ctx, "ввод с цветами");
			ctx.setScreen(() -> null);
			ctx.takeScreenshot("perf_end");
		}
	}
}
