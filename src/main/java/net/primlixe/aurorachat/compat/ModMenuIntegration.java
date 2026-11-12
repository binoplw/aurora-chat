package net.primlixe.aurorachat.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.primlixe.aurorachat.gui.ChatSettingsScreen;

/** Кнопка «Настройки» в Mod Menu открывает настройки Aurora Chat. */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ChatSettingsScreen::new;
	}
}
