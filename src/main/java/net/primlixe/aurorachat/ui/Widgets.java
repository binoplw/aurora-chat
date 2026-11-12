package net.primlixe.aurorachat.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.primlixe.aurorachat.config.ChatConfig;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Виджеты Aurora Chat в строгом стиле: плоские строки, тонкие линии, без лишнего свечения.
 * Высота строки — {@link UI#rowH()}, текст — шрифтом окна настроек.
 */
public final class Widgets {
	private static Font font() { return Minecraft.getInstance().font; }

	private static void changed() { ChatConfig.save(); }

	private static int textY(int y0, int h) { return y0 + (h - 8) / 2; }

	public enum Style { PRIMARY, SECONDARY, GHOST, DANGER }

	/** Кнопка. */
	public static class Pill extends AbstractWidget {
		private final Consumer<Pill> onPress;
		private Supplier<String> label;
		private final Style style;
		private float hover;

		public Pill(int x, int y, int w, int h, String label, Style style, Consumer<Pill> onPress) {
			super(x, y, w, h, Component.literal(label));
			this.label = () -> I18n.t(label);
			this.style = style;
			this.onPress = onPress;
		}

		public Pill label(Supplier<String> s) { this.label = () -> I18n.t(s.get()); return this; }

		public Pill tip(String t) { if (t != null) setTooltip(UI.tip(t)); return this; }

		public void setLabel(String s) { this.label = () -> I18n.t(s); }

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
			hover = UI.approach(hover, isHoveredOrFocused() && active ? 1 : 0, 0.3f);
			int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
			int fg = UI.TEXT;
			switch (style) {
				case PRIMARY -> {
					UI.round(g, x0, y0, x1, y1, 3, UI.mix(UI.alpha(UI.accent(), 0.85f), UI.accent(), hover));
					fg = 0xFFFFFFFF;
				}
				case DANGER -> {
					UI.round(g, x0, y0, x1, y1, 3, UI.mix(0x30E5484D, 0x70E5484D, hover));
					UI.roundOutline(g, x0, y0, x1, y1, 3, 0x80E5484D);
					fg = 0xFFFFB4B6;
				}
				case GHOST -> { if (hover > 0.02f) UI.round(g, x0, y0, x1, y1, 3, UI.alpha(UI.ink(0x30), hover)); }
				default -> {
					UI.round(g, x0, y0, x1, y1, 3, UI.mix(UI.ink(0x1C), UI.ink(0x30), hover));
					UI.roundOutline(g, x0, y0, x1, y1, 3, UI.mix(UI.ink(0x22), UI.alpha(UI.accent(), 0.7f), hover));
				}
			}
			UI.centered(g, font(), UI.trim(font(), label.get(), width - 8), x0 + width / 2, textY(y0, height), active ? fg : UI.MUTED);
		}

		@Override
		public void onClick(MouseButtonEvent e, boolean dbl) { onPress.accept(this); }

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
	}

	/** Строка-переключатель. */
	public static class Toggle extends AbstractWidget {
		private final String label;
		private final BooleanSupplier get;
		private final Consumer<Boolean> set;
		private float knob = -1, hover;

		public Toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set, String tip) {
			super(x, y, w, UI.rowH(), Component.literal(label));
			this.label = I18n.t(label);
			this.get = get;
			this.set = set;
			if (tip != null) setTooltip(UI.tip(tip));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
			boolean on = get.getAsBoolean();
			if (knob < 0) knob = on ? 1 : 0;
			knob = UI.approach(knob, on ? 1 : 0, 0.35f);
			hover = UI.approach(hover, isHoveredOrFocused() ? 1 : 0, 0.3f);
			int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
			UI.row(g, x0, y0, x1, y1, hover);
			UI.text(g, font(), UI.trim(font(), label, width - 34), x0 + 6, textY(y0, height), UI.mix(UI.SUB, UI.TEXT, 0.6f + 0.4f * Math.max(knob, hover)), false);
			int tw = 18, th = 9, tx = x1 - tw - 6, ty = y0 + (height - th) / 2;
			UI.round(g, tx, ty, tx + tw, ty + th, 4, UI.mix(UI.mix(0xFF3A3D47, UI.ink(0xFF), 0.12f), UI.accent(), knob));
			int kx = tx + 1 + Math.round(knob * (tw - th));
			UI.round(g, kx, ty + 1, kx + th - 2, ty + th - 1, 3, 0xFFF4F5F8);
		}

		@Override
		public void onClick(MouseButtonEvent e, boolean dbl) {
			set.accept(!get.getAsBoolean());
			changed();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
	}

	/** Слайдер: подпись слева, значение справа, тонкая шкала снизу. */
	public static class Slider extends AbstractSliderButton {
		private final String label;
		private final float min, max;
		private final Consumer<Float> set;
		private final Function<Float, String> fmt;
		private Supplier<int[]> gradient;
		private boolean hue;
		private float hover;

		public Slider(int x, int y, int w, String label, float min, float max, float cur, Consumer<Float> set, Function<Float, String> fmt) {
			super(x, y, w, UI.rowH() + 2, Component.empty(), (Math.max(min, Math.min(max, cur)) - min) / (max - min));
			this.label = I18n.t(label); this.min = min; this.max = max; this.set = set; this.fmt = fmt;
			updateMessage();
		}

		public Slider hue() { this.hue = true; return this; }

		public Slider gradient(Supplier<int[]> g) { this.gradient = g; return this; }

		public Slider tip(String t) { if (t != null) setTooltip(UI.tip(t)); return this; }

		private float val() { return min + (float) value * (max - min); }

		@Override
		protected void updateMessage() { setMessage(Component.literal(label)); }

		@Override
		protected void applyValue() { set.accept(val()); changed(); }

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
			hover = UI.approach(hover, isHoveredOrFocused() ? 1 : 0, 0.3f);
			int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
			UI.row(g, x0, y0, x1, y1, hover);
			String v = fmt.apply(val());
			int ty0 = y0 + Math.max(1, (height - 14) / 2);
			UI.text(g, font(), UI.trim(font(), label, width - UI.width(font(), v) - 20), x0 + 6, ty0, UI.TEXT, false);
			if (!v.isEmpty()) UI.text(g, font(), v, x1 - 6 - UI.width(font(), v), ty0, UI.SUB, false);
			int tx0 = x0 + 6, tx1 = x1 - 6, ty = y1 - 3;
			if (hue) {
				// полоса оттенков — готовая текстура (раньше по заливке на каждый пиксель)
				Baked.draw(g, "hue_bar", 256, 1, (x, y) -> UI.hsb(x / 255f, 0.7f, 1f, 1f) & 0xFFFFFF, tx0, ty - 1, tx1, ty + 1);
			} else if (gradient != null) {
				int[] gr = gradient.get();
				UI.hGradient(g, tx0, ty - 1, tx1, ty + 1, gr[0], gr[1]);
			} else {
				g.fill(tx0, ty - 1, tx1, ty, UI.ink(0x30));
				g.fill(tx0, ty - 1, tx0 + (int) (value * (tx1 - tx0)), ty, UI.accent());
			}
			int kx = tx0 + (int) (value * (tx1 - tx0));
			g.fill(kx - 1, ty - 3, kx + 2, ty + 1, hover > 0.5f ? 0xFFFFFFFF : 0xFFD8DAE0);
		}
	}

	/** Выбор из списка: клик — следующее, ПКМ — предыдущее. */
	public static class Choice extends AbstractWidget {
		private final String label;
		private final String[] options;
		private final IntSupplier get;
		private final IntConsumer set;
		private float hover;

		public Choice(int x, int y, int w, String label, String[] options, IntSupplier get, IntConsumer set, String tip) {
			super(x, y, w, UI.rowH(), Component.literal(label));
			this.label = I18n.t(label); this.options = I18n.t(options); this.get = get; this.set = set;
			if (tip != null) setTooltip(UI.tip(tip));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
			hover = UI.approach(hover, isHoveredOrFocused() ? 1 : 0, 0.3f);
			int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
			UI.row(g, x0, y0, x1, y1, hover);
			String v = options[Math.max(0, Math.min(options.length - 1, get.getAsInt()))];
			int maxV = Math.max(40, width / 2);
			v = UI.trim(font(), v, maxV);
			int vw = UI.width(font(), v) + 18;
			int ty = textY(y0, height);
			UI.text(g, font(), UI.trim(font(), label, width - vw - 14), x0 + 6, ty, UI.TEXT, false);
			int bx = x1 - vw - 4;
			UI.round(g, bx, y0 + 2, x1 - 3, y1 - 2, 2, UI.mix(UI.ink(0x1A), UI.alpha(UI.accent(), 0.3f), hover));
			UI.text(g, font(), "‹", bx + 3, ty, UI.MUTED, false);
			UI.text(g, font(), v, bx + 9, ty, UI.TEXT, false);
			UI.text(g, font(), "›", x1 - 9, ty, UI.MUTED, false);
		}

		@Override
		public boolean isValidClickButton(net.minecraft.client.input.MouseButtonInfo info) { return info.button() == 0 || info.button() == 1; }

		@Override
		public void onClick(MouseButtonEvent e, boolean dbl) {
			int n = options.length;
			set.accept(((get.getAsInt() + (e.button() == 1 ? -1 : 1)) % n + n) % n);
			changed();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
	}

	/** Цвет: набор готовых образцов. */
	/**
	 * Выбор цвета: слева — название, крупный образец и HEX; справа — большая палитра (свои цвета раздела +
	 * общий набор ~30 оттенков) в два ряда и спектр, где можно ткнуть или протянуть мышью любой цвет.
	 */
	public static class Swatches extends AbstractWidget {
		/** Общая палитра: яркие, пастель, тёмные и серые — добавляется к цветам каждого раздела. */
		public static final int[] PALETTE = {
				0xFF3B3B, 0xFF6B7A, 0xFF7EC8, 0xFF4FD8, 0xD15CFF, 0xB18CFF, 0x8C7CFF, 0x5865F2, 0x3E6AFF, 0x6CB4FF,
				0x33D6FF, 0x40F0E0, 0x3EF06A, 0x5BE39A, 0xA8FF3E, 0xE8FF4A, 0xFFE066, 0xFFC53D, 0xFFA21F, 0xFF7A1F,
				0xFFFFFF, 0xD8DCEB, 0xA0A6B8, 0x6A6F88, 0x3A3E4E, 0x1B1F2A, 0x0B0C16, 0x000000,
				0x2A1010, 0x2B1430, 0x0E1A33, 0x0F241B, 0x2B2418, 0x1E0F2E};
		private final String label;
		private final int[] colors;
		private final IntSupplier get;
		private final IntConsumer set;
		private final boolean allowAnimated;
		private int[] spec; // x0,y0,x1,y1 спектра в последнем кадре
		/** Вместо «сияния» последняя клетка — «без цвета» (значение -1) с этой подписью. */
		private String noneLabel;

		public Swatches none(String label) { this.noneLabel = I18n.t(label); return this; }

		public Swatches(int x, int y, int w, String label, int[] colors, IntSupplier get, IntConsumer set, boolean allowAnimated) {
			super(x, y, w, 50, Component.literal(label));
			this.label = I18n.t(label); this.get = get; this.set = set; this.allowAnimated = allowAnimated;
			// свои цвета раздела — первыми, потом общая палитра без повторов
			java.util.LinkedHashSet<Integer> all = new java.util.LinkedHashSet<>();
			for (int c : colors) all.add(c & 0xFFFFFF);
			for (int c : PALETTE) all.add(c);
			this.colors = all.stream().mapToInt(Integer::intValue).toArray();
			// клетки 8 px: сколько влезает в ряд — столько и ставим, ряды растут вниз (раньше в узком окне вылезали за край)
			this.height = Math.max(46, 4 + rows() * (CELL + 2) + 1 + 14 + 4); // не ниже образца цвета слева
		}

		private static final int CELL = 8;

		private int count() { return colors.length + (allowAnimated ? 1 : 0); }

		private int areaX0() { return getX() + Math.max(110, (int) (width * 0.36f)); }

		private int perRow() { return Math.max(4, (getX() + width - 6 - areaX0() + 2) / (CELL + 2)); }

		private int rows() { return (count() + perRow() - 1) / perRow(); }

		private int cell() { return CELL; }

		private int[] cellRect(int i) {
			int c = cell(), row = i / perRow(), col = i % perRow();
			int x = areaX0() + col * (c + 2), y = getY() + 4 + row * (c + 2);
			return new int[]{x, y, x + c, y + c};
		}

		/** Цвет спектра: по горизонтали оттенок; сверху пастель, в середине чистый, снизу тёмный. */
		private static int spectrum(float fx, float fy) {
			fx = Math.max(0, Math.min(0.999f, fx));
			fy = Math.max(0, Math.min(1, fy));
			if (fx > 0.94f) { // справа — серая шкала от белого до чёрного
				int v = Math.round(255 * (1 - fy));
				return (v << 16) | (v << 8) | v;
			}
			float hue = fx / 0.94f;
			float s = fy < 0.5f ? 0.15f + fy * 1.7f : 1f;
			float b = fy < 0.5f ? 1f : 1f - (fy - 0.5f) * 1.8f;
			return UI.hsb(hue, Math.min(1, s), Math.max(0.05f, b), 1) & 0xFFFFFF;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
			int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
			UI.row(g, x0, y0, x1, y1, isHovered() ? 1 : 0);
			int ax = areaX0();
			UI.text(g, font(), UI.trim(font(), label, ax - x0 - 12), x0 + 6, y0 + 5, UI.TEXT, false);
			int cur = get.getAsInt();
			// крупный образец + HEX
			int px = x0 + 6, py = y0 + 18;
			int shown = cur < 0 ? (noneLabel != null ? 0xFF15171F : UI.hsb(0.72f + 0.12f * (float) Math.sin(UI.time() * 0.8f), 0.62f, 1f, 1f)) : 0xFF000000 | cur;
			UI.roundOutline(g, px - 1, py - 1, px + 23, py + 23, 3, UI.ink(0x40));
			UI.round(g, px, py, px + 22, py + 22, 3, shown);
			if (cur < 0 && noneLabel != null) UI.centered(g, font(), "✕", px + 11, py + 7, UI.MUTED);
			UI.text(g, font(), cur < 0 ? (noneLabel != null ? noneLabel : I18n.t("сияние")) : String.format(java.util.Locale.ROOT, "#%06X", cur & 0xFFFFFF), px + 28, py + 7, UI.SUB, false);
			// палитра
			int c = cell();
			String tip = null;
			for (int i = 0; i < count(); i++) {
				int[] r = cellRect(i);
				boolean anim = allowAnimated && i == colors.length;
				boolean none = anim && noneLabel != null;
				int col = none ? 0xFF15171F : anim ? UI.hsb(0.72f + 0.12f * (float) Math.sin(UI.time() * 0.8f), 0.62f, 1f, 1f) : 0xFF000000 | colors[i];
				boolean sel = anim ? cur < 0 : cur == colors[i];
				boolean hv = mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
				if (sel || hv) UI.roundOutline(g, r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, 2, sel ? UI.ink(0xFF) : UI.ink(0x90));
				UI.round(g, r[0], r[1], r[2], r[3], 2, col);
				if (anim && c >= 8) UI.centered(g, font(), none ? "✕" : "✦", (r[0] + r[2]) / 2 + 1, r[1] + (c - 8) / 2, none ? UI.MUTED : 0xFFFFFFFF);
				if (hv) tip = none ? noneLabel : anim ? I18n.t("Сияние — переливается") : String.format(java.util.Locale.ROOT, "#%06X", colors[i]);
			}
			// спектр — любой цвет
			int sy0 = y0 + 4 + rows() * (c + 2) + 1, sy1 = y1 - 4, sx0 = ax, sx1 = x1 - 6;
			spec = new int[]{sx0, sy0, sx1, sy1};
			// спектр посчитан один раз в текстуру 256×32 — рисуется одной командой
			Baked.draw(g, "swatch_spectrum", 256, 32, (x, y) -> spectrum(x / 256f, y / 31f), sx0, sy0, sx1, sy1);
			UI.roundOutline(g, sx0 - 1, sy0 - 1, sx1 + 1, sy1 + 1, 1, UI.ink(0x30));
			if (mx >= sx0 && mx < sx1 && my >= sy0 && my < sy1) {
				g.fill(mx, sy0, mx + 1, sy1, 0xC0FFFFFF);
				tip = String.format(java.util.Locale.ROOT, "#%06X · клик или веди мышью", spectrum((mx - sx0) / (float) (sx1 - sx0), (my - sy0) / (float) Math.max(1, sy1 - sy0 - 1)));
			}
			if (tip != null) g.setTooltipForNextFrame(font(), Component.literal(tip), mx, my);
		}

		private boolean pick(double mx, double my) {
			for (int i = 0; i < count(); i++) {
				int[] r = cellRect(i);
				if (mx >= r[0] - 1 && mx < r[2] + 1 && my >= r[1] - 1 && my < r[3] + 1) {
					set.accept(allowAnimated && i == colors.length ? -1 : colors[i]);
					return true;
				}
			}
			if (spec != null && mx >= spec[0] && mx < spec[2] && my >= spec[1] - 1 && my < spec[3] + 1) {
				set.accept(spectrum((float) (mx - spec[0]) / (spec[2] - spec[0]), (float) (my - spec[1]) / Math.max(1, spec[3] - spec[1] - 1)));
				return true;
			}
			return false;
		}

		@Override
		public void onClick(MouseButtonEvent e, boolean dbl) { if (pick(e.x(), e.y())) changed(); }

		@Override
		protected void onDrag(MouseButtonEvent e, double dx, double dy) {
			// тянуть только по спектру — палитру не «перебираем» мышью
			if (spec != null && e.x() >= spec[0] && e.x() < spec[2] && e.y() >= spec[1] - 1 && e.y() < spec[3] + 1 && pick(e.x(), e.y())) changed();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
	}

	/** Заголовок группы: мелкие прописные буквы и тонкая линия. */
	public static class Header extends AbstractWidget {
		private final String text, hint;

		public Header(int x, int y, int w, String text, String hint) {
			super(x, y, w, 16, Component.literal(text));
			this.text = I18n.t(text); this.hint = I18n.t(hint);
			this.active = false;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
			int x0 = getX(), y0 = getY();
			String t = text.toUpperCase(java.util.Locale.ROOT);
			UI.text(g, font(), t, x0 + 1, y0 + 6, UI.SUB, false);
			int lx = x0 + 7 + UI.width(font(), t);
			if (hint != null && !hint.isEmpty()) {
				String h = UI.trim(font(), hint, width - (lx - x0) - 6);
				UI.text(g, font(), h, lx, y0 + 6, UI.MUTED, false);
				lx += UI.width(font(), h) + 6;
			}
			if (lx < x0 + width) g.fill(lx, y0 + 10, x0 + width, y0 + 11, UI.ink(0x1C));
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
	}

	/** Поле ввода. */
	public static class Field extends EditBox {
		private int boxX, boxY, boxW, boxH;

		public Field(Font font, int x, int y, int w, int h, String hint) {
			super(font, x + 6, y + (h - 8) / 2, w - 12, 9, Component.literal(hint));
			setBordered(false);
			setHint(Component.literal("§8" + I18n.t(hint)));
			this.boxX = x; this.boxY = y; this.boxW = w; this.boxH = h;
			// в окне настроек — выбранный шрифт интерфейса
			addFormatter((text, offset) -> UI.inSettings()
					? FormattedCharSequence.forward(text, UI.lit("").getStyle())
					: null);
		}

		@Override
		public void setY(int y) {
			super.setY(y + (boxH - 8) / 2);
			boxY = y;
		}

		@Override
		public void setX(int x) {
			super.setX(x + 6);
			boxX = x;
		}

		public int boxY() { return boxY; }

		public int boxH() { return boxH; }

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
			g.fill(boxX, boxY, boxX + boxW, boxY + boxH, isFocused() ? 0x40000000 : 0x28000000);
			UI.roundOutline(g, boxX, boxY, boxX + boxW, boxY + boxH, 2, isFocused() ? UI.alpha(UI.accent(), 0.9f) : UI.ink(0x26));
			super.extractWidgetRenderState(g, mx, my, pt);
		}
	}

	private Widgets() {}
}
