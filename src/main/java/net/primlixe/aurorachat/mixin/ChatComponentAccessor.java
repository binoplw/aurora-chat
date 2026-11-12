package net.primlixe.aurorachat.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("allMessages") List<GuiMessage> aurorachat$all();

	@Accessor("trimmedMessages") List<GuiMessage.Line> aurorachat$trimmed();

	@Accessor("chatScrollbarPos") int aurorachat$scroll();

	@Invoker("refreshTrimmedMessages") void aurorachat$refresh();

	@Invoker("getScale") double aurorachat$scale();

	@Invoker("getLineHeight") int aurorachat$lineHeight();

	@Invoker("getWidth") int aurorachat$width();

	@Invoker("getHeight") int aurorachat$height();
}
