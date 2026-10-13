package com.solegendary.reignofnether.tech;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.unit.UnitAction;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

// Library button that opens the technology tree
public class OpenTechTree extends Ability {

    public OpenTechTree() {
        super(UnitAction.NONE, 0, 0, 0, false);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, BuildingPlacement placement) {
        return new AbilityButton("Technologies",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/writable_book.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> placement.isBuilt,
            TechClientEvents::openTree,
            null,
            List.of(FormattedCharSequence.forward("Arbre des technologies", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Lancer des recherches qui débloquent des bâtiments et des niveaux.", Style.EMPTY)),
            this,
            placement
        );
    }
}
