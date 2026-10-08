package com.bettersearch.tabs;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.Click;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

/**
 * Clean rounded card: background + optional outline ring drawn with Meteor's
 * shared GuiRenderer behind normal theme widgets. Clicks are NOT handled here
 * (children like Meteor's module widget do that natively); only right-click is
 * intercepted statelessly per event, so nothing can go stale or get stuck.
 * Theme is assigned in the constructor — never add children before that.
 */
public class CardBack extends WVerticalList {
    private static final Color BG = new Color(22, 22, 28, 150);
    private static final Color BG_SELECTED = new Color(48, 48, 60, 185);
    private static final Color BG_OUTLINED = new Color(66, 54, 20, 205);

    public boolean selected = false;
    public boolean outlined = false;
    public double radius = 6;
    public double pad = 2;

    /** Right-click action (e.g. open settings). Left clicks pass to children. */
    public Runnable onSettings;

    public CardBack(GuiTheme theme) {
        this.theme = theme;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        // Right click opens our settings and never reaches Meteor's own window.
        // Stateless hit-test per event — nothing stored, nothing that can go stale.
        if (click.button() == GLFW_MOUSE_BUTTON_RIGHT && isOver(click.x(), click.y())) {
            if (onSettings != null) onSettings.run();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Color bg = outlined ? BG_OUTLINED : (selected ? BG_SELECTED : BG);
        double rad = theme.scale(radius);
        double p = theme.scale(pad);
        double x = this.x - p;
        double y = this.y - p;
        double w = this.width + p * 2;
        double h = this.height + p * 2;
        if (outlined) {
            double o = theme.scale(1.5);
            if (o < 2) o = 2;
            // Outline ring first (rounded, follows the corners)...
            if (rad > 0) rounded(renderer, x - o, y - o, w + o * 2, h + o * 2, rad + o, Color.YELLOW);
            else {
                renderer.quad(x - o, y - o, w + o * 2, o, Color.YELLOW);
                renderer.quad(x - o, y + h, w + o * 2, o, Color.YELLOW);
                renderer.quad(x - o, y, o, h, Color.YELLOW);
                renderer.quad(x + w, y, o, h, Color.YELLOW);
            }
        }
        // ...background on top leaves exactly the border visible.
        if (rad > 0) rounded(renderer, x, y, w, h, rad, bg);
        else renderer.quad(x, y, w, h, bg);
    }

    /**
     * Filled rounded rectangle, every pixel painted exactly once (no overlapping
     * pieces, so no double-blended stripes or visible disc edges): center +
     * edge bars inset past the corner discs + corner discs.
     */
    static void rounded(GuiRenderer r, double x, double y, double w, double h, double rad, Color c) {
        if (w <= 0 || h <= 0) return;
        rad = Math.min(rad, Math.min(w, h) / 2);
        if (rad <= 0) {
            r.quad(x, y, w, h, c);
            return;
        }
        double d = rad * 2;
        if (w < d * 2 || h < d * 2) {
            r.quad(x, y, w, h, c);
            return;
        }
        r.quad(x + rad, y + rad, w - d, h - d, c); // center
        r.quad(x + d, y, w - d * 2, rad, c); // top
        r.quad(x + d, y + h - rad, w - d * 2, rad, c); // bottom
        r.quad(x, y + d, rad, h - d * 2, c); // left
        r.quad(x + w - rad, y + d, rad, h - d * 2, c); // right
        r.quad(x, y, d, d, GuiRenderer.CIRCLE, c);
        r.quad(x + w - d, y, d, d, GuiRenderer.CIRCLE, c);
        r.quad(x, y + h - d, d, d, GuiRenderer.CIRCLE, c);
        r.quad(x + w - d, y + h - d, d, d, GuiRenderer.CIRCLE, c);
    }
}
