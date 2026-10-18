package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.road.RoadType;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.ability.abilities.BuildRoad;
import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.addon.BuildingAddon;
import com.solegendary.reignofnether.building.level.BuildingLevel;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.faction.Faction;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Registered object in Buildings
// is a member of BuildingPlacement and is generally not instantiated directly
public abstract class Building {
    public String name;
    public String structureName;
    public ResourceLocation icon;
    public final boolean isCapitol;
    public Block portraitBlock; // block rendered in the portrait GUI to represent this building
    public boolean canAcceptResources = false; // can workers drop off resources here?
    private Faction faction;

    // chance for a mini explosion to destroy extra blocks if a player is breaking it
    // should be higher for large fragile buildings so players don't take ages to destroy it
    protected float explodeChance = 0.3f;
    protected float explodeRadius = 2.0f;
    protected float fireThreshold = 0.75f; // if building has less %hp than this, explosions caused can make fires
    protected float buildTimeModifier = 1.0f; // only affects non-built buildings, not repair times
    protected float repairTimeModifier = 1.25f; // only affects built buildings

    public int captureRange = 20;
    public boolean capturable = false;
    public boolean invulnerable = false;
    public boolean repairable = true;
    public boolean shouldDestroyOnReset = true;
    public boolean drawAggro = true;

    public static final int CAPITOL_SIGHT_RANGE = 32;
    public static final int DEFAULT_SIGHT_RANGE = 16;
    public static final int GARRISONED_BONUS_SIGHT_RANGE = 16;

    public ResourceCost cost;
    public boolean selfBuilding = false;
    public double maxHealth = 0;
    protected double maxHealthBonusPerUpgradeLevel = 0;

    public final static double DEFAULT_HEALTH_PER_BLOCK = 2.0d;

    // optional levels (level 1, 2, 3...); empty for buildings that don't use the generic level system
    private List<BuildingLevel> levels = List.of();

    public double getMaxHealth(BuildingPlacement placement) {
        BuildingLevel level = getLevel(placement);
        if (level != null && level.maxHealth > 0)
            return level.maxHealth;
        return maxHealth + (getUpgradeLevel(placement) * maxHealthBonusPerUpgradeLevel);
    }

    public boolean isUsingSetHealth(BuildingPlacement placement) {
        return getMaxHealth(placement) > 0;
    }

    // blocks types that are placed automatically when the building is placed
    // used to control size of initial foundations while keeping it symmetrical
    public final ArrayList<Block> startingBlockTypes = new ArrayList();

    public int foundationYLayers = 1; // how many Y layers from the bottom are part of the foundation

    protected final Abilities abilities = new Abilities();

    private Map<Class<BuildingAddon>, BuildingAddon> activeAddons = new HashMap<>();

    public Abilities getAbilities() {
        return this.abilities;
    }

    public Building(String structureName, ResourceCost cost, boolean isCapitol) {
        this.structureName = structureName;
        this.cost = cost;
        this.isCapitol = isCapitol;
        this.abilities.add(new BuildRoad(RoadType.PATH), Keybindings.hotkey10);
        this.abilities.add(new BuildRoad(RoadType.ROYAL), Keybindings.hotkey9); // only shown on Town Centres
    }

    public float getMeleeDamageMult() {
        return 0.25F; // this is 50% visually, as 1 block is 2hp by default
    }

    public ArrayList<BuildingBlock> getRelativeBlockData(LevelAccessor level) {
        return BuildingBlockData.getBuildingBlocksFromNbt(this.structureName, level);
    }

    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new BuildingPlacement(this, level, pos, rotation, ownerName, BuildingUtils.getAbsoluteBlockData(this.getRelativeBlockData(level), level, pos, rotation), this.isCapitol);
    }

    public void setFaction(ResourceLocation faction) {
        this.faction = Factions.getFaction(faction);
    }
    
    public Faction getFaction() {
        return this.faction == null ? Factions.NONE : this.faction;
    }

    // for levelled buildings this is the level index (0 = level 1), stored on the placement
    // other buildings override this to derive their upgrade state from their blocks
    public int getUpgradeLevel(BuildingPlacement placement) {
        return hasLevels() ? placement.getLevelIndex() : 0;
    }

    // the first level's structure replaces structureName so placement uses it
    protected void setLevels(BuildingLevel... levels) {
        this.levels = List.of(levels);
        this.structureName = levels[0].structureName;
    }

    public boolean hasLevels() {
        return !levels.isEmpty();
    }

    public List<BuildingLevel> getLevels() {
        return levels;
    }

    @Nullable
    public BuildingLevel getLevel(BuildingPlacement placement) {
        if (!hasLevels())
            return null;
        int index = getUpgradeLevel(placement);
        return index >= 0 && index < levels.size() ? levels.get(index) : null;
    }

    public float getProductionSpeed(BuildingPlacement placement) {
        BuildingLevel level = getLevel(placement);
        return level != null ? level.productionSpeed : 1.0f;
    }

    public abstract BuildingPlaceButton getBuildButton(Keybinding var1);

    public boolean isTypeOf(Building building) {
        return this == building;
    }

    public String getUpgradedStructureName(int upgradeLevel) {
        if (upgradeLevel >= 0 && upgradeLevel < levels.size())
            return levels.get(upgradeLevel).structureName;
        return structureName;
    }

    @Nullable
    public <T extends BuildingAddon> T getActiveAddon(Class<T> addonClass) {
        return (T) activeAddons.get(addonClass);
    }

    public <T extends BuildingAddon> void setActiveAddon(Class<T> addonClass, T addon, boolean active) {
        if (active) {
            activeAddons.put((Class<BuildingAddon>) addonClass, addon);
        } else {
            activeAddons.remove(addonClass);
        }
    }

    public boolean hasActiveAddon(Class<? extends BuildingAddon> addonClass) {
        return activeAddons.containsKey(addonClass);
    }

    public boolean canDestroyBlock(BlockPos relativeBp, BuildingPlacement placement) {
        return true;
    }

    public void onBlockBuilt(BlockPos bp, BlockState bs, BuildingPlacement buildingPlacement) {

    }

    public void onBuilt(BuildingPlacement buildingPlacement) {

    }

    public void destroy(ServerLevel serverLevel, BuildingPlacement placement) {

    }

    public void tick(Level tickLevel, BuildingPlacement buildingPlacement) {

    }

    public String getUpgradedName(BuildingPlacement buildingPlacement) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        if (key == null) {
            return "Unknown";
        }
        String name = I18n.get("buildings." + key.getNamespace() + "." + key.getPath());
        if (hasLevels())
            name += " " + I18n.get("buildings.reignofnether.level", getUpgradeLevel(buildingPlacement) + 1);
        return name;
    }
}