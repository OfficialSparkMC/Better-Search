package com.bettersearch.tabs;

import com.bettersearch.search.ModuleSearch;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.Click;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

/**
 * Custom clickable module card rendered with Meteor's shared GuiRenderer.
 * Rounded corners, customizable padding/size, left-click toggles,
 * right-click opens settings (inline locked or classic draggable).
 *
 * <p>Credits: Turbo</p>
 */
public class ModuleCard extends WPressable {
    private static final Color BG = new Color(22, 22, 28, 150);
    private static final Color BG_HOVER = new Color(48, 48, 60, 185);
    private static final Color BG_OUTLINED = new Color(66, 54, 20, 205);

    private final ModuleSearch.Result result;
    public boolean selected;
    public boolean rounded = true;
    public double radius = 6;
    public int padExtra = 2;
    public boolean showDot = true;
    /** Right-side meta text (category/state). Null = hidden (compact grid). */
    public String meta;

    /** Outline border (e.g. module whose settings are open). Null = none. */
    public Color outline = null;

    public Runnable onToggle;
    public Runnable onSettings;

    private double pad;
    private double titleW;
    private double metaW;
    private double dotSize;

    public ModuleCard(ModuleSearch.Result result) {
        this.result = result;
        this.tooltip = result.module().description;
    }

    @Override
    protected void onCalculateSize() {
        pad = theme.scale(4 + padExtra);
        double gap = theme.scale(4);

        dotSize = theme.textHeight() * 0.62;
        titleW = theme.textWidth(result.module().title);
        metaW = meta != null ? theme.textWidth(meta) : 0;

        double w = pad * 2 + titleW;
        if (showDot) w += dotSize + gap;
        if (meta != null) w += gap + metaW;

        width = w;
        height = pad * 2 + theme.textHeight();

        double minWidth = theme.scale(this.minWidth);
        if (width < minWidth) width = minWidth;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Color bg = outline != null ? BG_OUTLINED : ((selected || mouseOver) ? BG_HOVER : BG);
        double rad = theme.scale(radius);

        if (outline != null) {
            // Border as 4 plain quads (no texture dependency) + tinted bg below.
            // Visible on every theme and GUI scale.
            double o = theme.scale(1.5);
            if (o < 2) o = 2;
            renderer.quad(x - o, y - o, width + o * 2, o, outline); // top
            renderer.quad(x - o, y + height, width + o * 2, o, outline); // bottom
            renderer.quad(x - o, y, o, height, outline); // left
            renderer.quad(x + width, y, o, height, outline); // right
        }

        if (rounded && rad > 0) rounded(renderer, x, y, width, height, rad, bg);
        else renderer.quad(x, y, width, height, bg);

        double cy = y + height / 2;
        double cx = x + pad;

        if (showDot) {
            boolean active = result.module().isActive();
            Color dotColor = selected ? Color.YELLOW : (active ? Color.GREEN : Color.GRAY);
            double ds = dotSize;
            renderer.quad(cx, cy - ds / 2, ds, ds, GuiRenderer.CIRCLE, dotColor);
            cx += ds + theme.scale(4);
        }

        renderer.text(result.module().title, cx, y + pad, theme.textColor(), false);

        if (meta != null) {
            Color metaColor = selected ? theme.textColor() : theme.textSecondaryColor();
            renderer.text(meta, x + width - pad - metaW, y + pad, metaColor, false);
        }
    }

    static void rounded(GuiRenderer r, double x, double y, double w, double h, double rad, Color c) {
        if (w <= 0 || h <= 0) return;
        rad = Math.min(rad, Math.min(w, h) / 2);
        if (rad <= 0) {
            r.quad(x, y, w, h, c);
            return;
        }
        double d = rad * 2;
        r.quad(x + rad, y, w - d, h, c);
        r.quad(x, y + rad, w, h - d, c);
        r.quad(x, y, d, d, GuiRenderer.CIRCLE, c);
        r.quad(x + w - d, y, d, d, GuiRenderer.CIRCLE, c);
        r.quad(x, y + h - d, d, d, GuiRenderer.CIRCLE, c);
        r.quad(x + w - d, y + h - d, d, d, GuiRenderer.CIRCLE, c);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        // Meteor only refreshes hover on mouse motion, so a rebuilt card under a
        // stationary cursor would eat clicks. Recompute for the click position.
        mouseOver = isOver(click.x(), click.y());
        return super.mouseClicked(click, doubled);
    }

    @Override
    protected void onPressed(int button) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            if (onToggle != null) onToggle.run();
        } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            if (onSettings != null) onSettings.run();
        }
    }
}
