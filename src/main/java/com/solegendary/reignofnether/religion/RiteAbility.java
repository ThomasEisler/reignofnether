package com.solegendary.reignofnether.religion;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.unit.UnitAction;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.List;

// a temple button for a rite (Rite): paid in faith, done by the server (which also says when it can be done again)
public class RiteAbility extends Ability {

    private final Rite rite;

    public RiteAbility(Rite rite) {
        super(UnitAction.NONE, 0, 0, 0, false);
        this.rite = rite;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, BuildingPlacement placement) {
        String me = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getName().getString();
        return new AbilityButton("Rite " + rite.name(),
            ResourceLocation.fromNamespaceAndPath("minecraft", rite.icon),
            hotkey,
            () -> false,
            () -> !placement.ownerName.equals(me),
            () -> placement.isBuilt && Religions.faith(true, placement.ownerName) >= rite.faith,
            () -> ReligionServerboundPacket.rite(rite, placement.originPos),
            null,
            List.of(
                FormattedCharSequence.forward("Rite : " + rite.frName, Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward(rite.faith + " de foi — puis " + (rite.cooldownSeconds / 60) + " min avant de recommencer", Style.EMPTY),
                FormattedCharSequence.forward(rite.description, Style.EMPTY)),
            this,
            placement
        );
    }

    @Override
    public void use(Level level, BuildingPlacement buildingUsing, BlockPos targetBp) { }
}
