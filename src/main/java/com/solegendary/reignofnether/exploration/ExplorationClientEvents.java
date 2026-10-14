package com.solegendary.reignofnether.exploration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;

// Clouds over every chunk this player has not explored yet (see ExplorationServerEvents). They cover everything as
// soon as the player has started (is an RTS player); only the spot choice before starting shows the whole map.
// They reach far beyond the render distance so that far terrain (Distant Horizons) stays hidden too.
// Things inside unexplored chunks (units, icons, minimap) are hidden through FogOfWarClientEvents.isInBrightChunk.
public class ExplorationClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    private static final LongOpenHashSet explored = new LongOpenHashSet();
    private static boolean enabled = true;
    private static int version = 0; // bumped whenever the explored set changes

    // cloud look
    private static final int SLAB_THICKNESS = 5;
    private static final int CLEARANCE = 4;       // blocks between the highest ground of a chunk and its cloud
    private static final int CURTAIN_DEPTH = 16;  // misty veil hanging down along the explored border
    private static final int EXTRA_RADIUS = 4;    // puffy clouds up to this many chunks beyond the render distance
    private static final int FAR_RADIUS = 64;     // flat clouds up to here (1024 blocks), over far terrain

    public static void receive(boolean isEnabled, boolean replace, long[] chunks) {
        enabled = isEnabled;
        if (replace)
            explored.clear();
        for (long c : chunks)
            explored.add(c);
        version++;
    }

    public static boolean isActive() {
        return enabled && MC.level != null && PlayerClientEvents.isRTSPlayer();
    }

    public static boolean isExplored(int blockX, int blockZ) {
        return !isActive() || explored.contains(ChunkPos.asLong(blockX >> 4, blockZ >> 4));
    }

    public static boolean isExplored(BlockPos pos) {
        return isExplored(pos.getX(), pos.getZ());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        explored.clear();
        heights.clear();
        version++;
    }

    // ---------------------------------------------------------------- rendering

    private static final Long2IntOpenHashMap heights = new Long2IntOpenHashMap(); // highest ground per chunk
    private static VertexBuffer solidBuffer = null;
    private static VertexBuffer curtainBuffer = null;
    private static boolean hasSolid = false, hasCurtain = false;
    private static int builtVersion = -1;
    private static long builtCentre = Long.MIN_VALUE;
    private static long lastBuildTime = 0;
    private static int originX = 0, originZ = 0;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !isActive())
            return;
        Vec3 cam = evt.getCamera().getPosition();
        int camCx = (int) Math.floor(cam.x) >> 4, camCz = (int) Math.floor(cam.z) >> 4;
        long centre = ChunkPos.asLong(camCx, camCz);
        long now = System.currentTimeMillis();
        if (builtVersion != version || builtCentre != centre || now - lastBuildTime > 3000) {
            rebuild(camCx, camCz);
            builtVersion = version;
            builtCentre = centre;
            lastBuildTime = now;
        }
        if (!hasSolid && !hasCurtain)
            return;

        float light = Math.max(0.35f, MC.level.getSkyDarken(evt.getPartialTick()));
        PoseStack pose = evt.getPoseStack();
        pose.pushPose();
        pose.translate(originX - cam.x, -cam.y, originZ - cam.z);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(light, light, light, 1f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        if (hasSolid) {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            solidBuffer.bind();
            solidBuffer.drawWithShader(pose.last().pose(), evt.getProjectionMatrix(), GameRenderer.getPositionColorShader());
        }
        if (hasCurtain) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            curtainBuffer.bind();
            curtainBuffer.drawWithShader(pose.last().pose(), evt.getProjectionMatrix(), GameRenderer.getPositionColorShader());
        }
        VertexBuffer.unbind();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        pose.popPose();
    }

    private static int height(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        if (heights.containsKey(key))
            return heights.get(key);
        if (MC.level == null || !MC.level.getChunkSource().hasChunk(cx, cz))
            return Integer.MIN_VALUE;
        LevelChunk chunk = MC.level.getChunk(cx, cz);
        int max = Integer.MIN_VALUE;
        for (int x = 0; x < 16; x += 3)
            for (int z = 0; z < 16; z += 3)
                max = Math.max(max, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
        if (max <= MC.level.getMinBuildHeight())
            return Integer.MIN_VALUE; // empty chunk data: try again later
        heights.put(key, max);
        return max;
    }

    private static boolean isUnexplored(int cx, int cz) {
        return !explored.contains(ChunkPos.asLong(cx, cz));
    }

    private static void rebuild(int camCx, int camCz) {
        if (MC.level == null)
            return;
        int nearRadius = MC.options.getEffectiveRenderDistance() + EXTRA_RADIUS;
        int radius = Math.max(nearRadius, FAR_RADIUS);
        originX = camCx << 4;
        originZ = camCz << 4;

        // cloud base per chunk: highest ground of the chunk and its neighbours, so the sea of clouds stays smooth
        int size = radius * 2 + 3;
        int[][] h = new int[size][size];
        long sum = 0;
        int known = 0;
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++) {
                h[i][j] = height(camCx - radius - 1 + i, camCz - radius - 1 + j);
                if (h[i][j] != Integer.MIN_VALUE) { sum += h[i][j]; known++; }
            }
        int fallback = known > 0 ? (int) (sum / known) + 6 : 80;
        int[][] base = new int[size][size];
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++) {
                int m = Integer.MIN_VALUE;
                for (int di = -1; di <= 1; di++)
                    for (int dj = -1; dj <= 1; dj++) {
                        int a = i + di, b = j + dj;
                        if (a >= 0 && b >= 0 && a < size && b < size)
                            m = Math.max(m, h[a][b]);
                    }
                base[i][j] = (m == Integer.MIN_VALUE ? fallback : m) + CLEARANCE;
            }

        BufferBuilder solid = new BufferBuilder(1 << 20);
        BufferBuilder curtain = new BufferBuilder(1 << 14);
        solid.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        curtain.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int solidCount = 0, curtainCount = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius)
                    continue;
                int cx = camCx + dx, cz = camCz + dz;
                if (!isUnexplored(cx, cz))
                    continue;
                int i = dx + radius + 1, j = dz + radius + 1;
                int y0 = base[i][j], y1 = y0 + SLAB_THICKNESS;
                float x0 = (cx << 4) - originX, z0 = (cz << 4) - originZ, x1 = x0 + 16, z1 = z0 + 16;

                // main slab (sides only where the neighbour isn't an identical cloud)
                boolean n = isUnexplored(cx, cz - 1), s = isUnexplored(cx, cz + 1);
                boolean w = isUnexplored(cx - 1, cz), e = isUnexplored(cx + 1, cz);
                box(solid, x0, y0, z0, x1, y1, z1,
                    !(n && base[i][j - 1] == y0), !(s && base[i][j + 1] == y0),
                    !(w && base[i - 1][j] == y0), !(e && base[i + 1][j] == y0));
                solidCount++;

                // puffs on top (near the camera only), a few may hang over the border a little
                if (dx * dx + dz * dz > nearRadius * nearRadius) {
                    if (!n) { veil(curtain, x0, z0, x1, z0, y0); curtainCount++; }
                    if (!s) { veil(curtain, x0, z1, x1, z1, y0); curtainCount++; }
                    if (!w) { veil(curtain, x0, z0, x0, z1, y0); curtainCount++; }
                    if (!e) { veil(curtain, x1, z0, x1, z1, y0); curtainCount++; }
                    continue;
                }
                Random rand = new Random(cx * 341873128712L + cz * 132897987541L);
                int puffs = 2 + rand.nextInt(3);
                for (int p = 0; p < puffs; p++) {
                    int pw = 4 + rand.nextInt(8), pd = 4 + rand.nextInt(8);
                    float px = x0 + Math.max(-2, Math.min(16 - pw + 2, rand.nextInt(16) - pw / 2));
                    float pz = z0 + Math.max(-2, Math.min(16 - pd + 2, rand.nextInt(16) - pd / 2));
                    int py = y1 - 1, ph = 2 + rand.nextInt(4);
                    box(solid, px, py, pz, px + pw, py + ph, pz + pd, true, true, true, true);
                }

                // misty veil hanging along the explored border
                if (!n) { veil(curtain, x0, z0, x1, z0, y0); curtainCount++; }
                if (!s) { veil(curtain, x0, z1, x1, z1, y0); curtainCount++; }
                if (!w) { veil(curtain, x0, z0, x0, z1, y0); curtainCount++; }
                if (!e) { veil(curtain, x1, z0, x1, z1, y0); curtainCount++; }
            }
        }

        hasSolid = upload(solid, solidCount > 0, true);
        hasCurtain = upload(curtain, curtainCount > 0, false);
    }

    private static boolean upload(BufferBuilder builder, boolean any, boolean isSolid) {
        BufferBuilder.RenderedBuffer rendered = builder.end();
        if (!any) {
            rendered.release();
            return false;
        }
        VertexBuffer vb = isSolid ? solidBuffer : curtainBuffer;
        if (vb == null) {
            vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            if (isSolid) solidBuffer = vb; else curtainBuffer = vb;
        }
        vb.bind();
        vb.upload(rendered);
        VertexBuffer.unbind();
        return true;
    }

    private static final int R = 246, G = 248, B = 252;

    private static void quad(BufferBuilder b, float shade, int alpha, float[]... v) {
        int r = (int) (R * shade), g = (int) (G * shade), bl = (int) (B * shade);
        for (float[] p : v)
            b.vertex(p[0], p[1], p[2]).color(r, g, bl, alpha).endVertex();
    }

    private static void box(BufferBuilder b, float x0, float y0, float z0, float x1, float y1, float z1,
                            boolean north, boolean south, boolean west, boolean east) {
        quad(b, 1.0f, 255, new float[]{x0, y1, z0}, new float[]{x0, y1, z1}, new float[]{x1, y1, z1}, new float[]{x1, y1, z0});
        quad(b, 0.74f, 255, new float[]{x0, y0, z0}, new float[]{x1, y0, z0}, new float[]{x1, y0, z1}, new float[]{x0, y0, z1});
        if (north) quad(b, 0.9f, 255, new float[]{x0, y0, z0}, new float[]{x0, y1, z0}, new float[]{x1, y1, z0}, new float[]{x1, y0, z0});
        if (south) quad(b, 0.9f, 255, new float[]{x0, y0, z1}, new float[]{x1, y0, z1}, new float[]{x1, y1, z1}, new float[]{x0, y1, z1});
        if (west)  quad(b, 0.83f, 255, new float[]{x0, y0, z0}, new float[]{x0, y0, z1}, new float[]{x0, y1, z1}, new float[]{x0, y1, z0});
        if (east)  quad(b, 0.83f, 255, new float[]{x1, y0, z0}, new float[]{x1, y1, z0}, new float[]{x1, y1, z1}, new float[]{x1, y0, z1});
    }

    // vertical quad from the cloud's underside down, fading out
    private static void veil(BufferBuilder b, float xa, float za, float xb, float zb, int top) {
        int bottom = top - CURTAIN_DEPTH;
        int r = (int) (R * 0.88f), g = (int) (G * 0.88f), bl = (int) (B * 0.9f);
        b.vertex(xa, top, za).color(r, g, bl, 210).endVertex();
        b.vertex(xb, top, zb).color(r, g, bl, 210).endVertex();
        b.vertex(xb, bottom, zb).color(r, g, bl, 0).endVertex();
        b.vertex(xa, bottom, za).color(r, g, bl, 0).endVertex();
    }
}
