package com.solegendary.reignofnether.building.buildings.placements;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;

// The sawmill turns its workers' time into wood: each villager working inside (sent with a right click, like to a
// farm, up to MAX_WORKERS) adds to the progress; every WORK_SECONDS of work gives WOOD_PER_LOAD wood straight to the
// owner. No trees: while it works, smoke rises from the shed and sawdust flies around the saw.
public class SawmillPlacement extends FarmPlacement {

    public static final int MAX_WORKERS = 3;
    public static final int WORK_SECONDS = 12;   // of one worker's time for one load
    public static final int WOOD_PER_LOAD = 15;  // like cutting one log
    private static final int TICK_INTERVAL = 20;

    private int progress = 0; // ticks of work done towards the next load
    private int workers = 0;
    private BlockPos saw = null;

    public SawmillPlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName, ArrayList<BuildingBlock> blocks) {
        super(building, level, originPos, rotation, ownerName, blocks, false);
        for (BuildingBlock bb : blocks)
            if (bb.getBlockState().is(Blocks.STONECUTTER))
                saw = bb.getBlockPos();
    }

    public int getWorkers() {
        return workers;
    }

    private int countWorkers() {
        int n = 0;
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le instanceof WorkerUnit w && w.getGatherResourceGoal() != null && w.getGatherResourceGoal().getTargetFarm() == this
                    && isPosInsideBuilding(le.getOnPos(), 1))
                n++;
        return Math.min(n, MAX_WORKERS);
    }

    @Override
    public void tick(Level tickLevel) {
        super.tick(tickLevel);
        if (!(tickLevel instanceof ServerLevel level) || !isBuilt || tickAge % TICK_INTERVAL != 0)
            return;
        workers = countWorkers();
        if (workers == 0)
            return;
        progress += workers * TICK_INTERVAL;
        if (progress >= WORK_SECONDS * 20) {
            progress -= WORK_SECONDS * 20;
            ResourcesServerEvents.addSubtractResources(new Resources(ownerName, 0, WOOD_PER_LOAD, 0));
        }
        BlockPos at = saw != null ? saw : centrePos;
        for (ServerPlayer p : level.players()) {
            level.sendParticles(p, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, at.getX() + 0.5, maxCorner.getY() + 1.0, at.getZ() + 0.5,
                1, 0.2, 0.1, 0.2, 0.01);
            level.sendParticles(p, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()), true,
                at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, 4 + 2 * workers, 0.3, 0.1, 0.3, 0.1);
        }
    }
}
