package net.primlixe.aurorachat.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Переходник между версиями Minecraft — вариант для 26.2+ (экран — в Gui, чат и тики — в Gui.hud).
 * Остальной код мода обращается к игре через этот класс и не зависит от версии.
 */
public final class Mc {
	public static Screen screen() { return Minecraft.getInstance().gui.screen(); }

	public static void setScreen(Screen s) { Minecraft.getInstance().gui.setScreen(s); }

	public static ChatComponent chat() { return Minecraft.getInstance().gui.hud.getChat(); }

	public static int guiTicks() { return Minecraft.getInstance().gui.hud.getGuiTicks(); }

	public static void overlay(Component c, boolean tinted) { Minecraft.getInstance().gui.hud.setOverlayMessage(c, tinted); }

	private Mc() {}
}
