package com.solegendary.reignofnether.rampart;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

// A segment of rampart (a few columns of wall) or a gate, as a building: it has health, units attack it and it
// collapses when too damaged. Its shape isn't a structure file but a list of columns (RampartShapes), so walls can
// follow any line and the terrain.
public class RampartBuilding extends Building {

    public enum Kind { PALISADE, STONE, WOODEN_GATE, IRON_GATE, TOWER }

    public final Kind kind;

    public RampartBuilding(Kind kind, String buildingName, double maxHealth, Block portraitBlock, String iconTexture) {
        super("rampart_" + kind.name().toLowerCase(), ResourceCost.Building(0, 0, 0, 0), false);
        this.kind = kind;
        this.name = buildingName;
        this.portraitBlock = portraitBlock;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", iconTexture);
        this.maxHealth = maxHealth;
        this.capturable = false;
        this.invulnerable = false;
        this.explodeChance = 0.1f;
        this.abilities.clear(); // no "build a road" button on walls
    }

    public boolean isGate() {
        return kind == Kind.WOODEN_GATE || kind == Kind.IRON_GATE;
    }

    // default height of a column of this kind (stone walls have a row of crenels on top)
    public int columnHeight(int x, int z) {
        return switch (kind) {
            case PALISADE -> 3 + (int) Math.floorMod(Mth.getSeed(x, 21, z) >> 16, 2L);
            case STONE -> 5;
            case WOODEN_GATE -> 4;
            case IRON_GATE -> 5;
            case TOWER -> 8;
        };
    }

    public boolean isTower() {
        return kind == Kind.TOWER;
    }

    public Faction getFaction() {
        return Factions.NEUTRAL;
    }

    // in the workers' build menu: starts drawing a rampart instead of placing a building
    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        String name = net.minecraft.network.chat.Component.translatable("buildings.reignofnether." +
            com.solegendary.reignofnether.api.ReignOfNetherRegistries.BUILDING.getKey(this).getPath()).getString();
        String cost = isGate()
            ? RampartLine.woodCost(this, 1) + " bois" + (RampartLine.oreCost(this, 1) > 0 ? ", " + RampartLine.oreCost(this, 1) + " minerai" : "")
            : isTower() ? RampartLine.woodCost(this, 1) + " bois, " + RampartLine.oreCost(this, 1) + " minerai"
            : RampartLine.woodCost(this, 1) + " bois" + (RampartLine.oreCost(this, 1) > 0 ? ", " + RampartLine.oreCost(this, 1) + " minerai" : "") + " par bloc de long";
        List<net.minecraft.util.FormattedCharSequence> tooltip = List.of(
            net.minecraft.util.FormattedCharSequence.forward(name, net.minecraft.network.chat.Style.EMPTY.withBold(true)),
            net.minecraft.util.FormattedCharSequence.forward(cost + " — " + (int) maxHealth + " PV par morceau", net.minecraft.network.chat.Style.EMPTY),
            net.minecraft.util.FormattedCharSequence.forward(isTower()
                ? "Tour de 3 x 3 : un clic la pose, aussi sur un mur. 2 unités peuvent s'y installer et tirer d'en haut."
                : isGate()
                ? "Porte de 5 blocs : cliquez où elle commence, puis dans sa direction. S'ouvre pour vos unités."
                : "Cliquez où il commence, puis où il finit (Maj : continuer). Clic droit pour arrêter.", net.minecraft.network.chat.Style.EMPTY));
        BuildingPlaceButton button = new BuildingPlaceButton(name, icon, hotkey,
            () -> RampartClientEvents.isPlacing(this), () -> false, () -> true, tooltip, this);
        button.onLeftClick = () -> RampartClientEvents.startPlacing(this);
        return button;
    }

    @Override
    public ArrayList<BuildingBlock> getRelativeBlockData(LevelAccessor level) {
        return new ArrayList<>();
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        List<RampartShapes.Column> columns = RampartShapes.get(level, pos);
        if (columns == null || columns.isEmpty())
            columns = List.of(new RampartShapes.Column(pos.getX(), pos.getY(), pos.getZ(), 1));
        return new BuildingPlacement(this, level, pos, Rotation.NONE, ownerName, getBlocks(columns), false);
    }

    static boolean isTowerCentre(RampartShapes.Column c, List<RampartShapes.Column> columns) {
        RampartShapes.Column centre = columns.get(columns.size() / 2);
        return c.x() == centre.x() && c.z() == centre.z();
    }

    public static Direction.Axis getAxis(List<RampartShapes.Column> columns) {
        RampartShapes.Column a = columns.get(0), b = columns.get(columns.size() - 1);
        return Math.abs(b.x() - a.x()) >= Math.abs(b.z() - a.z()) ? Direction.Axis.X : Direction.Axis.Z;
    }

    public ArrayList<BuildingBlock> getBlocks(List<RampartShapes.Column> columns) {
        ArrayList<BuildingBlock> blocks = new ArrayList<>();
        Direction.Axis axis = getAxis(columns);
        for (RampartShapes.Column c : columns) {
            for (int h = 1; h <= c.height(); h++) {
                BlockState state = switch (kind) {
                    case PALISADE -> h < c.height() ? BlockRegistrar.PALISADE.get().defaultBlockState() : BlockRegistrar.PALISADE_TOP.get().defaultBlockState();
                    case STONE -> h < c.height() || Math.floorMod(c.x() + c.z(), 2) == 0 ? BlockRegistrar.STONE_RAMPART.get().defaultBlockState() : null;
                    case WOODEN_GATE -> BlockRegistrar.WOODEN_GATE.get().defaultBlockState().setValue(RampartGateBlock.AXIS, axis);
                    case IRON_GATE -> BlockRegistrar.IRON_GATE.get().defaultBlockState().setValue(RampartGateBlock.AXIS, axis);
                    // a 3 x 3 stone tower, crenels round the top and a spot for the garrison in the middle
                    case TOWER -> h < c.height() || (!isTowerCentre(c, columns) && Math.floorMod(c.x() + c.z(), 2) == 0)
                        ? BlockRegistrar.STONE_RAMPART.get().defaultBlockState() : null;
                };
                if (state != null)
                    blocks.add(new BuildingBlock(new BlockPos(c.x(), c.y() + h, c.z()), state));
            }
        }
        return blocks;
    }
}
