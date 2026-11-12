package net.primlixe.aurorachat.compat;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Переходник между версиями Minecraft — вариант для 26.3+ (ввод через SDL) (экран — в Gui, чат и тики — в Gui.hud).
 * Остальной код мода обращается к игре через этот класс и не зависит от версии.
 */
public final class Mc {
	public static Screen screen() { return Minecraft.getInstance().gui.screen(); }

	public static void setScreen(Screen s) { Minecraft.getInstance().gui.setScreen(s); }

	public static ChatComponent chat() { return Minecraft.getInstance().gui.hud.getChat(); }

	public static int guiTicks() { return Minecraft.getInstance().gui.hud.getGuiTicks(); }

	public static void overlay(Component c, boolean tinted) { Minecraft.getInstance().gui.hud.setOverlayMessage(c, tinted); }

	/** Тип «клавиатура» для KeyMapping. */
	public static final InputConstants.Type KEYS = InputConstants.Type.KEYBOARD;

	public static boolean keyDown(int key) { return InputConstants.isKeyDown(key); }

	/** button — InputConstants.MOUSE_BUTTON_*. */
	public static boolean mouseDown(int button) {
		var m = Minecraft.getInstance().mouseHandler;
		return button == InputConstants.MOUSE_BUTTON_LEFT ? m.isLeftPressed() : button == InputConstants.MOUSE_BUTTON_RIGHT ? m.isRightPressed()
				: button == InputConstants.MOUSE_BUTTON_MIDDLE && m.isMiddlePressed();
	}

	public static void openPath(Path p) { com.mojang.blaze3d.Blaze3D.openPath(p); }

	public static void openUri(String u) { com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(u)); }

	private Mc() {}
}
