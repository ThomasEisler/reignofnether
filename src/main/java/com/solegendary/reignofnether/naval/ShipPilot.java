package com.solegendary.reignofnether.naval;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.talhanation.smallships.api.ShipBridge;
import com.talhanation.smallships.config.SmallShipsConfig;
import com.talhanation.smallships.world.entity.ship.Ship;
import com.talhanation.smallships.world.entity.ship.abilities.Sailable;
import com.talhanation.smallships.world.entity.ship.abilities.Seatable;
import com.talhanation.smallships.world.entity.ship.seat.SeatType;
import com.talhanation.smallships.world.entity.ship.seat.ShipSeat;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// The part of the ships that uses Small Ships' classes (see NavalServerEvents): captains at the helm and steering.
// A ship goes where its captain was last ordered to, straight: it turns towards the spot, sails slower while
// turning hard and when close, and furls its sails once there (Small Ships stops it on an obstacle by itself).
public class ShipPilot {

    public static final int TICK_INTERVAL = 2;
    private static final double ARRIVAL_DIST = 4; // blocks
    private static final double SLOW_DIST = 14; // blocks: half sail from here
    private static final float TURN_DEADZONE = 6; // degrees either side of the heading that count as straight on
    private static final float HARD_TURN = 60; // degrees: a quarter of sail while turning more than this

    private static final Map<UUID, BlockPos> destinations = new HashMap<>(); // by ship

    // Small Ships only lets players and the entities in its driverEntities config take the helm
    public static void allowCaptains() {
        String id = EntityRegistrar.VILLAGER_UNIT.getId().toString();
        List<String> drivers = new ArrayList<>(SmallShipsConfig.Server.driverEntities.get());
        if (!drivers.contains(id)) {
            drivers.add(id);
            SmallShipsConfig.Server.driverEntities.set(drivers);
            ReignOfNether.LOGGER.info("Small Ships: " + id + " may now take the helm");
        }
    }

    public static String spawnShip(ServerLevel level, String type, BlockPos at, String ownerName) {
        EntityType<?> entityType = EntityType.byString(ResourceLocation.fromNamespaceAndPath("smallships", type).toString()).orElse(null);
        if (entityType == null)
            return "Navire inconnu : " + type;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ());
        BlockPos water = new BlockPos(at.getX(), surface - 1, at.getZ());
        if (!level.getFluidState(water).isSource())
            return "Il faut de l'eau à cet endroit (" + at.getX() + ", " + at.getZ() + ")";
        Entity entity = entityType.create(level);
        if (!(entity instanceof Ship ship))
            return "Ce n'est pas un navire : " + type;
        entity.moveTo(at.getX() + 0.5, surface, at.getZ() + 0.5, 0, 0);
        level.addFreshEntity(entity);

        Entity captain = UnitServerEvents.spawnMob(EntityRegistrar.VILLAGER_UNIT.get(), level, water, ownerName);
        if (captain == null)
            return "Navire posé, mais pas de capitaine";
        captain.startRiding(ship, true);
        Integer helm = helmSeat(ship);
        if (helm == null || !ShipBridge.takeSeat(ship, captain, helm))
            return "Navire posé, mais le capitaine n'a pas pu prendre la barre (réglage driverEntities de Small Ships ?)";
        return "Navire (" + type + ") posé en " + at.getX() + ", " + at.getZ() + " pour " + ownerName;
    }

    private static Integer helmSeat(Ship ship) {
        if (ship instanceof Seatable seatable)
            for (ShipSeat seat : seatable.getSeats())
                if (seat.type() == SeatType.DRIVER)
                    return seat.id();
        return null;
    }

    public static void tick(ServerLevel level) {
        for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
            if (!entity.isAlive() || !(entity instanceof Unit unit) || !(entity.getVehicle() instanceof Ship ship) ||
                ship.getHelmsman() != entity)
                continue;
            // a move (or attack-move) order given to the captain is the ship's new destination
            BlockPos ordered = unit.getMoveGoal().getMoveTarget();
            if (entity instanceof AttackerUnit attacker && attacker.getAttackMoveTarget() != null) {
                ordered = attacker.getAttackMoveTarget();
                attacker.setAttackMoveTarget(null);
            }
            if (ordered != null) {
                destinations.put(((Entity) ship).getUUID(), ordered);
                unit.getMoveGoal().stopMoving(); // the captain stays at the helm
            }
            steer(ship, destinations.get(((Entity) ship).getUUID()));
        }
    }

    // Minecraft's own methods are called on the ship as an Entity, so they're remapped like ours
    private static void steer(Ship ship, BlockPos destination) {
        Entity e = ship;
        if (destination != null && !ShipBridge.canMakeWay(ship)) {
            destinations.remove(e.getUUID()); // sinking, sunk, moored...
            destination = null;
        }
        double dx = destination == null ? 0 : destination.getX() + 0.5 - e.getX();
        double dz = destination == null ? 0 : destination.getZ() + 0.5 - e.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (destination == null || dist < ARRIVAL_DIST) {
            if (destination != null)
                destinations.remove(e.getUUID());
            ShipBridge.pressKeys(ship, false, false, false, false);
            setSail(ship, 0);
            return;
        }
        float heading = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
        float diff = Mth.wrapDegrees(heading - e.getYRot());
        ShipBridge.pressKeys(ship, true, false, diff < -TURN_DEADZONE, diff > TURN_DEADZONE);
        setSail(ship, Math.abs(diff) > HARD_TURN ? 1 : dist < SLOW_DIST ? 2 : 4);
    }

    private static void setSail(Ship ship, int state) {
        if (ship instanceof Sailable sailable && sailable.getSailState() != state)
            sailable.setSailState((byte) state);
    }
}
