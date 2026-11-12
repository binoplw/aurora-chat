package net.primlixe.aurorachat.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.primlixe.aurorachat.config.ChatConfig;
import net.primlixe.aurorachat.ui.UI;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Круговое меню действий с игроком: навёлся на игрока, нажал клавишу (по умолчанию G) —
 * экран плавно темнеет, по центру круг с действиями (/hug, /headpat …). Выбор — мышью в сторону действия
 * и ЛКМ, или отпустить клавишу (если держал), или цифрой 1…9. Esc / ПКМ — отмена.
 */
public class PlayerWheelScreen extends Screen {
	private static final float RING = 74;   // радиус круга с действиями
	private static final float DEAD = 20;   // мёртвая зона в центре

	private final String nick;
	private final UUID uuid;
	private final Player target;
	private final List<ChatConfig.WheelAction> actions = new ArrayList<>();
	private final float[] hover;
	private final float[][] pos;    // где нарисована каждая кнопка — оттуда летят эффекты
	private int[] gearBtn;
	private float gearHover;
	private int holdKey;            // клавиша, которую держат (-1 — режим кликов)
	private boolean holdMouse;      // это кнопка мыши, а не клавиатуры
	private float openT, closeT = -1;
	private long last = System.nanoTime();
	private int selected = -1;
	private float pointer;          // плавный угол стрелки-указателя
	private boolean pointerInit;

	private PlayerWheelScreen(Player target, int holdKey, boolean holdMouse) {
		this(target, holdKey);
		this.holdMouse = holdMouse;
	}

	private PlayerWheelScreen(Player target, int holdKey) {
		super(Component.literal(net.primlixe.aurorachat.ui.I18n.t("Действия с игроком")));
		this.nick = target.getName().getString();
		this.uuid = target.getUUID();
		this.target = target;
		this.holdKey = holdKey;
		for (ChatConfig.WheelAction a : ChatConfig.get().wheelItems) {
			if (!a.enabled || a.command.isBlank()) continue;
			actions.add(a);
			if (actions.size() >= 12) break;
		}
		hover = new float[actions.size()];
		pos = new float[actions.size()][2];
	}

	/**
	 * Нажата клавиша меню: найти игрока под прицелом и открыть круг.
	 * @param quiet не писать «наведись на игрока» (клавиша общая с другой — например, ванильными «Быстрыми действиями» на G)
	 * @return открылось ли меню
	 */
	public static boolean tryOpen(Minecraft mc, KeyMapping key, boolean quiet) {
		ChatConfig c = ChatConfig.get();
		if (!c.playerWheel || mc.player == null || mc.level == null || net.primlixe.aurorachat.compat.Mc.screen() != null) return false;
		Player t = target(mc, c.wheelRange);
		if (t == null) {
			if (!quiet) net.primlixe.aurorachat.compat.Mc.overlay(Component.literal(net.primlixe.aurorachat.ui.I18n.t("§7Наведись на игрока, чтобы выбрать действие")), false);
			return false;
		}
		int code = -1;
		InputConstants.Key k = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(key);
		boolean mouse = false;
		if (k != null && k.getType() == InputConstants.Type.KEYSYM) code = k.getValue();
		else if (k != null && k.getType() == InputConstants.Type.MOUSE) { code = k.getValue(); mouse = true; }
		net.primlixe.aurorachat.compat.Mc.setScreen(new PlayerWheelScreen(t, code, mouse));
		return true;
	}

	/**
	 * Игрок под прицелом: сначала точное попадание лучом по хитбоксу, иначе ближайший к центру экрана в узком конусе.
	 * Луч идёт от камеры, а не от тела — так меню работает и со свободной камерой (Freecam).
	 */
	public static Player target(Minecraft mc, float range) {
		net.minecraft.world.entity.Entity cam = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
		Vec3 eye = cam.getEyePosition(1f);
		Vec3 look = cam.getViewVector(1f);
		Vec3 end = eye.add(look.scale(range));
		Player best = null;
		double bestScore = Double.MAX_VALUE;
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p == mc.player || p == cam || p.isSpectator() || p.isInvisibleTo(mc.player)) continue;
			Vec3 center = p.getBoundingBox().getCenter();
			Vec3 to = center.subtract(eye);
			double dist = to.length();
			if (dist > range || dist < 0.01) continue;
			// видимость: от камеры до глаз или до центра игрока не мешают блоки
			if (!visible(mc, cam, eye, p.getEyePosition(1f)) && !visible(mc, cam, eye, center)) continue;
			AABB box = p.getBoundingBox().inflate(0.35);
			double score;
			if (box.clip(eye, end).isPresent() || box.contains(eye)) score = dist * 0.001; // прямо в прицеле — всегда лучший
			else {
				double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, look.dot(to.normalize())))));
				double allow = Math.max(3.0, Math.toDegrees(Math.atan2(1.2, dist)));
				if (ang > allow) continue;
				score = 1 + ang / allow + dist * 0.01;
			}
			if (score < bestScore) { bestScore = score; best = p; }
		}
		return best;
	}

	private static boolean visible(Minecraft mc, net.minecraft.world.entity.Entity cam, Vec3 from, Vec3 to) {
		return mc.level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, cam)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
		g.nextStratum(); // поверх чата и HUD
		float a = ChatConfig.get().wheelDim * vis();
		if (a > 0.005f) {
			g.fill(0, 0, width, height, UI.rgba(0x05060A, a));
			// мягкое пятно потемнее под кругом — меню читается на любом фоне
			int cx = width / 2, cy = height / 2;
			for (int i = 3; i >= 1; i--) {
				int r = (int) (RING + 26 + i * 14);
				UI.round(g, cx - r, cy - r, cx + r, cy + r, r, UI.rgba(0x000000, a * 0.12f));
			}
		}
	}

	/** Общая видимость 0…1: открытие и закрытие. */
	private float vis() {
		float o = UI.ease(openT);
		return closeT < 0 ? o : o * (1 - UI.ease(closeT));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		boolean anim = ChatConfig.get().animations;
		openT = anim ? Math.min(1, openT + dt / 0.16f) : 1;
		if (closeT >= 0) {
			closeT = anim ? Math.min(1, closeT + dt / 0.18f) : 1;
			if (closeT >= 1) { net.primlixe.aurorachat.compat.Mc.setScreen(null); return; }
		}

		float cx = width / 2f, cy = height / 2f;
		float dx = mx - cx, dy = my - cy;
		float dist = (float) Math.sqrt(dx * dx + dy * dy);
		int n = actions.size();
		if (closeT < 0) selected = n > 0 && dist > DEAD ? sector(dx, dy, n) : -1;

		// круг живёт, пока держишь клавишу: отпустил на действии — выполнить, в центре — просто закрыть
		if (holdKey >= 0 && closeT < 0) {
			long win = minecraft.getWindow().handle();
			boolean down = holdMouse ? GLFW.glfwGetMouseButton(win, holdKey) == GLFW.GLFW_PRESS : GLFW.glfwGetKey(win, holdKey) == GLFW.GLFW_PRESS;
			if (!down) {
				if (selected >= 0) run(selected);
				else onClose();
			}
		}

		float v = vis();
		float ease = UI.ease(openT);
		float ring = RING * (0.55f + 0.45f * ease) * (closeT < 0 ? 1 : 1 - 0.15f * UI.ease(closeT));
		int acc = UI.accent();

		// кольцо-подложка
		int ro = (int) (ring + 22), ri = (int) (ring - 22);
		UI.round(g, Math.round(cx) - ro, Math.round(cy) - ro, Math.round(cx) + ro, Math.round(cy) + ro, ro, UI.rgba(0x10121A, 0.55f * v));
		UI.round(g, Math.round(cx) - ri, Math.round(cy) - ri, Math.round(cx) + ri, Math.round(cy) + ri, ri, UI.rgba(0x05060A, 0.35f * v));

		// стрелка-указатель по краю внутреннего круга
		if (selected >= 0 || dist > DEAD) {
			float target = (float) Math.atan2(dy, dx);
			if (!pointerInit) { pointer = target; pointerInit = true; }
			float d = (float) Math.atan2(Math.sin(target - pointer), Math.cos(target - pointer));
			pointer += anim ? d * (1 - (float) Math.exp(-28 * dt)) : d;
			for (int i = 0; i < 3; i++) {
				float rr = ri - 5 - i * 3, sz = 3 - i;
				float px = cx + (float) Math.cos(pointer) * rr, py = cy + (float) Math.sin(pointer) * rr;
				g.fill(Math.round(px - sz / 2f), Math.round(py - sz / 2f), Math.round(px + sz / 2f) + 1, Math.round(py + sz / 2f) + 1, UI.alpha(acc, v * (1 - i * 0.3f)));
			}
		}

		// центр: голова и ник
		int head = 24;
		PlayerInfo pi = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(uuid);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		float cs = 0.8f + 0.2f * ease;
		g.pose().scale(cs, cs);
		if (pi != null) {
			UI.round(g, -head / 2 - 2, -head / 2 - 8, head / 2 + 2, head / 2 - 4, 3, UI.rgba(0xFFFFFF, 0.12f * v));
			PlayerFaceExtractor.extractRenderState(g, net.primlixe.auroralib.compat.Skins.of(pi), -head / 2, -head / 2 - 6, head, UI.rgba(0xFFFFFF, v));
		}
		String name = net.primlixe.aurorachat.chat.Nicknames.display(nick);
		g.text(font, name, -font.width(name) / 2, pi != null ? head / 2 - 1 : -4, UI.rgba(0xFFFFFF, v), true);
		g.pose().popMatrix();

		// действия по кругу, первое — сверху
		for (int i = 0; i < n; i++) {
			hover[i] = anim ? UI.damp(hover[i], i == selected ? 1 : 0, 22, dt) : (i == selected ? 1 : 0);
			// лёгкая «волна» при открытии: пункты выезжают по очереди
			float stagger = anim ? UI.ease(Math.max(0, Math.min(1, (openT * 1.6f) - i * 0.6f / Math.max(1, n)))) : 1;
			float a = (float) (start(n) + i * Math.PI * 2 / n);
			// при применении выбранная кнопка «вспыхивает» и растёт, остальные гаснут быстрее
			boolean chosen = closeT >= 0 && i == selected;
			float r = ring * (0.6f + 0.4f * stagger) + hover[i] * 5;
			float bx = cx + (float) Math.cos(a) * r, by = cy + (float) Math.sin(a) * r;
			pos[i][0] = bx;
			pos[i][1] = by;
			String label = actions.get(i).name;
			String num = i < 9 ? String.valueOf(i + 1) : "";
			int w = font.width(label) + 14 + (num.isEmpty() ? 0 : 10), h = 16;
			float sc = 1 + 0.12f * hover[i] + (chosen ? 0.25f * UI.ease(closeT) : 0);
			float ia = (chosen ? UI.ease(openT) * (1 - closeT * closeT) : v) * stagger;
			g.pose().pushMatrix();
			g.pose().translate(bx, by);
			g.pose().scale(sc, sc);
			int x0 = -w / 2, y0 = -h / 2;
			if (hover[i] > 0.02f) UI.shadow(g, x0, y0, x0 + w, y0 + h, 8, ia * hover[i]);
			int bg = UI.mix(0xFF1B1E2B, acc, hover[i] * 0.85f);
			UI.round(g, x0, y0, x0 + w, y0 + h, 8, UI.alpha(bg, ia * (0.85f + 0.15f * hover[i])));
			UI.roundOutline(g, x0, y0, x0 + w, y0 + h, 8, UI.alpha(UI.mix(0x30FFFFFF, 0xC0FFFFFF, hover[i]), ia));
			int tx = x0 + 7;
			if (!num.isEmpty()) {
				g.text(font, num, tx, y0 + 4, UI.rgba(UI.mix(0xFF8A90A6, 0xFFFFFFFF, hover[i]), ia), false);
				tx += 10;
			}
			g.text(font, label, tx, y0 + 4, UI.rgba(0xFFFFFF, ia), hover[i] > 0.5f);
			g.pose().popMatrix();
		}

		// подсказки снизу
		String cmd = selected >= 0 ? cmd(actions.get(selected)) : null;
		String hint = n == 0 ? net.primlixe.aurorachat.ui.I18n.t("Нет действий — добавь их в «⚙ Настроить»")
				: cmd != null ? (cmd.startsWith("?") ? net.primlixe.aurorachat.ui.I18n.t("в строку чата: ") + cmd.substring(1).trim() : cmd)
				: holdKey >= 0 ? net.primlixe.aurorachat.ui.I18n.t("Веди мышь к действию и отпусти — в центре отпустишь, ничего не будет") : net.primlixe.aurorachat.ui.I18n.t("ЛКМ или 1…") + Math.min(9, n) + net.primlixe.aurorachat.ui.I18n.t(" — выбрать · Esc — отмена");
		int hy = Math.round(cy + RING + 34);
		g.text(font, hint, Math.round(cx) - font.width(hint) / 2, hy, UI.rgba(cmd != null ? 0xD8DCEB : 0x8A90A6, v), false);

		// «⚙ Настроить» — сразу в раздел действий
		String gl = net.primlixe.aurorachat.ui.I18n.t("⚙ Настроить");
		int gw = font.width(gl) + 12, gx = Math.round(cx) - gw / 2, gy = hy + 14;
		gearBtn = new int[]{gx, gy, gx + gw, gy + 14};
		boolean gh = mx >= gearBtn[0] && mx < gearBtn[2] && my >= gearBtn[1] && my < gearBtn[3];
		gearHover = anim ? UI.damp(gearHover, gh ? 1 : 0, 22, dt) : (gh ? 1 : 0);
		UI.round(g, gearBtn[0], gearBtn[1], gearBtn[2], gearBtn[3], 7, UI.rgba(0xFFFFFF, (0.06f + 0.12f * gearHover) * v));
		g.text(font, gl, gx + 6, gy + 3, UI.rgba(UI.mix(0xFF8A90A6, 0xFFFFFFFF, gearHover), v), false);
	}

	private String cmd(ChatConfig.WheelAction a) {
		return a.command.replace("{ник}", nick).replace("{name}", nick).replace("{nick}", nick);
	}

	/** Угол первого действия: два — слева и справа, иначе первое сверху. */
	private static double start(int n) { return n == 2 ? Math.PI : -Math.PI / 2; }

	private static int sector(float dx, float dy, int n) {
		double a = Math.atan2(dy, dx) - start(n); // 0 — первое действие
		double step = Math.PI * 2 / n;
		int i = (int) Math.floor((a + step / 2) / step);
		return ((i % n) + n) % n;
	}

	private void run(int i) {
		if (closeT >= 0 || i < 0 || i >= actions.size()) return;
		ChatConfig.WheelAction act = actions.get(i);
		String cmd = cmd(act);
		selected = i;
		net.primlixe.aurorachat.chat.WheelEffects.play(act.fx, act.name, nick, pos[i][0], pos[i][1], width / 2f, height / 2f, target);
		if (cmd.startsWith("?")) {
			String t = cmd.substring(1).trim();
			net.primlixe.aurorachat.compat.Mc.setScreen(new ChatScreen(t.isEmpty() ? t : t + " ", false));
			return;
		}
		var con = minecraft.getConnection();
		String c = cmd.trim();
		// новое действие из настроек — пока просто «/»: пустую команду серверу не шлём
		if (con != null && !c.isEmpty() && !c.equals("/")) {
			if (c.startsWith("/")) con.sendCommand(c.substring(1).trim());
			else con.sendChat(c);
		}
		closeT = 0; // плавно исчезаем
		holdKey = -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (closeT >= 0) return true;
		if (e.button() == 1) { onClose(); return true; }
		if (e.button() == 0 && gearBtn != null && e.x() >= gearBtn[0] && e.x() < gearBtn[2] && e.y() >= gearBtn[1] && e.y() < gearBtn[3]) {
			net.primlixe.aurorachat.compat.Mc.setScreen(ChatSettingsScreen.wheel(null));
			return true;
		}
		if (e.button() == 0 && selected >= 0) run(selected);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (closeT >= 0) return true;
		int k = e.key();
		if (k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_9) { run(k - GLFW.GLFW_KEY_1); return true; }
		if (k >= GLFW.GLFW_KEY_KP_1 && k <= GLFW.GLFW_KEY_KP_9) { run(k - GLFW.GLFW_KEY_KP_1); return true; }
		if ((k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_SPACE) && selected >= 0) { run(selected); return true; }
		if (k == holdKey) return true;
		return super.keyPressed(e);
	}

	@Override
	public void onClose() {
		if (closeT < 0) { closeT = 0; selected = -1; holdKey = -1; }
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
