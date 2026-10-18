package com.solegendary.reignofnether.religion;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.tutorial.TutorialClientEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.List;

// Temple: makes faith, founds the civilisation's religion (one button per free religion) and buys its second belief.
// One per city, on that city's land. Needs Mysticism. Prophets (step 2) will be trained here.
// PLACEHOLDER structure: temple.nbt is a small white temple made by tools/make_temple.py until a real one is made
public class Temple extends ProductionBuilding {

    public final static String buildingName = "Temple";
    public final static String structureName = "temple";
    public final static ResourceCost cost = ResourceCost.Building(0, 150, 75, 0);

    public Temple() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.SMOOTH_QUARTZ;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/quartz_pillar.png");
        this.maxHealth = 250d;
        this.startingBlockTypes.add(Blocks.POLISHED_ANDESITE);
        this.productions.add(com.solegendary.reignofnether.building.production.ProductionItems.PROPHET,
            com.solegendary.reignofnether.keybinds.Keybindings.abilitySlot1);
        for (Religion r : Religion.values())
            this.abilities.add(new ReligionAbility(r, null));
        for (Belief b : Belief.values())
            this.abilities.add(new ReligionAbility(null, b));
    }

    // one temple per city, on the land of one of the owner's cities
    @Nullable
    public static String getPlacementError(Level level, String ownerName, BlockPos origin) {
        BuildingPlacement city = Religions.cityAt(level.isClientSide(), origin.getX(), origin.getZ());
        if (city == null || !city.ownerName.equals(ownerName))
            return "Un temple se construit sur les terres d'une de vos villes";
        for (BuildingPlacement b : BuildingUtils.getBuildingsList(level.isClientSide()))
            if (b.getBuilding() instanceof Temple && b.ownerName.equals(ownerName) &&
                Religions.cityAt(level.isClientSide(), b.centrePos.getX(), b.centrePos.getZ()) == city)
                return "Cette ville a déjà un temple";
        return null;
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/quartz_pillar.png"),
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.TEMPLE,
            TutorialClientEvents::isEnabled,
            () -> BuildingClientEvents.hasFinishedBuilding(Buildings.TOWN_CENTRE) || ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.literal(name).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                FormattedCharSequence.forward("Produit de la foi (+1 toutes les 5 s). Un par ville, sur ses terres.", Style.EMPTY),
                FormattedCharSequence.forward("C'est là qu'on fonde sa religion (" + ReligionServerEvents.FOUND_COST + " de foi).", Style.EMPTY)
            ),
            this
        );
    }
}
