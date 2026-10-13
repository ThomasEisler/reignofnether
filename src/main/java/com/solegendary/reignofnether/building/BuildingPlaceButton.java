package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.keybinds.Keybinding;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Supplier;

public class BuildingPlaceButton extends Button {
    public BuildingPlaceButton(String name, ResourceLocation rl, Keybinding hotkey, Supplier<Boolean> isSelected,
                               Supplier<Boolean> isHidden, Supplier<Boolean> isEnabled,
                               List<FormattedCharSequence> tooltipLines, Building building) {

        // generate x/y based on given position (starting at 0 which is bottom left 1 row above generic action buttons)
        // buildings that need a technology the player hasn't discovered yet are hidden from the build menu
        super(name, Button.itemIconSize, rl, hotkey, isSelected,
            () -> isHidden.get() || (net.minecraft.client.Minecraft.getInstance().player != null &&
                !com.solegendary.reignofnether.tech.Tech.canBuild(true, net.minecraft.client.Minecraft.getInstance().player.getName().getString(), building)),
            isEnabled, () -> BuildingClientEvents.setBuildingToPlace(building), null, tooltipLines);
    }
}
