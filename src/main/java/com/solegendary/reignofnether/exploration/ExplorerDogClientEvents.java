package com.solegendary.reignofnether.exploration;

import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.units.villagers.ExplorerUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// The Explorer's dog (its mount): a wolf that exists only on screen, kept in step with its explorer
// (position, facing, walking animation). Drawn by ExplorerUnitRenderer.
public class ExplorerDogClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final Map<Integer, Wolf> dogs = new HashMap<>();

    public static Wolf getDog(ExplorerUnit explorer) {
        return dogs.get(explorer.getId());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        if (MC.level == null) {
            dogs.clear();
            return;
        }
        if (MC.isPaused())
            return;
        Set<Integer> alive = new HashSet<>();
        for (LivingEntity le : UnitClientEvents.getAllUnits()) {
            if (!(le instanceof ExplorerUnit explorer) || !explorer.isAlive())
                continue;
            alive.add(explorer.getId());
            Wolf dog = dogs.get(explorer.getId());
            if (dog == null || dog.level() != MC.level) {
                dog = new Wolf(EntityType.WOLF, MC.level);
                dog.setTame(true);
                dogs.put(explorer.getId(), dog);
            }
            dog.tickCount++;
            dog.xo = explorer.xo; dog.yo = explorer.yo; dog.zo = explorer.zo;
            dog.setPos(explorer.position());
            dog.yBodyRotO = explorer.yBodyRotO;
            dog.yBodyRot = explorer.yBodyRot;
            dog.yHeadRotO = explorer.yHeadRotO;
            dog.yHeadRot = explorer.yHeadRot;
            dog.yRotO = explorer.yRotO;
            dog.setYRot(explorer.getYRot());
            double moved = explorer.position().subtract(explorer.xo, explorer.yo, explorer.zo).horizontalDistance();
            dog.walkAnimation.update((float) Math.min(1.0, moved * 4.0), 0.4F);
        }
        dogs.keySet().retainAll(alive);
    }
}
