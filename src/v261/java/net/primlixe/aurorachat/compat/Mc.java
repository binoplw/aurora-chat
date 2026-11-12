package net.primlixe.aurorachat.compat;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import net.minecraft.util.Util;
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

	/** Тип «клавиатура» для KeyMapping. */
	public static final InputConstants.Type KEYS = InputConstants.Type.KEYSYM;

	public static boolean keyDown(int key) { return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key); }

	/** button — InputConstants.MOUSE_BUTTON_*. */
	public static boolean mouseDown(int button) {
		return org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), button) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
	}

	public static void openPath(Path p) { Util.getPlatform().openPath(p); }

	public static void openUri(String u) { Util.getPlatform().openUri(u); }

	private Mc() {}
}
