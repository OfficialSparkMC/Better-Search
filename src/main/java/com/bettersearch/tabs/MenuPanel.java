package com.bettersearch.tabs;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * Rounded background panel with outline, drawn with Meteor's shared GuiRenderer.
 * Used behind the inline module menu. Children lay out normally; the background
 * is drawn expanded by {@link #pad} so it also acts as inner padding.
 */
public class MenuPanel extends WVerticalList {
    public boolean drawBg = true;
    public Color bg = new Color(12, 12, 18, 210);
    public boolean outline = true;
    public Color outlineColor = Color.YELLOW;
    public double radius = 8;
    public double pad = 6;

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double p = theme.scale(pad);
        double rad = theme.scale(radius);
        double x = this.x - p;
        double y = this.y - p;
        double w = this.width + p * 2;
        double h = this.height + p * 2;
        // Outline ring first (follows the same rounded corners), background on top.
        if (outline) {
            double o = theme.scale(1.5);
            if (o < 2) o = 2;
            if (rad > 0) ModuleCard.rounded(renderer, x - o, y - o, w + o * 2, h + o * 2, rad + o, outlineColor);
            else {
                renderer.quad(x - o, y - o, w + o * 2, o, outlineColor);
                renderer.quad(x - o, y + h, w + o * 2, o, outlineColor);
                renderer.quad(x - o, y, o, h, outlineColor);
                renderer.quad(x + w, y, o, h, outlineColor);
            }
        }
        if (drawBg) {
            if (rad > 0) ModuleCard.rounded(renderer, x, y, w, h, rad, bg);
            else renderer.quad(x, y, w, h, bg);
        }
    }
}
