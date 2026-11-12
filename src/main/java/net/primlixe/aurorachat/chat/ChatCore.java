package net.primlixe.aurorachat.chat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.primlixe.aurorachat.AuroraChatMod;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.mixin.ChatComponentAccessor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ядро Aurora Chat: обработка входящих (время, ×N, упоминания, каналы, фильтры, игнор),
 * журнал, сохранение истории между запусками, разделители сессий.
 */
public final class ChatCore {
	private static final Gson GSON = new Gson();

	public record Result(Component component, boolean hidden, boolean replaceLast) {}

	private static String lastKey;
	private static int lastCount;
	private static Component lastShown;
	private static final Map<Component, Long> TIMES = new com.google.common.collect.MapMaker().weakKeys().makeMap();
	private static final Map<Component, ChatFormat.Meta> META = new com.google.common.collect.MapMaker().weakKeys().makeMap();
	private static boolean internal;
	private static List<Pattern> hide = List.of();
	private static String hideSrc = "";

	public static ChatConfig cfg() { return ChatConfig.get(); }

	public static boolean active() { return cfg().enabled; }

	public static boolean fancy() { return cfg().enabled && cfg().customHud; }

	/** Метаданные показанного сообщения (для восстановленных из истории считаются на лету). */
	public static ChatFormat.Meta meta(Component shown) {
		ChatFormat.Meta m = META.get(shown);
		if (m == null) {
			var mc = Minecraft.getInstance();
			String self = mc.player != null ? mc.player.getName().getString() : null;
			// срезаем только время «[12:30] » (ванильный режим), а не тег канала «[ɢ] » — иначе после перезапуска пропадали G/L
			String plain = stripTime(shown.getString());
			m = ChatFormat.classify(shown, plain, false, self, false);
			Long t = TIMES.get(shown); // восстановленное из истории — со своим временем
			if (t != null && t > 0) m = new ChatFormat.Meta(t, m.cat(), m.sender(), m.count(), m.channel());
			META.put(shown, m);
		}
		return m;
	}

	static void putMeta(Component c, ChatFormat.Meta m) { META.put(c, m); }

	private static int warmFrom;

	/**
	 * Каждый тик: заранее разобрать до 60 сообщений истории без метаданных (восстановленные после перезапуска).
	 * Иначе при первом открытии чата все 2000 разбирались в одном кадре — рывок до 100 мс.
	 */
	public static void warmMeta() {
		if (!cfg().enabled) return;
		var all = ((ChatComponentAccessor) net.primlixe.aurorachat.compat.Mc.chat()).aurorachat$all();
		int n = all.size();
		if (warmFrom >= n) { warmFrom = n; return; }
		int done = 0;
		for (int i = warmFrom; i < n && done < 60; i++) {
			Component c = all.get(i).content();
			if (!META.containsKey(c)) { meta(c); done++; }
			warmFrom = i + 1;
		}
	}

	/** Начать прогрев заново (история поменялась целиком). */
	public static void rewarm() { warmFrom = 0; }

	/** Убрать метку времени в начале строки: «[12:30] », «[12:30:05] », «[05.11 12:30] », «[03:15 PM] ». */
	public static String stripTime(String plain) {
		if (plain.isEmpty() || plain.charAt(0) != '[') return plain; // почти всегда — без регулярки
		return TIME_PREFIX.matcher(plain).replaceFirst("");
	}

	private static final Pattern TIME_PREFIX = Pattern.compile("^\\[(?:\\d{1,2}\\.\\d{1,2}\\s)?\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s?[AaPp][Mm]|\\s?[ДдПп][Пп])?\\]\\s");
	private static final Pattern SENDER_TIME = Pattern.compile("^\\[\\d{1,2}:\\d{2}(:\\d{2})?\\]\\s*");
	private static final Pattern DM_HEAD = Pattern.compile("^\\s*\\[[^\\]]*]\\s*");

	public static Result incoming(Component content, GuiMessageSource src, List<GuiMessage> all) {
		ChatConfig s = cfg();
		if (!s.enabled || internal) return new Result(content, false, false);
		String plain = content.getString();
		ConsoleLog.add(src == GuiMessageSource.PLAYER ? ConsoleLog.Level.CHAT : ConsoleLog.Level.SYS, content);
		if (!Moderation.notifying() && isHidden(plain)) { ConsoleLog.add(ConsoleLog.Level.HIDE, "§8(фильтр слов) §7" + plain); return new Result(content, true, false); }
		var mc = Minecraft.getInstance();
		String self = mc.player != null ? mc.player.getName().getString() : null;
		boolean mention = s.mentions && isMention(Channels.stripTag(plain));
		ChatFormat.Meta info = ChatFormat.classify(content, plain, src == GuiMessageSource.PLAYER, self, mention);
		if (info.sender() != null && !info.sender().equalsIgnoreCase(String.valueOf(self))) {
			for (String ig : s.ignored) if (ig.equalsIgnoreCase(info.sender())) { ConsoleLog.add(ConsoleLog.Level.HIDE, "§8(игнор " + ig + ") §7" + plain); return new Result(content, true, false); }
		}
		boolean joinLeave = info.cat() == ChatFormat.Category.JOIN || info.cat() == ChatFormat.Category.LEAVE;
		if (!net.primlixe.aurorachat.Edition.PUBLIC) try { StaffWatch.learn(ChatText.legacy(content), true); StaffWatch.fromChat(info); } catch (Exception ignored) { }
		if (joinLeave && s.hideJoins) { ConsoleLog.add(ConsoleLog.Level.HIDE, "§8(входы скрыты) §7" + plain); return new Result(content, true, false); }
		boolean fromOther = info.sender() != null && !info.sender().equalsIgnoreCase(String.valueOf(self));
		// модерация по правилам — до антиспама, чтобы видеть и повторы
		if (fromOther && !net.primlixe.aurorachat.Edition.PUBLIC) {
			if (!Moderation.notifying()) try { Moderation.check(info, plain); } catch (Exception e) { net.primlixe.aurorachat.AuroraChatMod.LOG.warn("Aurora Chat: модерация", e); }
		}
		// антиспам: повторы и флуд от одного игрока (подряд идущие повторы склеиваются в ×N ниже)
		if (fromOther && (info.cat() == ChatFormat.Category.PLAYER || info.cat() == ChatFormat.Category.MENTION)
				&& !plain.equals(lastKey) && AntiSpam.blocked(info.sender(), Channels.stripTag(plain))) {
			ConsoleLog.add(ConsoleLog.Level.HIDE, "§8(антиспам) §7" + plain);
			return new Result(content, true, false);
		}
		if (fromOther && info.cat() == ChatFormat.Category.DM) {
			try { AfkResponder.onDm(info.sender(), DM_HEAD.matcher(plain).replaceFirst("")); } catch (Exception ignored) { }
		}
		if (fromOther) content = AntiSpam.calmCaps(content);

		if (s.logging && (src == GuiMessageSource.PLAYER || s.logSystem)) ChatLog.write(type(src), ChatText.legacy(content));

		boolean stack = false;
		int count = 1;
		if (s.stacking && plain.equals(lastKey) && !all.isEmpty() && all.get(0).content() == lastShown) {
			stack = true;
			count = lastCount + 1;
		}
		long now = System.currentTimeMillis();
		boolean fancy = s.customHud;
		Component body = content;
		// тег канала заменяется значком
		if (fancy && info.channel() != 0 && s.channelBadges && s.stripChannelTag) body = ChatFormat.dropChars(body, Channels.tagLength(plain));
		if (joinLeave && s.compactJoins) body = ChatFormat.compactJoin(info, fancy);
		if (s.clickableLinks) body = ChatFormat.linkifyClick(body);
		boolean selfSender = info.sender() != null && info.sender().equalsIgnoreCase(String.valueOf(self));
		if (!joinLeave && info.sender() != null && (s.highlightSelf || !selfSender) && (s.colorNames || s.boldNames || s.nameItalic || s.nameUnderline || s.nameCaps)) body = ChatFormat.colorName(body, info.sender());
		body = Nicknames.apply(body); // свои имена для ников
		if (info.sender() == null && !net.primlixe.aurorachat.Edition.PUBLIC) try { body = ChatGames.onIncoming(body, plain); } catch (Exception e) { net.primlixe.aurorachat.AuroraChatMod.LOG.warn("Aurora Chat: викторина", e); }
		if (!net.primlixe.aurorachat.Edition.PUBLIC && s.staffBadge && info.sender() != null && !joinLeave) {
			ChatConfig.Staff st = StaffWatch.find(info.sender());
			if (st != null) body = Component.empty().append(Component.literal("★ ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(st.color)).withBold(true)
					.withHoverEvent(new HoverEvent.ShowText(Component.literal(st.role + " " + st.nick))))).append(body);
		}
		if (mention) {
			if (!stack && fromOther) AfkResponder.noteMention(info.sender());
			if (!s.highlightSelf) { /* свой ник не перекрашиваем */ }
			else if (fancy) body = ChatFormat.highlight(body, self, 0xFF000000 | s.mentionColor);
			else body = Component.empty().append(Component.literal("» ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(s.mentionColor)).withBold(true))).append(body);
			if (s.mentionSound && !stack) sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f);
			if (s.toasts && !stack) ChatToasts.push(info.sender() != null ? net.primlixe.aurorachat.ui.I18n.f("@ Упоминание от %s", Nicknames.display(info.sender())) : "@ Упоминание", Channels.stripTag(plain), info.sender(), 0xFFFFD166);
		}
		if (info.cat() == ChatFormat.Category.DM && !stack && info.sender() != null && !info.sender().equalsIgnoreCase(String.valueOf(self))) {
			if (s.dmSound) sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4f);
			if (s.toasts) ChatToasts.push(net.primlixe.aurorachat.ui.I18n.f("✉ Личное от %s", Nicknames.display(info.sender())), DM_HEAD.matcher(plain).replaceFirst(""), info.sender(), 0xFFFF7EC8);
		}
		Component out = body;
		if (!fancy) {
			if (s.timestamps) out = Component.empty().append(timestamp(now)).append(out);
			if (count > 1) out = Component.empty().append(out).append(Component.literal(" ×" + count).withStyle(ChatFormatting.DARK_GRAY));
		} else if (out == content) {
			out = content.copy(); // уникальный ключ для метаданных
		}
		ChatFormat.Meta meta = new ChatFormat.Meta(now, info.cat(), info.sender(), count, info.channel());
		META.put(out, meta);
		lastKey = plain;
		lastCount = count;
		lastShown = out;
		TIMES.put(out, now);
		if (!stack) {
			ChatHud.onIncoming(meta, plain);
			try { AutoGreet.onMessage(meta, plain); } catch (Exception ignored) { }
			try { Translator.maybeAuto(out, meta); } catch (Exception e) { AuroraChatMod.LOG.warn("Aurora Chat: автоперевод", e); }
		}
		return new Result(out, false, stack);
	}

	private static void sound(SoundEvent e, float pitch) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, 0.8f));
	}

	private static String type(GuiMessageSource src) {
		return switch (src) {
			case PLAYER -> "CHAT";
			case SYSTEM_SERVER -> "SYS";
			case SYSTEM_CLIENT -> "CLIENT";
		};
	}

	public static DateTimeFormatter timeFormatter() {
		return net.primlixe.auroralib.text.TimeFmt.of(cfg().timeFormat); // шаблон разбирается один раз, а не каждый кадр
	}

	public static Component timestamp(long epochMs) {
		ChatConfig s = cfg();
		LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
		Style st = Style.EMPTY.withColor(TextColor.fromRgb(s.timestampColor));
		if (s.timestampHoverDate) {
			st = st.withHoverEvent(new HoverEvent.ShowText(Component.literal(t.format(DateTimeFormatter.ofPattern("dd.MM.yyyy  HH:mm:ss")))));
		}
		return Component.literal("[" + t.format(timeFormatter()) + "] ").withStyle(st);
	}

	public static Long timeOf(Component shown) { return TIMES.get(shown); }

	private static boolean isHidden(String plain) {
		List<String> src = cfg().hidePatterns;
		if (src.isEmpty() && hide.isEmpty()) return false;
		String key = src.size() + ":" + src.hashCode(); // без склейки всех шаблонов на каждое сообщение
		if (!key.equals(hideSrc)) {
			List<Pattern> ps = new ArrayList<>();
			for (String p : src) {
				try { ps.add(Pattern.compile(p, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)); } catch (Exception ignored) { }
			}
			hide = ps;
			hideSrc = key;
		}
		for (Pattern p : hide) if (p.matcher(plain).find()) return true;
		return false;
	}

	private static boolean isMention(String plain) {
		var mc = Minecraft.getInstance();
		List<String> words = new ArrayList<>(cfg().mentionWords);
		String self = mc.player != null ? mc.player.getName().getString() : null;
		if (self != null) {
			if (plain.startsWith("<" + self + ">") || net.primlixe.auroralib.text.Regex.of("(?s)^\\S*\\s*" + Pattern.quote(self) + "\\s*[:»>].*").matcher(plain).matches()) return false;
			if (self.equalsIgnoreCase(String.valueOf(senderOf(plain)))) return false; // своё сообщение — не упоминание
			words.add(self);
		}
		String low = plain.toLowerCase(Locale.ROOT);
		for (String w : words) {
			if (w == null || w.isBlank()) continue;
			String lw = w.toLowerCase(Locale.ROOT);
			int i = low.indexOf(lw);
			while (i >= 0) {
				boolean left = i == 0 || !Character.isLetterOrDigit(low.charAt(i - 1));
				int end = i + lw.length();
				boolean right = end >= low.length() || !Character.isLetterOrDigit(low.charAt(end));
				if (left && right) return true;
				i = low.indexOf(lw, i + 1);
			}
		}
		return false;
	}

	/** Отправитель (эвристика по форматам «<Ник> текст», «Ник: текст», «[Ранг] Ник » текст»). */
	public static String senderOf(String plain) {
		String p = plain.startsWith("[") ? SENDER_TIME.matcher(plain).replaceFirst("") : plain;
		if (p.startsWith("» ")) p = p.substring(2);
		// клановый чат: «КЛАН: [ИИ-бот] Ник: текст»
		String cp = cfg().clanPrefix;
		if (cp != null && !cp.isBlank() && p.regionMatches(true, 0, cp, 0, cp.length())) p = p.substring(cp.length()).trim();
		// Mineblaze и похожие: «[Ранг] Клан ~~Ник✪ → текст» — ник последнее латинское слово перед стрелкой
		int arrow = p.indexOf('→');
		if (arrow > 0 && arrow < 120) {
			// ник стоит прямо перед стрелкой: допускаются значки (✪ ~), одно слово-суффикс («emannysss бубубу») и (скобки)
			String head = p.substring(0, arrow).replaceAll("\\[[^\\]]*]|\\([^)]*\\)", " ");
			var a = net.primlixe.auroralib.text.Regex.of("(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16})[^\\p{L}\\p{N}_]{0,6}(?:\\p{IsCyrillic}{1,12}[^\\p{L}\\p{N}_]{0,4})?$").matcher(head);
			if (a.find()) return a.group(1);
		}
		var m = net.primlixe.auroralib.text.Regex.of("^<([A-Za-z0-9_]{2,16})>").matcher(p);
		if (m.find()) return m.group(1);
		m = net.primlixe.auroralib.text.Regex.of("^(?:\\[[^\\]]{1,24}\\]\\s*)*([A-Za-z0-9_]{3,16})\\s*[:»>|]").matcher(p);
		if (m.find()) return m.group(1);
		return null;
	}

	// ------------------------------------------------------------ служебные сообщения

	public static void sessionSeparator() {
		if (!cfg().enabled || !cfg().sessionSeparator) return;
		var mc = Minecraft.getInstance();
		String server = mc.getSingleplayerServer() != null ? mc.getSingleplayerServer().getWorldData().getLevelName()
				: mc.getCurrentServer() != null ? mc.getCurrentServer().name : "?";
		String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
		Component line = Component.literal("━━━━━ ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal("✦ " + server).withStyle(ChatFormatting.LIGHT_PURPLE))
				.append(Component.literal(" · " + when + " ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("━━━━━").withStyle(ChatFormatting.DARK_GRAY));
		internal = true;
		try {
			net.primlixe.aurorachat.compat.Mc.chat().addClientSystemMessage(line);
		} finally {
			internal = false;
		}
		if (cfg().logging) ChatLog.write("SESSION", "§d" + server + " §7" + when);
	}

	// ------------------------------------------------------------ история между запусками

	public static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("aurorachat"); }

	private static Path historyFile() { return dir().resolve("chat_history.json"); }

	/** times — время каждого сообщения (мс), в том же порядке; в старых файлах его нет (null). */
	private record Saved(List<String> messages, List<String> sent, List<Long> times) {}

	public static void save() {
		ChatConfig s = cfg();
		if (!s.enabled || !s.persistHistory) return;
		try {
			var chat = net.primlixe.aurorachat.compat.Mc.chat();
			ChatComponentAccessor a = (ChatComponentAccessor) chat;
			List<String> msgs = new ArrayList<>();
			List<Long> times = new ArrayList<>();
			for (GuiMessage m : a.aurorachat$all()) {
				if (msgs.size() >= s.persistLimit) break;
				msgs.add(ChatText.json(m.content()));
				Long t = TIMES.get(m.content());
				if (t == null) { ChatFormat.Meta mm = META.get(m.content()); t = mm != null ? mm.time() : 0L; }
				times.add(t);
			}
			List<String> sent = new ArrayList<>(chat.getRecentChat());
			// снимок собран в игровом потоке, на диск — в фоне (атомарно, без фриза при выходе с сервера)
			net.primlixe.auroralib.io.AsyncFiles.write(historyFile(), GSON.toJson(new Saved(msgs, sent, times)));
		} catch (Exception e) {
			AuroraChatMod.LOG.warn("Aurora Chat: не удалось сохранить историю: {}", e.toString());
		}
	}

	private static boolean restored;

	public static void restore() {
		ChatConfig s = cfg();
		if (restored || !s.enabled || !s.persistHistory) return;
		restored = true;
		Path f = historyFile();
		if (!Files.exists(f)) {
			Path old = FabricLoader.getInstance().getConfigDir().resolve("aurora").resolve("chat_history.json");
			if (!Files.exists(old)) return;
			f = old;
		}
		try {
			Saved saved = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), new TypeToken<Saved>() {}.getType());
			if (saved == null) return;
			var chat = net.primlixe.aurorachat.compat.Mc.chat();
			ChatComponentAccessor a = (ChatComponentAccessor) chat;
			if (saved.messages() != null) {
				List<Long> times = saved.times();
				for (int i = 0; i < saved.messages().size(); i++) {
					Component c = ChatText.fromJson(saved.messages().get(i));
					Long t = times != null && i < times.size() ? times.get(i) : null;
					if (t != null && t > 0) TIMES.put(c, t); // время у сообщений сохраняется между запусками
					a.aurorachat$all().add(new GuiMessage(0, c, null, GuiMessageSource.SYSTEM_CLIENT, null));
				}
			}
			if (saved.sent() != null) for (String m : saved.sent()) chat.addRecentChat(m);
			a.aurorachat$refresh();
			rewarm();
			AuroraChatMod.LOG.info("Aurora Chat: восстановлено {} сообщений", saved.messages() == null ? 0 : saved.messages().size());
		} catch (Exception e) {
			AuroraChatMod.LOG.warn("Aurora Chat: не удалось восстановить историю: {}", e.toString());
		}
	}

	// ------------------------------------------------------------ геометрия (ванильный режим)

	public static GuiMessage messageAt(double mx, double my) {
		if (ChatHud.enabled()) return ChatHud.messageAt(mx, my);
		var mc = Minecraft.getInstance();
		ChatComponentAccessor a = (ChatComponentAccessor) net.primlixe.aurorachat.compat.Mc.chat();
		double scale = a.aurorachat$scale();
		int lh = Math.max(1, a.aurorachat$lineHeight());
		int guiH = mc.getWindow().getGuiScaledHeight();
		double cx = mx / scale;
		double bottom = Math.floor((guiH - 40) / scale);
		double cy = (bottom - my / scale) / lh;
		if (cy < 0 || cx < 0 || cx > a.aurorachat$width() / scale + 12) return null;
		int idx = (int) Math.floor(cy) + a.aurorachat$scroll();
		var lines = a.aurorachat$trimmed();
		if (idx < 0 || idx >= lines.size()) return null;
		return lines.get(idx).parent();
	}

	// ------------------------------------------------------------ закреплённые

	public static void pin(GuiMessage m) {
		ChatConfig c = cfg();
		ChatConfig.Pin p = new ChatConfig.Pin();
		p.json = ChatText.json(m.content());
		Long t = timeOf(m.content());
		p.time = t == null ? System.currentTimeMillis() : t;
		p.sender = meta(m.content()).sender();
		c.pinned.add(0, p);
		while (c.pinned.size() > 10) c.pinned.remove(c.pinned.size() - 1);
		ChatConfig.save();
	}

	public static void unpin(int i) {
		var l = cfg().pinned;
		if (i >= 0 && i < l.size()) { l.remove(i); ChatConfig.save(); }
	}

	public static void delete(GuiMessage m) {
		ChatComponentAccessor a = (ChatComponentAccessor) net.primlixe.aurorachat.compat.Mc.chat();
		List<GuiMessage> all = a.aurorachat$all();
		for (int i = 0; i < all.size(); i++) if (all.get(i) == m) { all.remove(i); break; } // по ссылке: equals у record сравнивает поля
		// раньше — refreshTrimmedMessages (перенос всей истории, 20–45 мс фриз); теперь убираем только строки этого сообщения
		a.aurorachat$trimmed().removeIf(l -> l.parent() == m);
	}

	/** Время и метаданные старого содержимого — новому (сообщение подменили на месте, например перевод). */
	static void carry(Component from, Component to) {
		Long t = TIMES.get(from);
		if (t != null) TIMES.put(to, t);
		ChatFormat.Meta m = META.get(from);
		if (m != null) META.put(to, m);
		if (lastShown == from) lastShown = to;
	}

	/**
	 * Строки ванильного чата после подмены сообщения на месте. Свой HUD берёт раскладку из allMessages сам;
	 * ванильный (или запасной после ошибки) — заново разбивает только это сообщение, если оно целиком последнее,
	 * иначе — полная пересборка (бывает редко: ручной перевод в ванильном режиме).
	 */
	static void relayout(GuiMessage old, GuiMessage now, int index) {
		ChatComponentAccessor a = (ChatComponentAccessor) net.primlixe.aurorachat.compat.Mc.chat();
		if (ChatHud.enabled()) {
			// строки ванильного списка для этого сообщения устарели — пометим подменой parent не получится (record), просто уберём;
			// при выключении своего HUD история пересоберётся (ChatHud.disableAfterError / переключение зовут refresh)
			a.aurorachat$trimmed().removeIf(l -> l.parent() == old);
			vanillaStale = true;
			return;
		}
		a.aurorachat$refresh();
	}

	/** Строки ванильного чата не совпадают с историей (подменяли сообщения при своём HUD). */
	public static boolean vanillaStale;

	/** Пересобрать строки ванильного чата, если они устарели. */
	public static void refreshVanillaIfStale() {
		if (!vanillaStale) return;
		vanillaStale = false;
		((ChatComponentAccessor) net.primlixe.aurorachat.compat.Mc.chat()).aurorachat$refresh();
	}

	private ChatCore() {}
}
