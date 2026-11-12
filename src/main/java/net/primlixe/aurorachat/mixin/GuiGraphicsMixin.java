package net.primlixe.aurorachat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.primlixe.aurorachat.ui.UI;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Окно настроек рисуется уменьшенным (масштаб матрицы). Области отсечения хранятся в экранных координатах,
 * а мышь у виджетов — в координатах окна, поэтому в настройках точку переводим тем же масштабом —
 * иначе наведение у прокручиваемых строк срабатывало бы мимо.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsMixin {
	@WrapMethod(method = "containsPointInScissor")
	private boolean aurorachat$scaledScissor(int x, int y, Operation<Boolean> original) {
		if (!UI.inSettings()) return original.call(x, y);
		Vector2f p = ((GuiGraphicsExtractor) (Object) this).pose().transformPosition(x, y, new Vector2f());
		return original.call(Math.round(p.x), Math.round(p.y));
	}
}
