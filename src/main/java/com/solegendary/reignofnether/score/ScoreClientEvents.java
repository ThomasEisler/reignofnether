package com.solegendary.reignofnether.score;

import com.solegendary.reignofnether.nation.Nation;
import com.solegendary.reignofnether.nation.NationClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

// the ranking, shown at the bottom of the screen while the diplomacy screen is open (key 8)
public class ScoreClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    public static List<Score> scores = new ArrayList<>();

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        if (!(evt.getScreen() instanceof com.solegendary.reignofnether.guiscreen.TopdownGui) || scores.isEmpty() ||
            !com.solegendary.reignofnether.hud.playerdisplay.PlayerDisplayClientEvents.isDiplomacyOpen())
            return;
        GuiGraphics g = evt.getGuiGraphics();
        int sw = MC.getWindow().getGuiScaledWidth();
        int nameW = 90, colW = 52, w = nameW + colW * (Score.DOMAINS.length + 1) + 16, x = sw / 2 - w / 2;
        int h = 30 + scores.size() * 12, y = MC.getWindow().getGuiScaledHeight() - h - 60;
        g.fill(x, y, x + w, y + h, 0xC0000000);
        g.drawCenteredString(MC.font, "Classement", sw / 2, y + 5, 0xFFFFD050);
        int hy = y + 17;
        for (int d = 0; d < Score.DOMAINS.length; d++)
            drawRight(g, Score.DOMAINS[d].substring(0, Math.min(8, Score.DOMAINS[d].length())), x + 8 + nameW + colW * (d + 1), hy, 0xFFB0B0B0);
        drawRight(g, "Total", x + 8 + nameW + colW * (Score.DOMAINS.length + 1), hy, 0xFFFFD050);
        int ly = hy + 12, rank = 1;
        for (Score s : scores) {
            Nation n = NationClientEvents.getNation(s.civ());
            int colour = 0xFF000000 | (n != null ? n.colour : 0xFFFFFF);
            g.drawString(MC.font, rank++ + ". " + s.civ(), x + 8, ly, colour);
            for (int d = 0; d < Score.DOMAINS.length; d++)
                drawRight(g, String.valueOf(s.get(d)), x + 8 + nameW + colW * (d + 1), ly, 0xFFFFFFFF);
            drawRight(g, String.valueOf(s.total()), x + 8 + nameW + colW * (Score.DOMAINS.length + 1), ly, 0xFFFFD050);
            ly += 12;
        }
    }

    private static void drawRight(GuiGraphics g, String text, int right, int y, int colour) {
        g.drawString(MC.font, text, right - MC.font.width(text), y, colour);
    }
}
