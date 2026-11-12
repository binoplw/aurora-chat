package net.primlixe.aurorachat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.ui.UI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Рамка выбранного слота хотбара плавно переезжает (с дробным сдвигом — без «ступенек»). */
@Mixin(Hud.class)
public abstract class GuiMixin {
	@Unique private static float aurorachat$selX = Float.NaN;
	@Unique private static long aurorachat$last;

	@WrapOperation(method = "extractItemHotbar", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
			ordinal = 1))
	private void aurorachat$smoothSelection(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
		ChatConfig c = ChatConfig.get();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - aurorachat$last) / 1e9f);
		aurorachat$last = now;
		if (!c.mcSmoothHotbar || net.primlixe.aurorachat.compat.Compat.hotbarByOtherMod() || Float.isNaN(aurorachat$selX) || Math.abs(x - aurorachat$selX) > 400) {
			aurorachat$selX = x;
			original.call(g, pipeline, sprite, x, y, w, h);
			return;
		}
		aurorachat$selX = UI.damp(aurorachat$selX, x, 22 * c.mcHotbarSpeed, dt);
		if (Math.abs(aurorachat$selX - x) < 0.05f) aurorachat$selX = x;
		int ix = (int) Math.floor(aurorachat$selX);
		g.pose().pushMatrix();
		g.pose().translate(aurorachat$selX - ix, 0);
		original.call(g, pipeline, sprite, ix, y, w, h);
		g.pose().popMatrix();
	}
}
