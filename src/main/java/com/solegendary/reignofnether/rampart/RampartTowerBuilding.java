package com.solegendary.reignofnether.rampart;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

// A 3 x 3 stone tower set into a rampart: its garrison stands on top behind the crenels and shoots from there
public class RampartTowerBuilding extends RampartBuilding implements GarrisonableBuildingAddon {

    public static final int CAPACITY = 2;

    public RampartTowerBuilding(String buildingName, double maxHealth, Block portraitBlock, String iconTexture) {
        super(Kind.TOWER, buildingName, maxHealth, portraitBlock, iconTexture);
        setActiveAddon(GarrisonableBuildingAddon.class, this, true);
    }

    public int getAttackRange() { return 22; }

    public int getExternalAttackRangeBonus() { return 8; }

    public int getCapacity() { return CAPACITY; }

    @Nullable
    private RampartShapes.Column getCentre(BuildingPlacement placement) {
        List<RampartShapes.Column> columns = RampartShapes.get(placement.getLevel(), placement.originPos);
        return columns == null || columns.isEmpty() ? null : columns.get(columns.size() / 2);
    }

    // on top, in the middle
    @Nullable
    public BlockPos getEntryPosition(BuildingPlacement placement) {
        RampartShapes.Column c = getCentre(placement);
        return c == null ? null : new BlockPos(c.x(), c.y() + c.height(), c.z());
    }

    // on the ground next to the tower, on a side that isn't wall
    @Nullable
    public BlockPos getExitPosition(BuildingPlacement placement) {
        RampartShapes.Column c = getCentre(placement);
        if (c == null)
            return null;
        int[][] sides = { { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 } };
        for (int[] side : sides) {
            BlockPos ground = RampartLine.getGround(placement.getLevel(), c.x() + side[0], c.z() + side[1]);
            if (ground != null && !BuildingUtils.isPosInsideAnyBuilding(placement.getLevel().isClientSide(), ground.above()))
                return ground.above();
        }
        return new BlockPos(c.x() + 2, c.y() + 1, c.z());
    }

    // the floor and crenels on top stay, so the garrison isn't left floating
    @Override
    public boolean canDestroyBlock(BlockPos relativeBp, BuildingPlacement placement) {
        RampartShapes.Column c = getCentre(placement);
        return c == null || relativeBp.getY() < c.height() - 1;
    }
}
