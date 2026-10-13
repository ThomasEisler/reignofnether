package com.solegendary.reignofnether.mapfeatures;

import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// fishing spots: bubbles popping at the water's surface, now and then a splash
public class FishingClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final Random random = new Random();
    private static final double SHOW_RANGE = 96; // blocks from the camera, sideways
    private static List<BlockPos> spots = new ArrayList<>();

    public static void setSpots(List<BlockPos> newSpots) {
        spots = newSpots;
    }

    public static List<BlockPos> getSpots() {
        return spots;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || MC.level == null || MC.isPaused() || MC.level.getGameTime() % 3 != 0)
            return;
        Vec3 cam = MC.gameRenderer.getMainCamera().getPosition();
        for (BlockPos spot : spots) {
            double dx = spot.getX() - cam.x, dz = spot.getZ() - cam.z;
            if (dx * dx + dz * dz > SHOW_RANGE * SHOW_RANGE)
                continue;
            if (FogOfWarClientEvents.isEnabled() && !FogOfWarClientEvents.isInBrightChunk(spot))
                continue;
            double surface = spot.getY() + 0.9;
            for (int i = 0; i < 3; i++)
                MC.level.addParticle(ParticleTypes.BUBBLE_POP, spot.getX() + 0.5 + random.nextGaussian() * 1.2, surface,
                    spot.getZ() + 0.5 + random.nextGaussian() * 1.2, 0, 0.02, 0);
            if (random.nextInt(4) == 0)
                MC.level.addParticle(ParticleTypes.SPLASH, spot.getX() + 0.5 + random.nextGaussian(), surface + 0.1,
                    spot.getZ() + 0.5 + random.nextGaussian(), 0, 0.1, 0);
        }
    }
}
