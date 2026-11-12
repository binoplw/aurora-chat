package net.primlixe.aurorachat.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Переходник между версиями Minecraft — вариант для 26.1–26.1.2 (экран — в Minecraft, чат — в Gui).
 * Остальной код мода обращается к игре через этот класс и не зависит от версии.
 */
public final class Mc {
	public static Screen screen() { return Minecraft.getInstance().screen; }

	public static void setScreen(Screen s) { Minecraft.getInstance().setScreen(s); }

	public static ChatComponent chat() { return Minecraft.getInstance().gui.getChat(); }

	public static int guiTicks() { return Minecraft.getInstance().gui.getGuiTicks(); }

	public static void overlay(Component c, boolean tinted) { Minecraft.getInstance().gui.setOverlayMessage(c, tinted); }

	private Mc() {}
}
