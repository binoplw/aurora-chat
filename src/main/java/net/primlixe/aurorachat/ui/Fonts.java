package net.primlixe.aurorachat.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Шрифты: обычный, ровный (Unifont) или любой из ресурспака по id. */
public final class Fonts {
	public static final String[] IDS = {"minecraft:default", "minecraft:uniform"};
	public static final String[] NAMES = {"Обычный", "Ровный (Unifont)"};

	private static final Map<String, FontDescription> CACHE = new HashMap<>();

	/** null — обычный шрифт (ничего не меняем). */
	public static FontDescription of(String id) {
		if (id == null || id.isBlank() || id.equals("minecraft:default") || id.equals("default")) return null;
		return CACHE.computeIfAbsent(id, k -> {
			Identifier i = Identifier.tryParse(k.contains(":") ? k : "minecraft:" + k);
			return i == null ? null : new FontDescription.Resource(i);
		});
	}

	public static Component wrap(Component c, String id) {
		FontDescription f = of(id);
		return f == null ? c : Component.empty().append(c).withStyle(s -> s.withFont(f));
	}

	public static int indexOf(String id) {
		for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
		return -1;
	}

	private Fonts() {}
}
