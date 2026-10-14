package com.solegendary.reignofnether.building.buildings.villagers;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.tutorial.TutorialClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.List;

// Hospice: slowly heals the player's (and allies') units standing near it, and trains Priests. Needs Mysticism.
// PLACEHOLDER structure: hospice.nbt is a simple white house made by a script until a real one is made
public class Hospice extends ProductionBuilding {

    public final static String buildingName = "Hospice";
    public final static String structureName = "hospice";
    public final static ResourceCost cost = ResourceCost.Building(0, 175, 100, 0);
    public static final int HEAL_RANGE = 12;
    public static final int HEAL_TICKS = 40;
    public static final float HEAL_AMOUNT = 1f;

    public Hospice() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.WHITE_TERRACOTTA;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/glistering_melon_slice.png");
        this.maxHealth = 200d;
        this.startingBlockTypes.add(Blocks.STONE_BRICKS);
        this.productions.add(ProductionItems.PRIEST, Keybindings.abilitySlot1);
    }

    @Override
    public void tick(Level level, BuildingPlacement placement) {
        super.tick(level, placement);
        if (!(level instanceof ServerLevel sl) || !placement.isBuilt || level.getGameTime() % HEAL_TICKS != 0)
            return;
        AABB area = new AABB(placement.centrePos).inflate(HEAL_RANGE);
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!(le instanceof Unit u) || !le.isAlive() || le.getHealth() >= le.getMaxHealth() ||
                !AlliancesServerEvents.isAlliedOrOwned(u.getOwnerName(), placement.ownerName))
                continue;
            le.heal(HEAL_AMOUNT);
            if (le.getRandom().nextInt(3) == 0)
                for (ServerPlayer p : sl.players())
                    sl.sendParticles(p, ParticleTypes.HAPPY_VILLAGER, true, le.getX(), le.getY() + le.getBbHeight(), le.getZ(), 2, 0.3, 0.3, 0.3, 0);
        }
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/glistering_melon_slice.png"),
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.HOSPICE,
            TutorialClientEvents::isEnabled,
            () -> BuildingClientEvents.hasFinishedBuilding(Buildings.TOWN_CENTRE) || ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.hospice").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.hospice.tooltip1").getVisualOrderText(),
                Component.translatable("buildings.reignofnether.hospice.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
