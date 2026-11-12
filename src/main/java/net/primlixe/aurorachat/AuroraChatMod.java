package net.primlixe.aurorachat;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.primlixe.aurorachat.chat.Channels;
import net.primlixe.aurorachat.chat.ChatCore;
import net.primlixe.aurorachat.chat.ChatLog;
import net.primlixe.aurorachat.chat.ChatLogScreen;
import net.primlixe.aurorachat.chat.ChatToasts;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.gui.ChatSettingsScreen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AuroraChatMod implements ClientModInitializer {
	public static final String MOD_ID = "aurorachat";
	public static final Logger LOG = LoggerFactory.getLogger("Aurora Chat");

	private static KeyMapping keyHistory, keySettings, keyChannel, keyConsole, keyCancel, keyPunishLog, keyWheel;
	private static Screen pending;

	@Override
	public void onInitializeClient() {
		ChatConfig.load();
		migrateLogs();
		net.primlixe.aurorachat.chat.AutoJoin.load();
		net.primlixe.aurorachat.chat.InputHistory.load();
		net.primlixe.aurorachat.chat.PunishLog.load();

		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
			ChatCore.restore();
			net.primlixe.aurorachat.chat.UpdateCheck.start(); // только публичная сборка, в фоне
			net.primlixe.aurorachat.chat.InputHistory.importVanilla(net.primlixe.aurorachat.compat.Mc.chat().getRecentChat());
			if (!net.primlixe.aurorachat.Edition.PUBLIC) net.primlixe.aurorachat.chat.StaffWatch.scanLogs(); // персонал из старых журналов
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			ChatCore.save();
			net.primlixe.aurorachat.chat.InputHistory.save();
			net.primlixe.aurorachat.chat.PunishLog.save();
			ChatConfig.flushNow(); // ждёт, пока вся очередь записи Aurora Lib опустеет
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "toasts"), (g, delta) -> ChatToasts.render(g));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "send_fx"), (g, delta) -> net.primlixe.aurorachat.chat.SendFx.render(g));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "wheel_fx"), (g, delta) -> net.primlixe.aurorachat.chat.WheelEffects.render(g));
		ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> mc.execute(() -> {
			net.primlixe.aurorachat.chat.ConsoleLog.add(net.primlixe.aurorachat.chat.ConsoleLog.Level.NET, "§6Подключение: §f" + net.primlixe.aurorachat.chat.AutoJoin.currentServer());
			if (!net.primlixe.aurorachat.Edition.PUBLIC) {
				net.primlixe.aurorachat.chat.StaffWatch.reset();
				net.primlixe.aurorachat.chat.Moderation.reset();
			}
			ChatCore.sessionSeparator();
			net.primlixe.aurorachat.chat.UpdateCheck.onJoin();
			if (ChatConfig.get().autoJoin) net.primlixe.aurorachat.chat.AutoJoin.onJoin();
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(() -> {
			net.primlixe.aurorachat.chat.ConsoleLog.add(net.primlixe.aurorachat.chat.ConsoleLog.Level.NET, "§6Отключение от сервера");
			ChatCore.save();
		}));

		KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		keyHistory = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aurorachat.history", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, cat));
		keySettings = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aurorachat.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
		keyChannel = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aurorachat.channel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
		keyConsole = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aurorachat.console", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, cat));
		if (!net.primlixe.aurorachat.Edition.PUBLIC) { // модерация — только в личной сборке
			keyCancel = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aurorachat.cancel_punish", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DELETE, cat));
			keyPunishLog = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aurorachat.punish_log", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));
		}
		keyWheel = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aurorachat.player_wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, cat));

		// Круговое меню — в начале тика, до обработки клавиш игрой: если навёлся на игрока и меню открылось,
		// «съедаем» нажатие у других клавиш на той же кнопке (в 26.1 на G стоят ванильные «Быстрые действия»).
		// Не навёлся — нажатие достаётся им как обычно.
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			boolean pressed = false;
			while (keyWheel.consumeClick()) pressed = true;
			if (!pressed) return;
			List<KeyMapping> same = new java.util.ArrayList<>();
			for (KeyMapping k : mc.options.keyMappings) if (k != keyWheel && k.same(keyWheel)) same.add(k);
			if (net.primlixe.aurorachat.gui.PlayerWheelScreen.tryOpen(mc, keyWheel, !same.isEmpty())) {
				for (KeyMapping k : same) while (k.consumeClick()) { /* нажатие ушло в круговое меню */ }
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			long t0 = net.primlixe.aurorachat.ui.Perf.TICK.start();
			try { tickImpl(mc); } finally { net.primlixe.aurorachat.ui.Perf.TICK.stop(t0); }
		});

		registerCommands();
		LOG.info("Aurora Chat загружен");
	}

	private static void tickImpl(Minecraft mc) {
		{
			ChatConfig cfg = ChatConfig.get();
			ChatConfig.tick();
			ChatCore.warmMeta(); // история после перезапуска разбирается понемногу, а не в кадре открытия чата
			net.primlixe.aurorachat.chat.AutoGreet.tick();
			net.primlixe.aurorachat.chat.AfkResponder.tick();
			if (cfg.remindersEnabled) net.primlixe.aurorachat.chat.Reminders.tick();
			if (cfg.laterSend) net.primlixe.aurorachat.chat.LaterSend.tick();
			if (!net.primlixe.aurorachat.Edition.PUBLIC) {
				net.primlixe.aurorachat.chat.StaffWatch.tick();
				net.primlixe.aurorachat.chat.ChatGames.tick();
			}
			if (cfg.autoJoin) net.primlixe.aurorachat.chat.AutoJoin.tick();
			if (!net.primlixe.aurorachat.Edition.PUBLIC) net.primlixe.aurorachat.chat.Moderation.tick();
			net.primlixe.aurorachat.chat.WheelEffects.tick();
			while (keyConsole.consumeClick()) pending = new net.primlixe.aurorachat.gui.ConsoleScreen(null);
			while (keyPunishLog != null && keyPunishLog.consumeClick()) pending = new net.primlixe.aurorachat.gui.PunishLogScreen(null);
			while (keyCancel != null && keyCancel.consumeClick()) {
				int n = net.primlixe.aurorachat.chat.Moderation.cancelAll();
				if (n > 0) msg(net.primlixe.aurorachat.ui.I18n.t("§aОтменено автонаказаний: ") + n);
			}
			while (keyHistory.consumeClick()) pending = new ChatLogScreen(null, "");
			while (keySettings.consumeClick()) pending = new ChatSettingsScreen(null);
			while (keyChannel.consumeClick()) {
				Channels.toggle();
				msg(net.primlixe.aurorachat.ui.I18n.t("Канал: §f") + Channels.name(ChatConfig.get().channel.charAt(0)));
			}
			if (pending != null && net.primlixe.aurorachat.compat.Mc.screen() == null) {
				net.primlixe.aurorachat.compat.Mc.setScreen(pending);
				pending = null;
			}
		}
	}

	private static void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("achat")
				.executes(c -> { pending = new ChatSettingsScreen(null); return 1; })
				.then(ClientCommands.literal("history").executes(c -> { pending = new ChatLogScreen(null, ""); return 1; }))
				.then(ClientCommands.literal("g").executes(c -> { ChatConfig.get().channel = "G"; ChatConfig.save(); msg(net.primlixe.aurorachat.ui.I18n.t("Канал: §fглобальный")); return 1; }))
				.then(ClientCommands.literal("l").executes(c -> { ChatConfig.get().channel = "L"; ChatConfig.save(); msg(net.primlixe.aurorachat.ui.I18n.t("Канал: §fлокальный")); return 1; }))
				.then(ClientCommands.literal("reset").executes(c -> { ChatConfig.get().resetWindow(); ChatConfig.save(); msg(net.primlixe.aurorachat.ui.I18n.t("Окно чата вернулось на место")); return 1; }))
				.then(ClientCommands.literal("reload").executes(c -> { ChatConfig.load(); msg(net.primlixe.aurorachat.ui.I18n.t("Настройки перечитаны")); return 1; }))
				.then(ClientCommands.literal("quiz").requires(s -> !net.primlixe.aurorachat.Edition.PUBLIC)
						.executes(c -> {
							ChatConfig q = ChatConfig.get();
							msg(net.primlixe.aurorachat.ui.I18n.t("Викторины: автоответ ") + (q.chatGameAuto ? net.primlixe.aurorachat.ui.I18n.t("§aвкл") : net.primlixe.aurorachat.ui.I18n.t("§cвыкл")) + net.primlixe.aurorachat.ui.I18n.t("§7, задержка ") + (q.chatGameDelay < 0.05f ? net.primlixe.aurorachat.ui.I18n.t("нет (сразу)") : q.chatGameDelay + net.primlixe.aurorachat.ui.I18n.t(" с")) + net.primlixe.aurorachat.ui.I18n.t(", запомнено ответов: ") + net.primlixe.aurorachat.chat.ChatGames.learnedCount() + ". §f/achat quiz on|off");
							return 1;
						})
						.then(ClientCommands.literal("on").executes(c -> { ChatConfig.get().chatGameAuto = true; ChatConfig.get().chatGameDelay = 0f; ChatConfig.save(); msg(net.primlixe.aurorachat.ui.I18n.t("§aАвтоответ на викторины включён — отвечаю сразу §7(/achat quiz delay 2 — с задержкой)")); return 1; }))
						.then(ClientCommands.literal("delay").then(ClientCommands.argument("sec", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0, 30)).executes(c -> { float qd = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "sec"); ChatConfig.get().chatGameDelay = qd; ChatConfig.save(); msg(net.primlixe.aurorachat.ui.I18n.t("Задержка автоответа: §f") + (qd < 0.05f ? net.primlixe.aurorachat.ui.I18n.t("сразу") : qd + net.primlixe.aurorachat.ui.I18n.t(" с"))); return 1; })))
						.then(ClientCommands.literal("off").executes(c -> { ChatConfig.get().chatGameAuto = false; ChatConfig.save(); msg(net.primlixe.aurorachat.ui.I18n.t("Автоответ на викторины выключен — остаётся кнопка [= ответ]")); return 1; })))				.then(ClientCommands.literal("afk").executes(c -> {
					boolean on = net.primlixe.aurorachat.chat.AfkResponder.toggleManual();
					msg(on ? net.primlixe.aurorachat.ui.I18n.t("☾ АФК включён — на личные ответит автоответчик") : net.primlixe.aurorachat.ui.I18n.t("АФК выключен, с возвращением!"));
					return 1;
				}))
				.then(ClientCommands.literal("remind")
						.then(ClientCommands.literal("list").executes(c -> {
							var l = ChatConfig.get().reminders;
							if (l.isEmpty()) msg(net.primlixe.aurorachat.ui.I18n.t("Напоминаний нет"));
							for (int i = 0; i < l.size(); i++) msg("§e" + (i + 1) + ". §f" + net.primlixe.aurorachat.chat.Reminders.describe(l.get(i)));
							return 1;
						}))
						.then(ClientCommands.literal("clear").executes(c -> { ChatConfig.get().reminders.clear(); ChatConfig.save(); msg(net.primlixe.aurorachat.ui.I18n.t("Напоминания удалены")); return 1; }))
						.then(ClientCommands.argument("args", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c -> {
							String r = net.primlixe.aurorachat.chat.Reminders.add(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "args"), net.primlixe.aurorachat.ui.I18n.t("Напоминание"));
							msg(r == null ? net.primlixe.aurorachat.ui.I18n.t("§cНе понял время. Примеры: §f10m текст§c, §f1h30m текст§c, §f20:30 текст") : net.primlixe.aurorachat.ui.I18n.t("⏰ Напомню в ") + r);
							return 1;
						})))
				.then(ClientCommands.literal("timer").then(ClientCommands.argument("time", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c -> {
					String t = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "time").trim();
					String r = net.primlixe.aurorachat.chat.Reminders.add(t.split(" ")[0] + " Таймер " + t.split(" ")[0] + " вышел", "Таймер вышел");
					msg(r == null ? net.primlixe.aurorachat.ui.I18n.t("§cПример: §f/achat timer 5m") : net.primlixe.aurorachat.ui.I18n.t("⏱ Таймер: ") + r);
					return 1;
				})))
				.then(ClientCommands.literal("later")
						.then(ClientCommands.literal("list").executes(c -> {
							var l = ChatConfig.get().scheduled;
							if (l.isEmpty()) msg(net.primlixe.aurorachat.ui.I18n.t("Отложенных сообщений нет"));
							for (int i = 0; i < l.size(); i++) msg("§e" + (i + 1) + ". §f" + net.primlixe.aurorachat.chat.LaterSend.describe(l.get(i)));
							return 1;
						}))
						.then(ClientCommands.literal("clear").executes(c -> { ChatConfig.get().scheduled.clear(); ChatConfig.save(); msg(net.primlixe.aurorachat.ui.I18n.t("Отложенные отменены")); return 1; }))
						.then(ClientCommands.argument("args", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c -> {
							String r = net.primlixe.aurorachat.chat.LaterSend.add(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "args"));
							msg(r == null ? net.primlixe.aurorachat.ui.I18n.t("§cПример: §f/achat later 10m текст §cили §f/achat later 20:30 текст") : net.primlixe.aurorachat.ui.I18n.t("⏱ Отправлю ") + r);
							return 1;
						})))
				.then(ClientCommands.literal("nick")
						.then(ClientCommands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
								.suggests((c, b) -> {
									var con = Minecraft.getInstance().getConnection();
									if (con != null) for (var p : con.getOnlinePlayers()) b.suggest(p.getProfile().name());
									return b.buildFuture();
								})
								.executes(c -> {
									String p = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "player");
									net.primlixe.aurorachat.chat.Nicknames.set(p, null);
									msg(net.primlixe.aurorachat.ui.I18n.t("Имя для §f") + p + net.primlixe.aurorachat.ui.I18n.t("§r сброшено"));
									return 1;
								})
								.then(ClientCommands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c -> {
									String p = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "player");
									String n = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "name");
									net.primlixe.aurorachat.chat.Nicknames.set(p, n);
									msg("§f" + p + net.primlixe.aurorachat.ui.I18n.t("§r теперь у тебя в чате — §d") + n + net.primlixe.aurorachat.ui.I18n.t(" §7(видно только тебе)"));
									return 1;
								}))))
				.then(ClientCommands.literal("cancel").requires(s -> !net.primlixe.aurorachat.Edition.PUBLIC).executes(c -> {
					int n = net.primlixe.aurorachat.chat.Moderation.cancelAll();
					msg(n > 0 ? net.primlixe.aurorachat.ui.I18n.t("§aОтменено автонаказаний: ") + n : net.primlixe.aurorachat.ui.I18n.t("Нечего отменять — всё уже выдано или очередь пуста"));
					return 1;
				}))
				.then(ClientCommands.literal("punish").requires(s -> !net.primlixe.aurorachat.Edition.PUBLIC).executes(c -> { pending = new net.primlixe.aurorachat.gui.PunishLogScreen(null); return 1; }))
				.then(ClientCommands.literal("tr")
						.executes(c -> {
							ChatConfig t = ChatConfig.get();
							msg(net.primlixe.aurorachat.ui.I18n.t("Переводчик: чужое → §f") + net.primlixe.aurorachat.chat.Translator.target() + net.primlixe.aurorachat.ui.I18n.t("§7, своё → §f") + t.translateOut + net.primlixe.aurorachat.ui.I18n.t("§7, автоперевод ") + (t.translateAuto ? net.primlixe.aurorachat.ui.I18n.t("§aвкл") : net.primlixe.aurorachat.ui.I18n.t("§cвыкл"))
									+ net.primlixe.aurorachat.ui.I18n.t("§7. §f/achat tr <текст>§7 — перевести своё в строку ввода, §f/achat tr auto on|off"));
							return 1;
						})
						.then(ClientCommands.literal("auto")
								.then(ClientCommands.literal("on").executes(c -> { ChatConfig.get().translateAuto = true; ChatConfig.save(); msg(net.primlixe.aurorachat.ui.I18n.t("§aАвтоперевод чужого языка включён")); return 1; }))
								.then(ClientCommands.literal("off").executes(c -> { ChatConfig.get().translateAuto = false; ChatConfig.save(); msg(net.primlixe.aurorachat.ui.I18n.t("Автоперевод выключен")); return 1; })))
						.then(ClientCommands.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
								.executes(c -> {
									String text = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "text");
									msg(net.primlixe.aurorachat.ui.I18n.t("§7Перевожу…"));
									net.primlixe.aurorachat.chat.Translator.translateOutgoing(text, tr -> { if (tr != null) pending = new net.minecraft.client.gui.screens.ChatScreen(tr, false); });
									return 1;
								})))
				.then(ClientCommands.literal("grad")
						.executes(c -> {
							String cur = ChatConfig.get().writeGradient;
							msg(cur.isBlank() ? net.primlixe.aurorachat.ui.I18n.t("Пишу без градиента. §7/achat grad <название>") : net.primlixe.aurorachat.ui.I18n.t("Пишу градиентом: §f") + cur + net.primlixe.aurorachat.ui.I18n.t(" §7(/achat grad off — выключить)"));
							return 1;
						})
						.then(ClientCommands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
								.suggests((c, b) -> {
									b.suggest("off");
									for (var g : ChatConfig.get().gradients) b.suggest(g.name);
									return b.buildFuture();
								})
								.executes(c -> {
									String n = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "name").trim();
									ChatConfig cfg = ChatConfig.get();
									if (n.equalsIgnoreCase("off") || n.equalsIgnoreCase("выкл")) {
										cfg.writeGradient = "";
										msg(net.primlixe.aurorachat.ui.I18n.t("Градиент для письма выключен"));
									} else if (net.primlixe.aurorachat.chat.ColorCodes.colors(n) != null) {
										cfg.writeGradient = n;
										msg(net.primlixe.aurorachat.ui.I18n.t("Теперь пишу градиентом: ") + net.primlixe.aurorachat.chat.ColorCodes.preview("&{" + n + "}" + n).getString());
									} else msg(net.primlixe.aurorachat.ui.I18n.t("§cНет такого градиента. Список — в /achat → «Цвета и градиенты»"));
									ChatConfig.save();
									return 1;
								})))
				.then(ClientCommands.literal("console").executes(c -> { pending = new net.primlixe.aurorachat.gui.ConsoleScreen(null); return 1; }))
				.then(ClientCommands.literal("staff").requires(s -> !net.primlixe.aurorachat.Edition.PUBLIC)
						.then(ClientCommands.literal("list").executes(c -> {
							var l = ChatConfig.get().staff;
							if (l.isEmpty()) msg(net.primlixe.aurorachat.ui.I18n.t("Список персонала пуст: §f/achat staff add Ник Роль"));
							for (var s : l) msg("§f" + s.nick + " §7— " + s.role + (net.primlixe.aurorachat.chat.StaffWatch.online().contains(s.nick.toLowerCase(java.util.Locale.ROOT)) ? net.primlixe.aurorachat.ui.I18n.t(" §a● на сервере") : ""));
							return 1;
						}))
						.then(ClientCommands.literal("add").then(ClientCommands.argument("nick", com.mojang.brigadier.arguments.StringArgumentType.word())
								.executes(c -> staffAdd(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "nick"), "Модератор"))
								.then(ClientCommands.argument("role", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
										.executes(c -> staffAdd(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "nick"),
												com.mojang.brigadier.arguments.StringArgumentType.getString(c, "role"))))))
						.then(ClientCommands.literal("remove").then(ClientCommands.argument("nick", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
							String n = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "nick");
							boolean ok = ChatConfig.get().staff.removeIf(s -> s.nick.equalsIgnoreCase(n));
							ChatConfig.save();
							msg(ok ? net.primlixe.aurorachat.ui.I18n.t("Убран: ") + n : net.primlixe.aurorachat.ui.I18n.t("§cНет такого в списке"));
							return 1;
						}))))
				.then(ClientCommands.literal("pins").then(ClientCommands.literal("clear").executes(c -> { ChatConfig.get().pinned.clear(); ChatConfig.save(); msg(net.primlixe.aurorachat.ui.I18n.t("Закреплённые очищены")); return 1; })))));
	}

	private static int staffAdd(String nick, String role) {
		ChatConfig c = ChatConfig.get();
		c.staff.removeIf(s -> s.nick.equalsIgnoreCase(nick));
		ChatConfig.Staff s = new ChatConfig.Staff();
		s.nick = nick;
		s.role = role;
		String low = role.toLowerCase(java.util.Locale.ROOT);
		s.color = low.contains("влад") || low.contains("own") ? 0xFFB347 : low.contains("админ") ? 0xFF5470 : low.contains("хелп") ? 0x6CE38A : 0x6CB4FF;
		c.staff.add(s);
		ChatConfig.save();
		msg(net.primlixe.aurorachat.ui.I18n.t("⚑ Слежу за §f") + nick + " §7(" + role + net.primlixe.aurorachat.ui.I18n.t(") — предупрежу, когда появится"));
		return 1;
	}

	private static void msg(String s) {
		var p = Minecraft.getInstance().player;
		if (p != null) p.sendSystemMessage(Component.literal("§d[Aurora Chat] §r" + s));
	}

	/** Журналы из Aurora Cosmetics (config/aurora/chatlogs) переезжают в config/aurorachat/chatlogs. */
	private static void migrateLogs() {
		Path old = FabricLoader.getInstance().getConfigDir().resolve("aurora").resolve("chatlogs");
		Path now = ChatLog.root();
		if (!Files.isDirectory(old) || Files.exists(now)) return;
		try {
			Files.createDirectories(now.getParent());
			Files.move(old, now);
			LOG.info("Aurora Chat: журналы перенесены в {}", now);
		} catch (Exception e) {
			LOG.warn("Aurora Chat: не удалось перенести журналы: {}", e.toString());
		}
	}
}
