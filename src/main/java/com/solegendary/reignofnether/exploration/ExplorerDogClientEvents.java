package com.solegendary.reignofnether.exploration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.units.villagers.ExplorerUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

// The Explorer's dog: a wolf drawn only on screen (no real entity) that trots along the path its explorer walked,
// a little behind, and sits down when the explorer stops.
public class ExplorerDogClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final double FOLLOW_DIST = 1.6;   // blocks behind the explorer, along its path
    private static final double TRAIL_STEP = 0.25;   // a trail point every this many blocks
    private static final int SIT_AFTER_TICKS = 50;

    private static class Dog {
        Wolf wolf;
        final ArrayDeque<Vec3> trail = new ArrayDeque<>(); // newest first
        Vec3 pos, oldPos;
        int stillTicks = 0;
    }

    private static final Map<Integer, Dog> dogs = new HashMap<>();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        if (MC.level == null || MC.isPaused()) {
            if (MC.level == null) dogs.clear();
            return;
        }
        Set<Integer> alive = new HashSet<>();
        for (LivingEntity le : UnitClientEvents.getAllUnits()) {
            if (!(le instanceof ExplorerUnit explorer) || !explorer.isAlive())
                continue;
            alive.add(explorer.getId());
            Dog dog = dogs.computeIfAbsent(explorer.getId(), id -> newDog(explorer));
            if (dog.wolf.level() != MC.level)
                dog.wolf = newWolf();
            tickDog(dog, explorer);
        }
        dogs.keySet().retainAll(alive);
    }

    private static Wolf newWolf() {
        Wolf wolf = new Wolf(EntityType.WOLF, MC.level);
        wolf.setTame(true);
        return wolf;
    }

    private static Dog newDog(ExplorerUnit explorer) {
        Dog dog = new Dog();
        dog.wolf = newWolf();
        Vec3 behind = explorer.position().add(Vec3.directionFromRotation(0, explorer.getYRot()).scale(-FOLLOW_DIST));
        dog.pos = dog.oldPos = behind;
        dog.trail.add(explorer.position());
        return dog;
    }

    private static void tickDog(Dog dog, ExplorerUnit explorer) {
        Vec3 here = explorer.position();
        if (dog.trail.isEmpty() || dog.trail.peekFirst().distanceTo(here) >= TRAIL_STEP)
            dog.trail.addFirst(here);
        if (here.distanceTo(dog.pos) > 12) { // teleported or just spawned far away
            dog.trail.clear();
            dog.trail.add(here);
            dog.pos = here;
        }

        // target: the trail point FOLLOW_DIST behind the explorer (walking back along the path)
        Vec3 target = dog.trail.peekLast();
        double walked = 0;
        Vec3 prev = null;
        int kept = 0;
        for (Vec3 p : dog.trail) {
            kept++;
            if (prev != null) walked += prev.distanceTo(p);
            prev = p;
            if (walked >= FOLLOW_DIST) { target = p; break; }
        }
        while (dog.trail.size() > kept + 2)
            dog.trail.removeLast();
        if (walked < FOLLOW_DIST && target.distanceTo(here) < 0.5)
            target = dog.pos; // not enough path yet: stay put

        dog.oldPos = dog.pos;
        Vec3 delta = target.subtract(dog.pos);
        double max = Math.max(0.12, explorer.getDeltaMovement().horizontalDistance() * 1.3 + 0.05);
        if (delta.length() > max)
            delta = delta.normalize().scale(max);
        dog.pos = dog.pos.add(delta);
        double moved = delta.horizontalDistance();

        Wolf wolf = dog.wolf;
        wolf.tickCount++;
        wolf.xo = dog.oldPos.x; wolf.yo = dog.oldPos.y; wolf.zo = dog.oldPos.z;
        wolf.setPos(dog.pos);
        wolf.yBodyRotO = wolf.yBodyRot;
        wolf.yHeadRotO = wolf.yHeadRot;
        wolf.yRotO = wolf.getYRot();
        if (moved > 0.01) {
            float yaw = (float) (Mth.atan2(delta.z, delta.x) * (180F / Math.PI)) - 90F;
            float body = Mth.approachDegrees(wolf.yBodyRot, yaw, 20F);
            wolf.yBodyRot = body;
            wolf.yHeadRot = body;
            wolf.setYRot(body);
            dog.stillTicks = 0;
        } else {
            dog.stillTicks++;
        }
        wolf.setInSittingPose(dog.stillTicks > SIT_AFTER_TICKS);
        wolf.walkAnimation.update((float) Math.min(1.0, moved * 4.0), 0.4F);
    }

    @SubscribeEvent
    public static void onRenderExplorer(RenderLivingEvent.Post<?, ?> evt) {
        if (!(evt.getEntity() instanceof ExplorerUnit explorer))
            return;
        Dog dog = dogs.get(explorer.getId());
        if (dog == null)
            return;
        float pt = evt.getPartialTick();
        double ex = Mth.lerp(pt, explorer.xo, explorer.getX());
        double ey = Mth.lerp(pt, explorer.yo, explorer.getY());
        double ez = Mth.lerp(pt, explorer.zo, explorer.getZ());
        double dx = Mth.lerp(pt, dog.oldPos.x, dog.pos.x);
        double dy = Mth.lerp(pt, dog.oldPos.y, dog.pos.y);
        double dz = Mth.lerp(pt, dog.oldPos.z, dog.pos.z);
        Wolf wolf = dog.wolf;
        @SuppressWarnings("unchecked")
        EntityRenderer<Wolf> renderer = (EntityRenderer<Wolf>) MC.getEntityRenderDispatcher().getRenderer(wolf);
        PoseStack pose = evt.getPoseStack();
        pose.pushPose();
        pose.translate(dx - ex, dy - ey, dz - ez);
        renderer.render(wolf, Mth.rotLerp(pt, wolf.yRotO, wolf.getYRot()), pt, pose, evt.getMultiBufferSource(), evt.getPackedLight());
        pose.popPose();
    }
}
