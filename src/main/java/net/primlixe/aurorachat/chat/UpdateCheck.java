package net.primlixe.aurorachat.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.primlixe.aurorachat.AuroraChatMod;
import net.primlixe.aurorachat.ui.I18n;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Публичная сборка: раз за запуск спрашивает Modrinth, нет ли версии новее (в фоне), и при первом входе в мир
 * пишет в чат строку со ссылкой. Ничего не скачивает и не ставит сам. В запрос уходят только название мода и версия игры.
 */
public final class UpdateCheck {
	public static final String SLUG = "aurora-ryizen";
	private static volatile String newer, newerUrl;
	private static boolean shown, started;

	public static void start() {
		if (started || !net.primlixe.aurorachat.Edition.PUBLIC || !ChatCore.cfg().updateCheck) return;
		started = true;
		String mine = current();
		String game = gameVersion();
		String url = "https://api.modrinth.com/v2/project/" + SLUG + "/version?loaders=" + enc("[\"fabric\"]") + "&game_versions=" + enc("[\"" + game + "\"]");
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
				.header("User-Agent", "PrimLixe/AuroraChat/" + mine + " (modrinth.com/mod/" + SLUG + ")").GET().build();
		HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
				.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.thenAccept(r -> {
					if (r.statusCode() != 200) return; // проекта ещё нет / нет сети — молча
					String[] latest = latest(r.body());
					if (latest != null && isNewer(latest[0], mine)) { newer = latest[0]; newerUrl = "https://modrinth.com/mod/" + SLUG + "/version/" + latest[1]; }
				})
				.exceptionally(e -> { AuroraChatMod.LOG.debug("Aurora Chat: проверка обновлений: {}", e.toString()); return null; });
	}

	/** Первая (самая новая) версия из ответа Modrinth: {номер, id}. Только release. */
	public static String[] latest(String body) {
		JsonElement root = JsonParser.parseString(body);
		if (!root.isJsonArray()) return null;
		JsonArray arr = root.getAsJsonArray();
		for (JsonElement e : arr) {
			var o = e.getAsJsonObject();
			if (o.has("version_type") && !"release".equals(o.get("version_type").getAsString())) continue;
			return new String[]{o.get("version_number").getAsString(), o.get("id").getAsString()};
		}
		return null;
	}

	/** «2.26.0+mc26.1.2» новее «2.25.0+mc26.1.2»? Сравниваются числа до «+» / «-». */
	public static boolean isNewer(String remote, String mine) {
		int[] a = nums(remote), b = nums(mine);
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
			if (x != y) return x > y;
		}
		return false;
	}

	private static int[] nums(String v) {
		String core = v.split("[+\\-]", 2)[0];
		String[] p = core.split("\\.");
		int[] out = new int[p.length];
		for (int i = 0; i < p.length; i++) {
			try { out[i] = Integer.parseInt(p[i].replaceAll("\\D", "")); } catch (NumberFormatException e) { out[i] = 0; }
		}
		return out;
	}

	/** При входе в мир — одна строка в чат, если нашлась версия новее. */
	public static void onJoin() {
		if (shown || newer == null) return;
		shown = true;
		Component link = Component.literal(I18n.t("[скачать]")).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x6CB4FF)).withUnderlined(true)
				.withClickEvent(new ClickEvent.OpenUrl(URI.create(newerUrl)))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(newerUrl))));
		Component line = Component.literal("§b✦ Aurora Chat §f" + newer + " §7" + I18n.t("— вышла новая версия") + " ").append(link);
		Minecraft.getInstance().execute(() -> net.primlixe.aurorachat.compat.Mc.chat().addClientSystemMessage(line));
	}

	private static String current() {
		return FabricLoader.getInstance().getModContainer(AuroraChatMod.MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0");
	}

	private static String gameVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
	}

	private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

	private UpdateCheck() {}
}
