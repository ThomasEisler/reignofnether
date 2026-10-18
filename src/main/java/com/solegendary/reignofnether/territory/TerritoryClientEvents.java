package com.solegendary.reignofnether.territory;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.exploration.ExplorationClientEvents;
import com.solegendary.reignofnether.nation.Nation;
import com.solegendary.reignofnether.nation.NationClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Borders: a thin, slightly see-through line on the ground in the nation's colour, all round each civilisation's
// territory (Territory), and the same border on the minimap. Where two territories touch, both lines run side by
// side. Hidden under the clouds (unexplored land).
public class TerritoryClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final ResourceLocation WHITE = ResourceLocation.parse("forge:textures/white.png");

    public static final float LINE_WIDTH = 0.28f;
    public static final float LINE_ALPHA = 0.45f;
    public static final float INSET = 0.3f; // from the edge of the cell, so two neighbours' lines don't overlap
    public static final int RENDER_DIST = 160;
    private static final int UPDATE_TICKS = 20;

    // a stretch of border along one side of a cell: x or z stays the same (alongZ: x is fixed)
    private record Edge(float x1, float z1, float x2, float z2, boolean alongZ, int colour) { }

    private static List<Edge> edges = new ArrayList<>();
    private static List<int[]> mapBlocks = new ArrayList<>(); // x, z, colour of the blocks just inside each border
    private static int lastSignature = 0;
    private static int ticks = 0;

    public static int colourOf(String owner) {
        Nation nation = NationClientEvents.getNation(owner);
        return nation != null ? nation.colour : PlayerColors.getPlayerDisplayColorHex(owner) & 0xFFFFFF;
    }

    // ---- working out the borders --------------------------------------------------------------------------------

    private static int signature(List<BuildingPlacement> buildings) {
        int h = 17;
        for (BuildingPlacement b : buildings)
            if (Territory.radius(b, PlayerClientEvents::isRTSPlayer) > 0)
                h = h * 31 + b.originPos.hashCode() * 7 + b.ownerName.hashCode() + (b.getBuilding().isCapitol ? 1 : 0);
        return h;
    }

    private static void update() {
        List<BuildingPlacement> buildings = new ArrayList<>(BuildingClientEvents.getBuildings());
        int sig = signature(buildings);
        if (sig == lastSignature)
            return;
        lastSignature = sig;
        Map<Long, String> grid = Territory.grid(buildings, PlayerClientEvents::isRTSPlayer);
        List<Edge> newEdges = new ArrayList<>();
        List<int[]> newMap = new ArrayList<>();
        int c = Territory.CELL;
        grid.forEach((key, owner) -> {
            int cx = (int) (key >> 32), cz = (int) (long) key;
            int colour = colourOf(owner);
            int x0 = cx * c, z0 = cz * c, x1 = x0 + c, z1 = z0 + c;
            if (!owner.equals(grid.get(Territory.cellKey(cx + 1, cz)))) { // east
                newEdges.add(new Edge(x1 - INSET, z0, x1 - INSET, z1, true, colour));
                for (int z = z0; z < z1; z++) newMap.add(new int[] { x1 - 1, z, colour });
            }
            if (!owner.equals(grid.get(Territory.cellKey(cx - 1, cz)))) { // west
                newEdges.add(new Edge(x0 + INSET, z0, x0 + INSET, z1, true, colour));
                for (int z = z0; z < z1; z++) newMap.add(new int[] { x0, z, colour });
            }
            if (!owner.equals(grid.get(Territory.cellKey(cx, cz + 1)))) { // south
                newEdges.add(new Edge(x0, z1 - INSET, x1, z1 - INSET, false, colour));
                for (int x = x0; x < x1; x++) newMap.add(new int[] { x, z1 - 1, colour });
            }
            if (!owner.equals(grid.get(Territory.cellKey(cx, cz - 1)))) { // north
                newEdges.add(new Edge(x0, z0 + INSET, x1, z0 + INSET, false, colour));
                for (int x = x0; x < x1; x++) newMap.add(new int[] { x, z0, colour });
            }
        });
        edges = newEdges;
        mapBlocks = newMap;
        groundCache.clear();
    }

    // for the minimap: each border block that has been explored
    public static List<int[]> getMapBorderBlocks() {
        return mapBlocks;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || MC.level == null || ++ticks < UPDATE_TICKS)
            return;
        ticks = 0;
        update();
    }

    // ---- drawing on the ground ----------------------------------------------------------------------------------

    // ground under the trees: the client only keeps MOTION_BLOCKING up to date (leaves included), so step down
    // through leaves and logs. Remembered per column (the ground hardly changes), forgotten when borders change.
    private static final java.util.Map<Long, Float> groundCache = new java.util.HashMap<>();

    private static float groundY(float x, float z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        long key = net.minecraft.core.BlockPos.asLong(bx, 0, bz);
        Float cached = groundCache.get(key);
        if (cached != null)
            return cached;
        if (!MC.level.hasChunk(bx >> 4, bz >> 4))
            return MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) + 0.03f;
        int y = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
        for (int i = 0; i < 24; i++) {
            var bs = MC.level.getBlockState(new net.minecraft.core.BlockPos(bx, y, bz));
            if (!bs.is(net.minecraft.tags.BlockTags.LEAVES) && !bs.is(net.minecraft.tags.BlockTags.LOGS))
                break;
            y--;
        }
        float ground = y + 1.03f;
        groundCache.put(key, ground);
        return ground;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || MC.level == null || edges.isEmpty())
            return;
        Vec3 cam = evt.getCamera().getPosition();
        PoseStack pose = evt.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        VertexConsumer vc = MC.renderBuffers().bufferSource().getBuffer(RenderType.entityTranslucent(WHITE));
        double maxSq = (double) RENDER_DIST * RENDER_DIST;
        float hw = LINE_WIDTH / 2;
        for (Edge e : edges) {
            float mx = (e.x1() + e.x2()) / 2, mz = (e.z1() + e.z2()) / 2;
            double dx = mx - cam.x, dz = mz - cam.z;
            if (dx * dx + dz * dz > maxSq || !ExplorationClientEvents.isExplored((int) Math.floor(mx), (int) Math.floor(mz)))
                continue;
            float r = ((e.colour() >> 16) & 0xFF) / 255f, g = ((e.colour() >> 8) & 0xFF) / 255f, b = (e.colour() & 0xFF) / 255f;
            // ground height at each end (just inside, so a step in the terrain doesn't lift the whole stretch)
            float y1 = groundY(e.x1() + (e.alongZ() ? 0 : 0.01f), e.z1() + (e.alongZ() ? 0.01f : 0));
            float y2 = groundY(e.x2() - (e.alongZ() ? 0 : 0.01f), e.z2() - (e.alongZ() ? 0.01f : 0));
            if (e.alongZ()) {
                vertex(vc, m, n, e.x1() - hw, y1, e.z1(), r, g, b);
                vertex(vc, m, n, e.x1() - hw, y2, e.z2(), r, g, b);
                vertex(vc, m, n, e.x1() + hw, y2, e.z2(), r, g, b);
                vertex(vc, m, n, e.x1() + hw, y1, e.z1(), r, g, b);
            } else {
                vertex(vc, m, n, e.x1(), y1, e.z1() - hw, r, g, b);
                vertex(vc, m, n, e.x1(), y1, e.z1() + hw, r, g, b);
                vertex(vc, m, n, e.x2(), y2, e.z2() + hw, r, g, b);
                vertex(vc, m, n, e.x2(), y2, e.z2() - hw, r, g, b);
            }
        }
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float r, float g, float b) {
        vc.vertex(m, x, y, z).color(r, g, b, LINE_ALPHA).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }
}
