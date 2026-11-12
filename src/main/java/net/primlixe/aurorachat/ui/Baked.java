package net.primlixe.aurorachat.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.primlixe.aurorachat.AuroraChatMod;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntBinaryOperator;

/**
 * «Запечённые» картинки интерфейса: спектры палитр считаются ОДИН раз в маленькую текстуру,
 * а потом рисуются одной командой. Раньше спектр рисовался тысячами квадратиков с пересчётом
 * цвета каждый кадр — в настройках и палитре «&» это сильно роняло FPS.
 */
public final class Baked {
	private record Tex(Identifier id, int w, int h) {}

	private static final Map<String, Tex> TEX = new HashMap<>();

	private static Tex get(String key, int w, int h, IntBinaryOperator rgb) {
		return TEX.computeIfAbsent(key, k -> {
			NativeImage ni = new NativeImage(w, h, false);
			for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) ni.setPixel(x, y, 0xFF000000 | rgb.applyAsInt(x, y));
			Identifier id = Identifier.fromNamespaceAndPath(AuroraChatMod.MOD_ID, "baked/" + k);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Aurora Chat " + k, ni));
			return new Tex(id, w, h);
		});
	}

	/** Нарисовать картинку key (w×h, цвет пикселя — rgb(x, y)) растянутой на прямоугольник экрана. */
	public static void draw(GuiGraphicsExtractor g, String key, int w, int h, IntBinaryOperator rgb, int x0, int y0, int x1, int y1) {
		if (x1 <= x0 || y1 <= y0) return;
		Tex t = get(key, w, h, rgb);
		g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x0, y0, 0, 0, x1 - x0, y1 - y0, t.w(), t.h(), t.w(), t.h(), 0xFFFFFFFF);
	}

	private Baked() {}
}
