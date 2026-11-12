package net.primlixe.aurorachat.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.config.ChatConfig.WheelFx;
import net.primlixe.aurorachat.ui.UI;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Спецэффекты кругового меню: вспышка, волна из центра, частицы-значки разлетаются от выбранной кнопки,
 * надпись «Обнять → Ник» всплывает и тает; в мире у игрока — частицы (видишь только ты); звук.
 */
public final class WheelEffects {
	private static final Random R = new Random();
	private static final List<P> PARTS = new ArrayList<>();
	private static long born;
	private static float cx, cy;
	private static String label = "";
	private static int color = 0xFFFFFFFF;
	private static ChatConfig.WheelFx kind = ChatConfig.WheelFx.NONE;
	// частицы в мире: сколько тиков ещё сыпать и у кого
	private static int worldTicks, entityId = -1;
	// звук: аккорд из нескольких нот подряд
	private static int soundStep;
	private static long soundAt;

	private static final class P {
		float x, y, vx, vy, rot, vr, size, life, age;
		int color;
		String glyph;
	}

	/** Применили действие: bx/by — где была кнопка, scx/scy — центр экрана. */
	public static void play(WheelFx fx, String name, String nick, float bx, float by, float scx, float scy, Entity target) {
		ChatConfig c = ChatConfig.get();
		kind = fx;
		cx = scx;
		cy = scy;
		label = name + " → " + Nicknames.display(nick);
		color = 0xFF000000 | palette(fx)[0];
		born = System.nanoTime();
		PARTS.clear();
		if (c.wheelFx && fx != WheelFx.NONE) {
			int n = fx == WheelFx.CONFETTI ? 44 : 28;
			for (int i = 0; i < n; i++) {
				P p = new P();
				p.x = bx + (R.nextFloat() - 0.5f) * 10;
				p.y = by + (R.nextFloat() - 0.5f) * 6;
				// разлёт веером от центра через кнопку
				double base = Math.atan2(by - scy, bx - scx);
				double a = base + (R.nextGaussian() * 0.9);
				float sp = 60 + R.nextFloat() * 110;
				p.vx = (float) Math.cos(a) * sp;
				p.vy = (float) Math.sin(a) * sp - 40;
				p.rot = R.nextFloat() * 360;
				p.vr = (R.nextFloat() - 0.5f) * 400;
				p.size = 0.8f + R.nextFloat() * 0.9f;
				p.life = 0.8f + R.nextFloat() * 0.6f;
				int[] pal = palette(fx);
				p.color = 0xFF000000 | pal[R.nextInt(pal.length)];
				p.glyph = glyph(fx);
				PARTS.add(p);
			}
		}
		entityId = target != null ? target.getId() : -1;
		worldTicks = c.wheelFxWorld && fx != WheelFx.NONE ? 14 : 0;
		soundStep = c.wheelFxSound && fx != WheelFx.NONE ? 0 : 99;
		soundAt = System.nanoTime();
	}

	private static int[] palette(WheelFx fx) {
		return switch (fx) {
			case HEARTS -> new int[]{0xFF5C8A, 0xFF8FB1, 0xFF3D6E, 0xFFC2D6};
			case SPARKS -> new int[]{0xFFE27A, 0xFFFFFF, 0xFFC94A, 0xB8F1FF};
			case STARS -> new int[]{0xFFD54A, 0xFFF1A8, 0xFFB020};
			case NOTES -> new int[]{0x7FD8FF, 0xB18CFF, 0x5BE39A, 0xFF8FB1};
			case CONFETTI -> new int[]{0xFF5C8A, 0x5BE39A, 0x6CB4FF, 0xFFD54A, 0xB18CFF, 0xFF9A4A};
			case NONE -> new int[]{0xFFFFFF};
		};
	}

	private static String glyph(WheelFx fx) {
		return switch (fx) {
			case HEARTS -> "❤";
			case SPARKS -> R.nextBoolean() ? "✦" : "✧";
			case STARS -> R.nextInt(3) == 0 ? "✩" : "★";
			case NOTES -> R.nextBoolean() ? "♪" : "♫";
			case CONFETTI -> "■";
			case NONE -> "";
		};
	}

	/** Каждый тик: частицы у игрока в мире и аккорд. */
	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (worldTicks > 0 && mc.level != null) {
			worldTicks--;
			Entity e = entityId >= 0 ? mc.level.getEntity(entityId) : null;
			if (e != null) {
				int count = worldTicks > 10 ? 3 : 1;
				for (int i = 0; i < count; i++) {
					double x = e.getX() + (R.nextDouble() - 0.5) * e.getBbWidth() * 1.6;
					double y = e.getY() + e.getBbHeight() * (0.6 + R.nextDouble() * 0.6);
					double z = e.getZ() + (R.nextDouble() - 0.5) * e.getBbWidth() * 1.6;
					ParticleOptions p = switch (kind) {
						case HEARTS -> ParticleTypes.HEART;
						case SPARKS -> ParticleTypes.ELECTRIC_SPARK;
						case STARS -> ParticleTypes.END_ROD;
						case NOTES -> ParticleTypes.NOTE;
						case CONFETTI -> R.nextBoolean() ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.TOTEM_OF_UNDYING;
						case NONE -> null;
					};
					if (p == null) break;
					double vx = kind == WheelFx.NOTES ? R.nextDouble() : (R.nextDouble() - 0.5) * 0.08;
					mc.level.addParticle(p, x, y, z, vx, kind == WheelFx.NOTES ? 0 : 0.05 + R.nextDouble() * 0.06, kind == WheelFx.NOTES ? 0 : (R.nextDouble() - 0.5) * 0.08);
				}
			}
		}
		if (soundStep < 3 && System.nanoTime() >= soundAt) {
			float[] pitch = switch (kind) {
				case HEARTS -> new float[]{1.06f, 1.33f, 1.6f};
				case SPARKS -> new float[]{1.5f, 1.78f, 2f};
				case STARS -> new float[]{1.0f, 1.26f, 1.5f};
				case NOTES -> new float[]{0.9f, 1.2f, 1.5f};
				default -> new float[]{1.2f, 1.5f, 1.8f};
			};
			SoundEvent ev = switch (kind) {
				case HEARTS -> SoundEvents.NOTE_BLOCK_CHIME.value();
				case SPARKS -> SoundEvents.NOTE_BLOCK_PLING.value();
				case STARS -> SoundEvents.NOTE_BLOCK_BELL.value();
				case NOTES -> SoundEvents.NOTE_BLOCK_HARP.value();
				default -> SoundEvents.NOTE_BLOCK_FLUTE.value();
			};
			mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch[soundStep], 0.55f));
			soundStep++;
			soundAt = System.nanoTime() + 70_000_000L;
		}
	}

	private static long lastFrame;

	public static void render(GuiGraphicsExtractor g) {
		if (born == 0) return;
		float t = (System.nanoTime() - born) / 1e9f;
		if (t > 1.6f && PARTS.isEmpty()) { born = 0; return; }
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		boolean screenFx = ChatConfig.get().wheelFx && kind != WheelFx.NONE;

		if (screenFx) {
			// вспышка цветом эффекта
			if (t < 0.3f) g.fill(0, 0, g.guiWidth(), g.guiHeight(), UI.alpha(color, 0.16f * (1 - t / 0.3f)));
			// две волны-кольца из центра
			for (int k = 0; k < 2; k++) {
				float tk = (t - k * 0.09f) / 0.55f;
				if (tk <= 0 || tk >= 1) continue;
				float e = UI.ease(tk);
				float rad = 18 + e * 120;
				int dots = 72;
				int col = UI.alpha(color, (1 - tk) * 0.8f);
				for (int i = 0; i < dots; i++) {
					double a = i * Math.PI * 2 / dots;
					int x = Math.round(cx + (float) Math.cos(a) * rad), y = Math.round(cy + (float) Math.sin(a) * rad);
					g.fill(x, y, x + 2, y + 2, col);
				}
			}
		}

		// частицы-значки
		for (Iterator<P> it = PARTS.iterator(); it.hasNext(); ) {
			P p = it.next();
			p.age += dt;
			if (p.age >= p.life) { it.remove(); continue; }
			p.vy += 260 * dt;           // гравитация
			p.vx *= (float) Math.exp(-2.2 * dt);
			p.x += p.vx * dt;
			p.y += p.vy * dt;
			p.rot += p.vr * dt;
			float lt = p.age / p.life;
			float a = lt < 0.7f ? 1 : 1 - (lt - 0.7f) / 0.3f;
			float pop = Math.min(1, p.age / 0.08f);
			g.pose().pushMatrix();
			g.pose().translate(p.x, p.y);
			g.pose().rotate((float) Math.toRadians(p.rot * (kind == WheelFx.CONFETTI ? 1 : 0.15f)));
			float s = p.size * pop;
			g.pose().scale(s, s);
			if (kind == WheelFx.CONFETTI) g.fill(-2, -1, 2, 2, UI.alpha(p.color, a));
			else g.text(font, p.glyph, -font.width(p.glyph) / 2, -4, UI.alpha(p.color, a), false);
			g.pose().popMatrix();
		}

		// надпись: выпрыгивает, чуть всплывает и тает
		if (t < 1.4f) {
			float in = UI.ease(Math.min(1, t / 0.18f));
			float out = t > 0.9f ? (t - 0.9f) / 0.5f : 0;
			float a = in * (1 - out);
			float sc = 0.7f + 0.5f * in - 0.1f * out;
			int w = font.width(label);
			g.pose().pushMatrix();
			g.pose().translate(cx, cy - 10 - t * 14);
			g.pose().scale(sc * 1.4f, sc * 1.4f);
			UI.round(g, -w / 2 - 6, -6, w / 2 + 6, 8, 6, UI.rgba(0x0B0C14, 0.7f * a));
			g.text(font, label, -w / 2, -3, UI.alpha(UI.mix(0xFFFFFFFF, color, 0.25f), a), true);
			g.pose().popMatrix();
		}
	}

	private WheelEffects() {}
}
