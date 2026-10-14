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
// - quarries: outcrops of ore spread over the map at regular intervals; mined ore grows back slowly
// - ruins with a treasure, spread the same way: plain blocks until a player's unit comes close, then a building to
//   destroy for a good amount of resources
// - wild herds: groups of animals (no more lone ones) that walk small migration circles around their home, move away
//   from towns, and regrow slowly around the same place when hunted
public class MapFeaturesServerEvents {

    public static final String HERD_TAG = "rtsHerd";
    public static final String RUINS_OWNER = "Ruins";

    // quarries and ruins: one per square of the map, at a fixed spot in it (the middle of the chunk holding that spot)
    public static final int QUARRY_CELL = 128;
    public static final int RUIN_CELL = 176;
    public static final String[] QUARRY_MODELS = { "quarry_1", "quarry_2" }; // structures; add more models here
    public static final int QUARRY_REGROW = 20 * 45; // one mined ore block grows back this often
    public static final int RUIN_DISCOVER_RANGE = 40; // a player's unit this close turns the ruins into a building
    public static final int RUIN_LOOT_MIN = 150, RUIN_LOOT_MAX = 300; // of each resource
    public static final int LOOT_RANGE = 40;

    // every resource runs out after a while
    public static final int QUARRY_REGROW_TOTAL = 30; // ore blocks a quarry grows back in all; then it is spent
    public static final int HERD_REGROW_TOTAL = 10; // animals a herd grows back in all; it is gone after the last one
    public static final int FISHING_CATCHES = 240; // catches of a fishing spot (5 food each); then the fish are gone

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
    private static boolean herdsChanged = false; // their icons are sent again
    private static boolean sitesChanged = false;

    private static java.util.List<com.solegendary.reignofnether.building.buildings.neutral.Ruin> ruinBuildings() {
        return java.util.List.of(com.solegendary.reignofnether.building.Buildings.RUIN_1, com.solegendary.reignofnether.building.Buildings.RUIN_2);
    }

    // a player (not barbarians, ruins, survival enemies or neutral)
    public static boolean isPlayerName(String owner) {
        return !owner.isBlank() && !owner.equals(RUINS_OWNER) &&
            !owner.equals(com.solegendary.reignofnether.barbarian.BarbarianServerEvents.OWNER_NAME) &&
            !owner.equals(com.solegendary.reignofnether.survival.SurvivalServerEvents.ENEMY_OWNER_NAME);
    }
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

    // a port's catch: true while the spot has fish; the spot is gone once emptied
    public static boolean takeCatch(BlockPos spot, String owner) {
        if (serverLevel == null || !data().fishingSpots.contains(spot))
            return false;
        int left = data().fishingCatchesLeft.getOrDefault(spot.asLong(), FISHING_CATCHES) - 1;
        if (left <= 0) {
            data().fishingSpots.remove(spot);
            data().fishingCatchesLeft.remove(spot.asLong());
            FishingSpotsClientboundPacket.sync(data().fishingSpots);
            com.solegendary.reignofnether.hud.HudClientboundPacket.showTempMessageI18n(owner, "Un coin de pêche est épuisé : plus de poisson pour ce port.");
        } else {
            data().fishingCatchesLeft.put(spot.asLong(), left);
        }
        data().setDirty();
        return true;
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
        data().fishingCatchesLeft.put(spot.asLong(), FISHING_CATCHES);
        data().setDirty();
        FishingSpotsClientboundPacket.sync(data().fishingSpots);
    }

    // ---- quarries and ruins -------------------------------------------------------------------------------------

    // the fixed spot of a square of the map, if it falls in this chunk: {x, z, seed}
    @Nullable
    private static long[] gridSpotInChunk(ChunkPos cp, int cell, int salt) {
        for (int cellX = Math.floorDiv(cp.getMinBlockX(), cell); cellX <= Math.floorDiv(cp.getMaxBlockX(), cell); cellX++)
            for (int cellZ = Math.floorDiv(cp.getMinBlockZ(), cell); cellZ <= Math.floorDiv(cp.getMaxBlockZ(), cell); cellZ++) {
                Random rand = new Random(Mth.getSeed(cellX, salt, cellZ));
                int x = cellX * cell + 16 + rand.nextInt(cell - 32);
                int z = cellZ * cell + 16 + rand.nextInt(cell - 32);
                if (x >= cp.getMinBlockX() && x <= cp.getMaxBlockX() && z >= cp.getMinBlockZ() && z <= cp.getMaxBlockZ())
                    return new long[] { x, z, rand.nextLong() };
            }
        return null;
    }

    // dry, fairly flat ground, not in a tree
    private static boolean isSiteGround(ServerLevel level, BlockPos ground) {
        BlockState bs = level.getBlockState(ground);
        return bs.getFluidState().isEmpty() && level.getBlockState(ground.above()).getFluidState().isEmpty() &&
            !bs.is(net.minecraft.tags.BlockTags.LEAVES) && !bs.is(net.minecraft.tags.BlockTags.LOGS) &&
            WaveSpawner.getYVariance(level, ground, 5) < 3;
    }

    // trees and plants above a site go
    private static void clearAbove(ServerLevel level, int x0, int z0, int x1, int z1, int fromY) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = fromY; y < fromY + 16; y++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    BlockState bs = level.getBlockState(bp);
                    if (bs.is(net.minecraft.tags.BlockTags.LEAVES) || bs.is(net.minecraft.tags.BlockTags.LOGS) ||
                        (bs.canBeReplaced() && bs.getFluidState().isEmpty() && !bs.isAir()))
                        level.setBlock(bp, Blocks.AIR.defaultBlockState(), 2);
                }
    }

    private static void placeQuarry(ServerLevel level, ChunkPos cp, long seed) {
        String model = QUARRY_MODELS[new Random(seed).nextInt(QUARRY_MODELS.length)];
        List<com.solegendary.reignofnether.building.BuildingBlock> blocks =
            com.solegendary.reignofnether.building.BuildingBlockData.getBuildingBlocksFromNbt(model, level);
        if (blocks.isEmpty())
            return;
        int sx = 0, sz = 0;
        for (var bb : blocks) {
            sx = Math.max(sx, bb.getBlockPos().getX() + 1);
            sz = Math.max(sz, bb.getBlockPos().getZ() + 1);
        }
        BlockPos ground = groundAt(level, cp.getMiddleBlockX(), cp.getMiddleBlockZ());
        if (!isSiteGround(level, ground))
            return;
        BlockPos origin = new BlockPos(cp.getMiddleBlockX() - sx / 2, ground.getY(), cp.getMiddleBlockZ() - sz / 2);
        clearAbove(level, origin.getX(), origin.getZ(), origin.getX() + sx - 1, origin.getZ() + sz - 1, ground.getY() + 1);
        MapFeaturesSaveData.Quarry quarry = new MapFeaturesSaveData.Quarry();
        quarry.centre = new BlockPos(cp.getMiddleBlockX(), ground.getY(), cp.getMiddleBlockZ());
        for (var bb : blocks) {
            BlockState state = bb.getBlockState();
            if (state.isAir() && bb.getBlockPos().getY() == 0)
                continue; // the bottom row sits in the ground: no holes around the outcrop
            BlockPos pos = origin.offset(bb.getBlockPos());
            level.setBlock(pos, state, 2);
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if (id.endsWith("_ore")) {
                quarry.orePositions.add(pos.asLong());
                quarry.oreBlocks.add(id);
            }
        }
        data().quarries.add(quarry);
        data().setDirty();
        sitesChanged = true;
    }

    private static void placeRuin(ServerLevel level, ChunkPos cp, long seed) {
        var ruins = ruinBuildings();
        var building = ruins.get(new Random(seed).nextInt(ruins.size()));
        BlockPos ground = groundAt(level, cp.getMiddleBlockX(), cp.getMiddleBlockZ());
        if (!isSiteGround(level, ground))
            return;
        int sx = 0, sz = 0;
        for (var bb : building.getRelativeBlockData(level)) {
            sx = Math.max(sx, bb.getBlockPos().getX() + 1);
            sz = Math.max(sz, bb.getBlockPos().getZ() + 1);
        }
        BlockPos origin = new BlockPos(cp.getMiddleBlockX() - sx / 2, ground.getY() + 1, cp.getMiddleBlockZ() - sz / 2);
        BuildingPlacement shape = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(building, level, origin,
            net.minecraft.world.level.block.Rotation.NONE, RUINS_OWNER, false);
        if (shape == null)
            return;
        clearAbove(level, origin.getX() - 1, origin.getZ() - 1, origin.getX() + sx, origin.getZ() + sz, origin.getY());
        for (var bb : shape.getBlocks())
            if (!bb.getBlockState().isAir())
                level.setBlock(bb.getBlockPos(), bb.getBlockState(), 2);
        MapFeaturesSaveData.RuinSite site = new MapFeaturesSaveData.RuinSite();
        site.origin = origin;
        site.building = com.solegendary.reignofnether.api.ReignOfNetherRegistries.BUILDING.getKey(building).toString();
        data().ruins.add(site);
        data().setDirty();
        sitesChanged = true;
    }

    @Nullable
    private static net.minecraft.world.entity.LivingEntity getNearestPlayerUnit(BlockPos pos, int range) {
        net.minecraft.world.entity.LivingEntity nearest = null;
        double nearestDist = (double) range * range;
        for (net.minecraft.world.entity.LivingEntity le : com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits()) {
            if (!le.isAlive() || !isPlayerName(((Unit) le).getOwnerName()))
                continue;
            double dist = le.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = le;
            }
        }
        return nearest;
    }

    // ruins become a building when a player's unit comes close (only then: every building keeps its chunk loaded)
    private static void tickRuins(ServerLevel level) {
        for (MapFeaturesSaveData.RuinSite site : data().ruins) {
            if (site.converted || !level.hasChunkAt(site.origin) || getNearestPlayerUnit(site.origin, RUIN_DISCOVER_RANGE) == null)
                continue;
            var building = com.solegendary.reignofnether.api.ReignOfNetherRegistries.BUILDING.get(net.minecraft.resources.ResourceLocation.tryParse(site.building));
            if (building == null)
                continue;
            BuildingPlacement placement = BuildingServerEvents.placeBuilding(building, site.origin,
                net.minecraft.world.level.block.Rotation.NONE, RUINS_OWNER, new int[] {}, false, false, true, true);
            if (placement != null)
                placement.selfBuilding = true; // fills in any block missing since
            site.converted = true;
            data().setDirty();
        }
    }

    // ruins destroyed: their treasure goes to the player whose unit is nearest
    public static void onRuinDestroyed(ServerLevel level, BuildingPlacement placement) {
        if (serverLevel == null)
            return;
        data().ruins.removeIf(site -> site.origin.equals(placement.originPos));
        data().setDirty();
        sitesChanged = true;
        net.minecraft.world.entity.LivingEntity looter = getNearestPlayerUnit(placement.centrePos, LOOT_RANGE);
        if (looter == null)
            return;
        String owner = ((Unit) looter).getOwnerName();
        int food = random.nextInt(RUIN_LOOT_MIN, RUIN_LOOT_MAX + 1);
        int wood = random.nextInt(RUIN_LOOT_MIN, RUIN_LOOT_MAX + 1);
        int ore = random.nextInt(RUIN_LOOT_MIN, RUIN_LOOT_MAX + 1);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.addSubtractResources(
            new com.solegendary.reignofnether.resources.Resources(owner, food, wood, ore));
        com.solegendary.reignofnether.hud.HudClientboundPacket.showTempMessageI18n(owner,
            "Trésor des ruines : +" + food + " nourriture, +" + wood + " bois, +" + ore + " minerai !");
    }

    // a mined ore block of a quarry grows back now and then (where nothing stands)
    private static void tickQuarries(ServerLevel level) {
        long beat = level.getGameTime() / TICK_INTERVAL;
        for (int i = 0; i < data().quarries.size(); i++) {
            MapFeaturesSaveData.Quarry quarry = data().quarries.get(i);
            if ((beat + i * 13L) % (QUARRY_REGROW / TICK_INTERVAL) != 0 || !level.hasChunkAt(quarry.centre) || quarry.orePositions.isEmpty())
                continue;
            if (quarry.regrowLeft <= 0) {
                // spent: once its last ore block is mined, the quarry is gone from the map
                boolean anyOre = false;
                for (int k = 0; k < quarry.orePositions.size(); k++)
                    if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(BlockPos.of(quarry.orePositions.get(k))).getBlock()).toString().equals(quarry.oreBlocks.get(k)))
                        anyOre = true;
                if (!anyOre) {
                    data().quarries.remove(i--);
                    data().setDirty();
                    sitesChanged = true;
                }
                continue;
            }
            for (int attempt = 0; attempt < 4; attempt++) {
                int k = random.nextInt(quarry.orePositions.size());
                BlockPos pos = BlockPos.of(quarry.orePositions.get(k));
                if (!level.getBlockState(pos).isAir() ||
                    !level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(pos)).isEmpty())
                    continue;
                var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.tryParse(quarry.oreBlocks.get(k)));
                level.setBlockAndUpdate(pos, block.defaultBlockState());
                quarry.regrowLeft--;
                data().setDirty();
                break;
            }
        }
    }

    // ---- herds --------------------------------------------------------------------------------------------------

    private static final String[] SPECIES = { "minecraft:cow", "minecraft:cow", "minecraft:sheep", "minecraft:sheep", "minecraft:pig", "minecraft:chicken" };

    private static boolean isNearTown(BlockPos pos, int range) {
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (isPlayerName(b.ownerName) && b.centrePos.distSqr(pos) < (double) range * range)
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
        herdsChanged = true;
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
        List<Herd> spentHerds = new ArrayList<>();
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
                    if (isPlayerName(b.ownerName) && b.centrePos.distSqr(herd.home) < townDist) {
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
                        if (level.getBlockState(ground).getFluidState().isEmpty()) {
                            herd.home = ground;
                            herdsChanged = true;
                        }
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
            // regrowth around the herd (or its home when none is left), while it can; a spent herd is gone with its last animal
            if (herd.regrowLeft <= 0 && herd.members.isEmpty()) {
                spentHerds.add(herd);
                continue;
            }
            if (herd.members.size() < herd.targetSize && herd.regrowLeft > 0) {
                herd.ticksToRegrow -= TICK_INTERVAL;
                if (herd.ticksToRegrow <= 0) {
                    BlockPos at = herd.home;
                    if (!loaded.isEmpty())
                        at = loaded.get(random.nextInt(loaded.size())).blockPosition();
                    if (!herd.members.isEmpty() || !isNearTown(herd.home, HERD_TOWN_DISTANCE / 2)) {
                        spawnMember(level, herd, at);
                        herd.regrowLeft--;
                    }
                    herd.ticksToRegrow = herd.members.isEmpty() ? REGROW_EMPTY : REGROW_INTERVAL;
                    data().setDirty();
                }
            }
        }
        if (!spentHerds.isEmpty()) {
            data().herds.removeAll(spentHerds);
            data().setDirty();
            herdsChanged = true;
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
            long[] quarrySpot = gridSpotInChunk(cp, QUARRY_CELL, 131);
            if (quarrySpot != null)
                placeQuarry(serverLevel, cp, quarrySpot[2]);
            long[] ruinSpot = gridSpotInChunk(cp, RUIN_CELL, 157);
            if (ruinSpot != null && (quarrySpot == null))
                placeRuin(serverLevel, cp, ruinSpot[2]);
            scanForHerd(serverLevel, cp);
        }
        tickHerds(serverLevel);
        tickQuarries(serverLevel);
        tickRuins(serverLevel);
        if (sitesChanged && serverLevel.getGameTime() % 100 < TICK_INTERVAL) {
            sitesChanged = false;
            SitesClientboundPacket.sync(data);
        }
        if (herdsChanged && serverLevel.getGameTime() % 100 < TICK_INTERVAL) {
            herdsChanged = false;
            HerdsClientboundPacket.sync(data.herds);
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (serverLevel != null && evt.getEntity() instanceof ServerPlayer player) {
            FishingSpotsClientboundPacket.sync(player, data().fishingSpots);
            HerdsClientboundPacket.sync(player, data().herds);
            SitesClientboundPacket.sync(player, data());
        }
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
