package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.keybinds.Keybinding;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

// the bots button in the start menu: left click = number of bots (0 to 3), right click = difficulty
public class BotClientEvents {

    public static int wanted = 0;
    public static BotDifficulty difficulty = BotDifficulty.NORMAL;

    public static void setSettings(int wanted, BotDifficulty difficulty) {
        BotClientEvents.wanted = wanted;
        BotClientEvents.difficulty = difficulty;
    }

    public static Button getButton() {
        String icon = wanted == 0 ? "textures/item/barrier.png" : switch (difficulty) {
            case EASY -> "textures/item/wooden_sword.png";
            case NORMAL -> "textures/item/iron_sword.png";
            case HARD -> "textures/item/netherite_sword.png";
        };
        String title = wanted == 0 ? "Bots : aucun" : "Bots : " + wanted + " (" + difficulty.frName + ")";
        Button button = new Button(
            "Bots",
            Button.itemIconSize,
            ResourceLocation.fromNamespaceAndPath("minecraft", icon),
            (Keybinding) null,
            () -> false,
            () -> false,
            () -> true,
            () -> BotServerboundPacket.send((wanted + 1) % (BotSettings.MAX_BOTS + 1), difficulty),
            () -> BotServerboundPacket.send(wanted, BotDifficulty.values()[(difficulty.ordinal() + 1) % BotDifficulty.values().length]),
            List.of(
                FormattedCharSequence.forward(title, Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("", Style.EMPTY),
                FormattedCharSequence.forward("Civilisations dirigées par l'ordinateur. Elles arrivent", Style.EMPTY),
                FormattedCharSequence.forward("une par une dès que vous avez un Hôtel de ville.", Style.EMPTY),
                FormattedCharSequence.forward("", Style.EMPTY),
                FormattedCharSequence.forward("Clic gauche : nombre de bots", Style.EMPTY),
                FormattedCharSequence.forward("Clic droit : facile, normal, difficile", Style.EMPTY)
            )
        );
        button.tooltipOffsetY = 15;
        return button;
    }
}
