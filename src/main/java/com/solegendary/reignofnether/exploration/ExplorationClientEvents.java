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
    private static final int TOP_ABOVE_GROUND = 3; // a cloud is at least this tall above the lowest ground
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
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !isActive() || isShadowPass())
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

    // lowest and highest ground of a chunk (trees included), packed as min << 16 | max; MIN_VALUE if unknown
    // Oculus (shaders) also runs this event while drawing its shadow map: skip it there, clouds cast no shadow.
    // Looked up by name so the mod still works without Oculus.
    private static java.lang.reflect.Method irisInstance = null, irisShadowPass = null;
    private static boolean irisChecked = false;

    private static boolean isShadowPass() {
        if (!irisChecked) {
            irisChecked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisInstance = api.getMethod("getInstance");
                irisShadowPass = api.getMethod("isRenderingShadowPass");
            } catch (Throwable ignored) {
                irisInstance = null;
            }
        }
        if (irisInstance == null)
            return false;
        try {
            return (Boolean) irisShadowPass.invoke(irisInstance.invoke(null));
        } catch (Throwable e) {
            return false;
        }
    }

    private static int height(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        if (heights.containsKey(key))
            return heights.get(key);
        if (MC.level == null || !MC.level.getChunkSource().hasChunk(cx, cz))
            return Integer.MIN_VALUE;
        LevelChunk chunk = MC.level.getChunk(cx, cz);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        if (max <= MC.level.getMinBuildHeight())
            return Integer.MIN_VALUE; // empty chunk data: try again later
        int packed = ((min + 1024) << 16) | (max + 1024);
        heights.put(key, packed);
        return packed;
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

        // average ground for chunks whose terrain the client doesn't have
        long sum = 0;
        int known = 0;
        for (int dx = -nearRadius; dx <= nearRadius; dx++)
            for (int dz = -nearRadius; dz <= nearRadius; dz++) {
                int hp = height(camCx + dx, camCz + dz);
                if (hp != Integer.MIN_VALUE) { sum += (hp & 0xFFFF) - 1024; known++; }
            }
        int fallback = known > 0 ? (int) (sum / known) : 70;

        BufferBuilder solid = new BufferBuilder(1 << 20);
        solid.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int solidCount = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius)
                    continue;
                int cx = camCx + dx, cz = camCz + dz;
                if (!isUnexplored(cx, cz))
                    continue;
                // the cloud sits on the ground: from just under the lowest ground to just above the trees
                int hp = height(cx, cz);
                int y0, y1;
                if (hp == Integer.MIN_VALUE) {
                    y0 = fallback - 4;
                    y1 = fallback + TOP_ABOVE_GROUND;
                } else {
                    y0 = (hp >>> 16) - 1024 - 2;
                    y1 = Math.max((hp & 0xFFFF) - 1024 + 2, y0 + TOP_ABOVE_GROUND);
                }
                float x0 = (cx << 4) - originX, z0 = (cz << 4) - originZ, x1 = x0 + 16, z1 = z0 + 16;
                box(solid, x0, y0, z0, x1, y1, z1, true, true, true, true);
                solidCount++;

                if (dx * dx + dz * dz > nearRadius * nearRadius)
                    continue;
                // a few puffs on top, kept inside the chunk
                Random rand = new Random(cx * 341873128712L + cz * 132897987541L);
                int puffs = 1 + rand.nextInt(3);
                for (int p = 0; p < puffs; p++) {
                    int pw = 4 + rand.nextInt(8), pd = 4 + rand.nextInt(8);
                    float px = x0 + rand.nextInt(17 - pw);
                    float pz = z0 + rand.nextInt(17 - pd);
                    int py = y1 - 1, ph = 1 + rand.nextInt(3);
                    box(solid, px, py, pz, px + pw, py + ph, pz + pd, true, true, true, true);
                }
            }
        }

        hasSolid = upload(solid, solidCount > 0, true);
        hasCurtain = false;
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

}
