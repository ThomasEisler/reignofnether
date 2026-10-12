package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.road.RoadServerEvents;
import com.solegendary.reignofnether.road.RoadType;
import com.solegendary.reignofnether.unit.UnitAction;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.List;

// Select a building, use this, then click another of your buildings to build a road between them.
// Validation, payment and road placement all happen serverside in RoadServerEvents.tryBuildRoad
public class BuildRoad extends Ability {

    private final RoadType type;

    public BuildRoad(RoadType type) {
        super(UnitAction.BUILD_ROAD, 0, 0, 0, false);
        this.type = type;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, BuildingPlacement placement) {
        if (placement.getBuilding() instanceof AbstractBridge || placement.ownerName.isEmpty())
            return null;
        return new AbilityButton("Build Road",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/dirt_path_top.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.BUILD_ROAD,
            () -> false,
            () -> placement.isBuilt,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.BUILD_ROAD),
            null,
            List.of(FormattedCharSequence.forward(I18n.get("abilities.reignofnether.build_road"), Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("", Style.EMPTY),
                FormattedCharSequence.forward(I18n.get("abilities.reignofnether.build_road.tooltip1"), Style.EMPTY),
                FormattedCharSequence.forward(I18n.get("abilities.reignofnether.build_road.tooltip2",
                    type.woodPerLength, type.orePerLength), Style.EMPTY),
                FormattedCharSequence.forward("", Style.EMPTY),
                FormattedCharSequence.forward(I18n.get("abilities.reignofnether.build_road.tooltip3"), Style.EMPTY),
                FormattedCharSequence.forward(I18n.get("abilities.reignofnether.build_road.tooltip4",
                    RoadType.PAVED.woodPerLength, RoadType.PAVED.orePerLength), Style.EMPTY),
                FormattedCharSequence.forward(I18n.get("abilities.reignofnether.build_road.tooltip5",
                    RoadType.ADVANCED.woodPerLength, RoadType.ADVANCED.orePerLength), Style.EMPTY)
            ),
            this,
            placement
        );
    }

    @Override
    public void use(Level level, BuildingPlacement buildingUsing, BlockPos targetBp) {
        if (level instanceof ServerLevel serverLevel)
            RoadServerEvents.tryBuildRoad(serverLevel, buildingUsing.ownerName, buildingUsing.originPos, targetBp, type);
    }
}
