package net.primlixe.aurorachat.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Отложенная подсказка: в уменьшенном окне настроек рисуем её сами, внутри того же масштаба. */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsAccessor {
	@Accessor("deferredTooltip") Runnable aurorachat$tooltip();

	@Accessor("deferredTooltip") void aurorachat$setTooltip(Runnable r);
}
