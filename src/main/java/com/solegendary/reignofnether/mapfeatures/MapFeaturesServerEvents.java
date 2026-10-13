package com.solegendary.reignofnether.mapfeatures;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.mapfeatures.MapFeaturesSaveData.Herd;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.survival.spawners.WaveSpawner;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

// Things placed on the map the first time each chunk is loaded:
// - fishing spots: bubbles on deep water near the shore of rivers, lakes and seas; a Port is built next to one
// - wild herds: groups of animals (no more lone ones) that walk small migration circles around their home, move away
//   from towns, and regrow slowly around the same place when hunted
public class MapFeaturesServerEvents {

    public static final String HERD_TAG = "rtsHerd";

    private static final int TICK_INTERVAL = 20;
    private static final int CHUNKS_PER_TICK = 6;

    public static final int FISHING_CHUNK_CHANCE = 4; // one chunk in N may have a fishing spot
    public static final int FISHING_SPACING = 40;

    // herds are spread over the whole map at regular intervals, like structures: one per square of HERD_CELL blocks,
    // somewhere inside it, so players choose where to settle between forests, quarries and herds
    public static final int HERD_CELL = 80;
    public static final int HERD_SPACING = 40; // at least this far apart (neighbouring squares)
    public static final int HERD_TOWN_DISTANCE = 48; // herds keep this far from players' buildings
    public static final int HERD_TOWN_STEP = 6; // how far a herd's home moves away from a town each migration step
    public static final int MIGRATION_INTERVAL = 20 * 30; // the herd heads to the next point of its circle
    public static final int MIGRATION_POINTS = 8;
    public static final int REGROW_INTERVAL = 20 * 90; // one animal back while the herd is short
    public static final int REGROW_EMPTY = 20 * 60 * 5; // a herd hunted to the last animal comes back after this

    private static final Queue<Long> pendingChunks = new ConcurrentLinkedQueue<>();
    private static final Random random = new Random();
    private static ServerLevel serverLevel = null;
    private static int ticks = 0;

    private static MapFeaturesSaveData data() {
        return MapFeaturesSaveData.getInstance(serverLevel);
    }

    // ---- fishing spots ------------------------------------------------------------------------------------------

    public static List<BlockPos> getFishingSpots(boolean isClientSide) {
        if (isClientSide)
            return FishingClientEvents.getSpots();
        return serverLevel != null ? data().fishingSpots : List.of();
    }

    @Nullable
    public static BlockPos getNearestFishingSpot(boolean isClientSide, double x, double z, double range) {
        BlockPos nearest = null;
        double nearestDist = range * range;
        for (BlockPos spot : getFishingSpots(isClientSide)) {
            double dx = spot.getX() + 0.5 - x, dz = spot.getZ() + 0.5 - z;
            if (dx * dx + dz * dz <= nearestDist) {
                nearestDist = dx * dx + dz * dz;
                nearest = spot;
            }
        }
        return nearest;
    }

    private static boolean isWater(BlockState bs) {
        return bs.getFluidState().is(FluidTags.WATER) && bs.getFluidState().isSource();
    }

    // top block of a column (the water's surface on water)
    private static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
    }

    private static void scanForFishingSpot(ServerLevel level, ChunkPos cp) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int lx = 2; lx <= 13; lx++) {
            for (int lz = 2; lz <= 13; lz++) {
                BlockPos top = surface(level, cp.getMinBlockX() + lx, cp.getMinBlockZ() + lz);
                if (!isWater(level.getBlockState(top)) || !isWater(level.getBlockState(top.below())))
                    continue; // at least 2 deep
                boolean shore = false;
                for (int d = 2; d <= 4 && !shore; d++)
                    for (int[] dir : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { -1, 1 }, { 1, -1 }, { -1, -1 } }) {
                        BlockPos land = surface(level, top.getX() + dir[0] * d, top.getZ() + dir[1] * d);
                        if (land.getY() >= top.getY() - 1 && level.getBlockState(land).getFluidState().isEmpty()) {
                            shore = true;
                            break;
                        }
                    }
                if (shore)
                    candidates.add(top);
            }
        }
        if (candidates.isEmpty())
            return;
        BlockPos spot = candidates.get(new Random(cp.toLong()).nextInt(candidates.size()));
        for (BlockPos other : data().fishingSpots)
            if (other.distSqr(spot) < FISHING_SPACING * FISHING_SPACING)
                return;
        data().fishingSpots.add(spot);
        data().setDirty();
        FishingSpotsClientboundPacket.sync(data().fishingSpots);
    }

    // ---- herds --------------------------------------------------------------------------------------------------

    private static final String[] SPECIES = { "minecraft:cow", "minecraft:cow", "minecraft:sheep", "minecraft:sheep", "minecraft:pig", "minecraft:chicken" };

    private static boolean isNearTown(BlockPos pos, int range) {
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (!b.ownerName.isBlank() && !b.ownerName.equals(com.solegendary.reignofnether.barbarian.BarbarianServerEvents.OWNER_NAME) &&
                b.centrePos.distSqr(pos) < (double) range * range)
                return true;
        return false;
    }

    // dry, fairly flat grassland
    private static boolean isHerdGround(ServerLevel level, BlockPos ground) {
        BlockState bs = level.getBlockState(ground);
        return (bs.is(Blocks.GRASS_BLOCK) || bs.is(Blocks.DIRT) || bs.is(Blocks.PODZOL) || bs.is(Blocks.COARSE_DIRT)) &&
            level.getBlockState(ground.above()).getFluidState().isEmpty() && WaveSpawner.getYVariance(level, ground, 5) < 3;
    }

    private static BlockPos groundAt(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    @Nullable
    private static Herd createHerd(ServerLevel level, BlockPos ground, long seed) {
        for (Herd other : data().herds)
            if (other.home.distSqr(ground) < (double) HERD_SPACING * HERD_SPACING)
                return null;
        Random rand = new Random(seed);
        Herd herd = new Herd();
        herd.id = data().nextHerdId++;
        herd.species = SPECIES[rand.nextInt(SPECIES.length)];
        herd.home = ground;
        herd.radius = 8 + rand.nextInt(7);
        herd.targetSize = herd.species.equals("minecraft:chicken") ? 5 + rand.nextInt(4) : 4 + rand.nextInt(4);
        herd.step = rand.nextInt(MIGRATION_POINTS);
        data().herds.add(herd);
        for (int i = 0; i < herd.targetSize; i++)
            spawnMember(level, herd, ground);
        data().setDirty();
        return herd;
    }

    private static void spawnMember(ServerLevel level, Herd herd, BlockPos near) {
        EntityType<?> type = EntityType.byString(herd.species).orElse(null);
        if (type == null)
            return;
        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob))
            return;
        BlockPos ground = groundAt(level, near.getX() + random.nextInt(-3, 4), near.getZ() + random.nextInt(-3, 4));
        if (!level.getBlockState(ground).getFluidState().isEmpty())
            ground = near;
        mob.moveTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, random.nextFloat() * 360, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), MobSpawnType.NATURAL, null, null);
        mob.setPersistenceRequired();
        mob.getPersistentData().putInt(HERD_TAG, herd.id);
        if (level.addFreshEntity(mob))
            herd.members.add(mob.getUUID());
    }

    // the herd of a square of the map stands at a point picked from the square's position (always the same one)
    private static void scanForHerd(ServerLevel level, ChunkPos cp) {
        for (int cellX = Math.floorDiv(cp.getMinBlockX(), HERD_CELL); cellX <= Math.floorDiv(cp.getMaxBlockX(), HERD_CELL); cellX++) {
            for (int cellZ = Math.floorDiv(cp.getMinBlockZ(), HERD_CELL); cellZ <= Math.floorDiv(cp.getMaxBlockZ(), HERD_CELL); cellZ++) {
                Random rand = new Random(Mth.getSeed(cellX, 113, cellZ));
                int x = cellX * HERD_CELL + 12 + rand.nextInt(HERD_CELL - 24);
                int z = cellZ * HERD_CELL + 12 + rand.nextInt(HERD_CELL - 24);
                if (x < cp.getMinBlockX() || x > cp.getMaxBlockX() || z < cp.getMinBlockZ() || z > cp.getMaxBlockZ())
                    continue;
                // that spot, or the nearest good grass in its chunk
                BlockPos best = null;
                double bestDist = Double.MAX_VALUE;
                for (int lx = 0; lx < 16; lx += 3)
                    for (int lz = 0; lz < 16; lz += 3) {
                        BlockPos ground = groundAt(level, cp.getMinBlockX() + lx, cp.getMinBlockZ() + lz);
                        double dist = ground.distToCenterSqr(x, ground.getY(), z);
                        if (dist < bestDist && isHerdGround(level, ground)) {
                            bestDist = dist;
                            best = ground;
                        }
                    }
                if (best != null)
                    createHerd(level, best, rand.nextLong());
            }
        }
    }

    private static void tickHerds(ServerLevel level) {
        long beat = level.getGameTime() / TICK_INTERVAL;
        for (Herd herd : data().herds) {
            if (!level.hasChunkAt(herd.home))
                continue;
            List<Mob> loaded = new ArrayList<>();
            for (UUID uuid : new ArrayList<>(herd.members)) {
                Entity e = level.getEntity(uuid);
                if (e != null && !e.isAlive()) {
                    herd.members.remove(uuid);
                    data().setDirty();
                } else if (e instanceof Mob mob) {
                    loaded.add(mob);
                }
            }
            // migration: the next point of the circle; the home moves away from towns
            if ((beat + herd.id * 7L) % (MIGRATION_INTERVAL / TICK_INTERVAL) == 0) {
                BuildingPlacement town = null;
                double townDist = (double) HERD_TOWN_DISTANCE * HERD_TOWN_DISTANCE;
                for (BuildingPlacement b : BuildingServerEvents.getBuildings())
                    if (!b.ownerName.isBlank() && b.centrePos.distSqr(herd.home) < townDist) {
                        townDist = b.centrePos.distSqr(herd.home);
                        town = b;
                    }
                if (town != null) {
                    double dx = herd.home.getX() - town.centrePos.getX(), dz = herd.home.getZ() - town.centrePos.getZ();
                    double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
                    int nx = herd.home.getX() + (int) Math.round(dx / len * HERD_TOWN_STEP);
                    int nz = herd.home.getZ() + (int) Math.round(dz / len * HERD_TOWN_STEP);
                    if (level.hasChunkAt(new BlockPos(nx, 0, nz))) {
                        BlockPos ground = groundAt(level, nx, nz);
                        if (level.getBlockState(ground).getFluidState().isEmpty())
                            herd.home = ground;
                    }
                }
                herd.step = (herd.step + 1) % MIGRATION_POINTS;
                double angle = herd.step * Math.PI * 2 / MIGRATION_POINTS;
                int tx = herd.home.getX() + (int) Math.round(Math.cos(angle) * herd.radius);
                int tz = herd.home.getZ() + (int) Math.round(Math.sin(angle) * herd.radius);
                if (level.hasChunkAt(new BlockPos(tx, 0, tz))) {
                    BlockPos target = groundAt(level, tx, tz);
                    for (Mob mob : loaded)
                        if (!mob.isLeashed() && mob.tickCount - mob.getLastHurtByMobTimestamp() > 200)
                            mob.getNavigation().moveTo(target.getX() + random.nextInt(-3, 4) + 0.5, target.getY() + 1,
                                target.getZ() + random.nextInt(-3, 4) + 0.5, 0.9);
                }
                data().setDirty();
            }
            // regrowth around the herd (or its home when none is left)
            if (herd.members.size() < herd.targetSize) {
                herd.ticksToRegrow -= TICK_INTERVAL;
                if (herd.ticksToRegrow <= 0) {
                    BlockPos at = herd.home;
                    if (!loaded.isEmpty())
                        at = loaded.get(random.nextInt(loaded.size())).blockPosition();
                    if (!herd.members.isEmpty() || !isNearTown(herd.home, HERD_TOWN_DISTANCE / 2))
                        spawnMember(level, herd, at);
                    herd.ticksToRegrow = herd.members.isEmpty() ? REGROW_EMPTY : REGROW_INTERVAL;
                    data().setDirty();
                }
            }
        }
    }

    // ---- events -------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load evt) {
        if (evt.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD)
            pendingChunks.add(evt.getChunk().getPos().toLong());
    }

    // no more lone animals: the only huntable animals are those of the herds
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide() || !(evt.getEntity() instanceof Animal animal) || animal instanceof Unit ||
            com.solegendary.reignofnether.tutorial.TutorialServerEvents.isEnabled()) // the tutorial brings its own pigs
            return;
        if (!animal.getPersistentData().contains(HERD_TAG) && ResourceSources.isHuntableAnimal(animal))
            evt.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent evt) {
        if (serverLevel == null || evt.getEntity().level().isClientSide() || !evt.getEntity().getPersistentData().contains(HERD_TAG))
            return;
        int id = evt.getEntity().getPersistentData().getInt(HERD_TAG);
        for (Herd herd : data().herds)
            if (herd.id == id && herd.members.remove(evt.getEntity().getUUID())) {
                if (herd.ticksToRegrow <= 0)
                    herd.ticksToRegrow = REGROW_INTERVAL;
                data().setDirty();
            }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || serverLevel == null || ++ticks < TICK_INTERVAL)
            return;
        ticks = 0;
        MapFeaturesSaveData data = data();
        for (int i = 0; i < CHUNKS_PER_TICK && !pendingChunks.isEmpty(); i++) {
            long key = pendingChunks.poll();
            if (data.scannedChunks.contains(key))
                continue;
            ChunkPos cp = new ChunkPos(key);
            if (!serverLevel.hasChunk(cp.x, cp.z))
                continue; // looked at again when it loads next time
            data.scannedChunks.add(key);
            data.setDirty();
            if (Math.floorMod(Mth.getSeed(cp.x, 71, cp.z) >> 8, (long) FISHING_CHUNK_CHANCE) == 0)
                scanForFishingSpot(serverLevel, cp);
            scanForHerd(serverLevel, cp);
        }
        tickHerds(serverLevel);
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (serverLevel != null && evt.getEntity() instanceof ServerPlayer player)
            FishingSpotsClientboundPacket.sync(player, data().fishingSpots);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD); // chunks loaded before this stay queued
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        serverLevel = null;
        pendingChunks.clear();
    }
}
