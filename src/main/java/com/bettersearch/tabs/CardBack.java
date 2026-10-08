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
        // Plain square cards only: background quad + 4 straight outline bars.
        // No curves, no textures — nothing here can render a circle.
        Color bg = outlined ? BG_OUTLINED : (selected ? BG_SELECTED : BG);
        double p = theme.scale(pad);
        double x = this.x - p;
        double y = this.y - p;
        double w = this.width + p * 2;
        double h = this.height + p * 2;
        if (outlined) {
            double o = theme.scale(1.5);
            if (o < 2) o = 2;
            renderer.quad(x - o, y - o, w + o * 2, o, Color.YELLOW);
            renderer.quad(x - o, y + h, w + o * 2, o, Color.YELLOW);
            renderer.quad(x - o, y, o, h, Color.YELLOW);
            renderer.quad(x + w, y, o, h, Color.YELLOW);
        }
        renderer.quad(x, y, w, h, bg);
    }
}
