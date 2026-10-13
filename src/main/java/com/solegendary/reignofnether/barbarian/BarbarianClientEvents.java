package com.solegendary.reignofnether.barbarian;

import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.keybinds.Keybinding;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

// the barbarians button in the start menu: left click cycles Easy -> Normal -> Hard -> Off
public class BarbarianClientEvents {

    public static boolean enabled = true;
    public static BarbarianDifficulty difficulty = BarbarianDifficulty.NORMAL;

    public static void setSettings(boolean enabled, BarbarianDifficulty difficulty) {
        BarbarianClientEvents.enabled = enabled;
        BarbarianClientEvents.difficulty = difficulty;
    }

    private static void cycle() {
        if (!enabled)
            BarbarianServerboundPacket.send(true, BarbarianDifficulty.EASY);
        else if (difficulty == BarbarianDifficulty.HARD)
            BarbarianServerboundPacket.send(false, difficulty);
        else
            BarbarianServerboundPacket.send(true, BarbarianDifficulty.values()[difficulty.ordinal() + 1]);
    }

    private static String getSettingName() {
        return I18n.get(enabled ? difficulty.langKey() : "hud.reignofnether.barbarians.off");
    }

    public static Button getButton() {
        String icon = !enabled ? "textures/item/wooden_axe.png" : switch (difficulty) {
            case EASY -> "textures/item/stone_axe.png";
            case NORMAL -> "textures/item/iron_axe.png";
            case HARD -> "textures/item/netherite_axe.png";
        };
        Button button = new Button(
            "Barbarians",
            Button.itemIconSize,
            ResourceLocation.fromNamespaceAndPath("minecraft", icon),
            (Keybinding) null,
            () -> false,
            () -> false,
            () -> true,
            BarbarianClientEvents::cycle,
            null,
            List.of(
                FormattedCharSequence.forward(I18n.get("hud.reignofnether.barbarians.title", getSettingName()), Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("", Style.EMPTY),
                FormattedCharSequence.forward(I18n.get(enabled ? difficulty.langKey() + ".desc" : "hud.reignofnether.barbarians.off.desc"), Style.EMPTY),
                FormattedCharSequence.forward("", Style.EMPTY),
                FormattedCharSequence.forward(I18n.get("hud.reignofnether.barbarians.change"), Style.EMPTY)
            )
        );
        button.tooltipOffsetY = 15;
        return button;
    }
}
