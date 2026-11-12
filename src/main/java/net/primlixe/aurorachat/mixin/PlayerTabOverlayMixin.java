package net.primlixe.aurorachat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.ui.Perf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Список игроков (Tab) плавно выезжает сверху. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	@Unique private static long aurorachat$lastFrame, aurorachat$shownAt;

	@WrapMethod(method = "extractRenderState")
	private void aurorachat$slide(GuiGraphicsExtractor g, int width, Scoreboard scoreboard, Objective objective, Operation<Void> original) {
		long now = System.nanoTime();
		if (now - aurorachat$lastFrame > 150_000_000L) aurorachat$shownAt = now; // список только что открыли
		aurorachat$lastFrame = now;
		float e = ChatConfig.get().mcTabAnim ? Perf.appear(aurorachat$shownAt, 0.18f) : 1;
		if (e >= 1) {
			original.call(g, width, scoreboard, objective);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(0, -(1 - e) * 14);
		try {
			original.call(g, width, scoreboard, objective);
		} finally {
			g.pose().popMatrix();
		}
	}
}
