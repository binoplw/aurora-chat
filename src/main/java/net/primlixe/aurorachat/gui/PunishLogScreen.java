package net.primlixe.aurorachat.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Публичная сборка: журнала наказаний нет — экран сразу закрывается. */
public class PunishLogScreen extends Screen {
	private final Screen parent;

	public PunishLogScreen(Screen parent) {
		super(Component.literal("Aurora Chat"));
		this.parent = parent;
	}

	@Override
	protected void init() { net.primlixe.aurorachat.compat.Mc.setScreen(parent); }
}
