package net.primlixe.aurorachat.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.primlixe.aurorachat.AuroraChatMod;
import net.primlixe.aurorachat.chat.ChatCore;
import net.primlixe.aurorachat.config.ChatConfig;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Своя картинка на фоне окна чата: файл из config/aurorachat/backgrounds (png, jpg, bmp, gif).
 * Загружается один раз (большие уменьшаются до 1024 px — экономим видеопамять), перечитывается,
 * если файл поменяли. Рисуется со скруглёнными углами окна, режимы: заполнить, растянуть, плиткой.
 */
public final class BgImage {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(AuroraChatMod.MOD_ID, "chat_background");
	/** Точность UV: blit принимает размер области в целых «пикселях» — считаем в пикселях ×64. */
	private static final int K = 64;
	private static final int MAX = 1024;

	private static String loadedName;
	private static long loadedStamp;
	private static int texW, texH;
	private static boolean ready;
	private static long nextCheck;
	private static String error;

	public static Path dir() { return ChatCore.dir().resolve("backgrounds"); }

	/** Картинки в папке (по имени). */
	public static List<String> files() {
		List<String> out = new ArrayList<>();
		try {
			Files.createDirectories(dir());
			try (Stream<Path> s = Files.list(dir())) {
				s.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
						.filter(n -> n.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|bmp|gif)$")).sorted().forEach(out::add);
			}
		} catch (Exception ignored) { }
		return out;
	}

	/** Почему картинка не показалась (для настроек), null — всё хорошо. */
	public static String error() { return error; }

	/** Загрузить/перезагрузить текстуру, если сменили файл; false — картинки нет. */
	private static boolean ensure() {
		String name = ChatCore.cfg().bgImage;
		if (name == null || name.isBlank()) { unload(); return false; }
		long now = System.currentTimeMillis();
		if (name.equals(loadedName) && (ready || error != null) && now < nextCheck) return ready;
		nextCheck = now + 2000; // раз в 2 с смотрим, не заменили ли файл
		Path f = dir().resolve(name).normalize();
		if (!f.startsWith(dir().normalize()) || !Files.isRegularFile(f)) {
			unload();
			loadedName = name;
			error = "файл не найден в папке backgrounds";
			return false;
		}
		long stamp;
		try { stamp = Files.getLastModifiedTime(f).toMillis() ^ Files.size(f); } catch (Exception e) { stamp = 0; }
		if (name.equals(loadedName) && stamp == loadedStamp && (ready || error != null)) return ready;
		unload();
		loadedName = name;
		loadedStamp = stamp;
		try {
			BufferedImage src = javax.imageio.ImageIO.read(f.toFile());
			if (src == null) throw new IllegalArgumentException("формат не поддерживается");
			// большие картинки уменьшаем (сохраняя пропорции)
			double k = Math.min(1.0, MAX / (double) Math.max(src.getWidth(), src.getHeight()));
			int w = Math.max(1, (int) Math.round(src.getWidth() * k)), h = Math.max(1, (int) Math.round(src.getHeight() * k));
			BufferedImage img = src;
			if (k < 1.0) {
				img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				var gr = img.createGraphics();
				gr.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				gr.drawImage(src, 0, 0, w, h, null);
				gr.dispose();
			}
			NativeImage ni = new NativeImage(w, h, false);
			for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) ni.setPixel(x, y, img.getRGB(x, y));
			Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(() -> "Aurora Chat background", ni));
			texW = w;
			texH = h;
			ready = true;
			error = null;
		} catch (Throwable e) {
			ready = false;
			error = "не удалось открыть: " + e.getMessage();
			AuroraChatMod.LOG.warn("Aurora Chat: картинка фона {} — {}", name, e.toString());
		}
		return ready;
	}

	private static void unload() {
		if (ready) {
			try { Minecraft.getInstance().getTextureManager().release(ID); } catch (Throwable ignored) { }
		}
		ready = false;
		loadedName = null;
		error = null;
	}

	/** Перечитать файл сейчас (после выбора в настройках). */
	public static void reload() { loadedName = null; nextCheck = 0; }

	/**
	 * Картинка в прямоугольнике (0,0)–(w,h) текущей матрицы со скруглёнными углами r.
	 * alpha — общая прозрачность (уже с учётом настройки и открытости окна).
	 */
	public static void draw(GuiGraphicsExtractor g, int w, int h, int r, float alpha) {
		if (w <= 0 || h <= 0 || alpha <= 0.01f || !ensure()) return;
		ChatConfig c = ChatCore.cfg();
		int color = ((Math.round(Math.min(1, alpha) * 255) & 255) << 24) | 0xFFFFFF;
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		switch (c.bgImageMode) {
			case TILE -> {
				// плиткой: одна плитка — картинка в половину своего размера (на экране интерфейса это чётче)
				int tw = Math.max(8, Math.round(texW / 2f)), th = Math.max(8, Math.round(texH / 2f));
				for (int y = 0; y < h; y++) {
					if (y >= r && y < h - r) { // середина — сплошным куском
						tiles(g, 0, y, w, h - r, tw, th, color);
						y = h - r - 1;
						continue;
					}
					int in = inset(y, h, r);
					tiles(g, in, y, w - in, y + 1, tw, th, color);
				}
			}
			default -> {
				// заполнить: масштаб по большей стороне, лишнее по краям обрезается; растянуть — по обеим осям
				float sx = w / (float) texW, sy = h / (float) texH;
				if (c.bgImageMode == ChatConfig.ImageMode.COVER) sx = sy = Math.max(sx, sy);
				float u0 = (texW - w / sx) / 2f, v0 = (texH - h / sy) / 2f;
				for (int y = 0; y < h; y++) {
					int in = inset(y, h, r);
					if (y >= r && y < h - r) { // середина — одним куском
						int mh = h - 2 * r;
						slice(g, 0, y, w, mh, u0, v0 + y / sy, w / sx, mh / sy, color);
						y += mh - 1;
						continue;
					}
					slice(g, in, y, w - 2 * in, 1, u0 + in / sx, v0 + y / sy, (w - 2 * in) / sx, 1 / sy, color);
				}
			}
		}
	}

	/** Отступ строки y от края из-за скругления углов радиуса r. */
	private static int inset(int y, int h, int r) {
		if (r <= 0) return 0;
		float d;
		if (y < r) d = r - y - 0.5f;
		else if (y >= h - r) d = y - (h - r) + 0.5f;
		else return 0;
		return Math.round(r - (float) Math.sqrt(Math.max(0, r * r - d * d)));
	}

	/** Кусок картинки: экран (x,y,w,h) ← текстура (u,v, uw×vh в пикселях текстуры, дробно). */
	/** Прямоугольник (xa,ya)–(xb,yb), замощённый плитками tw×th от точки (0,0). */
	private static void tiles(GuiGraphicsExtractor g, int xa, int ya, int xb, int yb, int tw, int th, int color) {
		for (int cy = (ya / th) * th; cy < yb; cy += th) {
			for (int cx = (xa / tw) * tw; cx < xb; cx += tw) {
				int x0 = Math.max(xa, cx), x1 = Math.min(xb, cx + tw), y0 = Math.max(ya, cy), y1 = Math.min(yb, cy + th);
				if (x1 <= x0 || y1 <= y0) continue;
				float kx = texW / (float) tw, ky = texH / (float) th;
				slice(g, x0, y0, x1 - x0, y1 - y0, (x0 - cx) * kx, (y0 - cy) * ky, (x1 - x0) * kx, (y1 - y0) * ky, color);
			}
		}
	}

	private static void slice(GuiGraphicsExtractor g, int x, int y, int w, int h, float u, float v, float uw, float vh, int color) {
		if (w <= 0 || h <= 0) return;
		g.blit(RenderPipelines.GUI_TEXTURED, ID, x, y, u * K, v * K, w, h, Math.max(1, Math.round(uw * K)), Math.max(1, Math.round(vh * K)), texW * K, texH * K, color);
	}

	private BgImage() {}
}
