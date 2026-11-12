package net.primlixe.aurorachat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.ui.Perf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Плавное открытие любых экранов игры: инвентарь, сундуки, пауза, настройки, меню. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Unique private long aurorachat$openedAt;

	@Inject(method = "added", at = @At("TAIL"))
	private void aurorachat$opened(CallbackInfo ci) {
		aurorachat$openedAt = System.nanoTime();
	}

	@WrapMethod(method = "extractRenderStateWithTooltipAndSubtitles")
	private void aurorachat$animate(GuiGraphicsExtractor g, int mx, int my, float pt, Operation<Void> original) {
		ChatConfig c = ChatConfig.get();
		Object self = this;
		boolean skip = !c.mcScreenAnim || aurorachat$openedAt == 0
				|| self instanceof ChatScreen                                           // у чата своя анимация
				|| self.getClass().getName().startsWith("net.primlixe.aurorachat")    // и у окон мода
				|| (self instanceof TitleScreen && !c.mcScreenTitle);
		float e = skip ? 1 : Perf.appear(aurorachat$openedAt, c.mcScreenTime);
		if (e >= 1) {
			original.call(g, mx, my, pt);
			return;
		}
		float w = g.guiWidth(), h = g.guiHeight();
		g.pose().pushMatrix();
		switch (c.mcScreenStyle) {
			case ZOOM -> {
				float s = 0.94f + 0.06f * e;
				g.pose().translate(w / 2, h / 2);
				g.pose().scale(s, s);
				g.pose().translate(-w / 2, -h / 2);
			}
			case SLIDE -> g.pose().translate(-(1 - e) * 16, 0);
			default -> g.pose().translate(0, (1 - e) * 10);
		}
		try {
			original.call(g, mx, my, pt);
		} finally {
			g.pose().popMatrix();
		}
	}
}
