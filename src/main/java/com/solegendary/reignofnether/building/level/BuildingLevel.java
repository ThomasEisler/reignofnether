package com.solegendary.reignofnether.building.level;

import com.solegendary.reignofnether.resources.ResourceCost;

import javax.annotation.Nullable;

// One level (tier) of a levelled building: which structure it uses and the stats it grants.
// Level 1 is the base building as placed; levels 2+ are reached with a BuildingLevelUpgrade.
// Stats left at their default values fall back to the Building's own values.
public class BuildingLevel {
    public final String structureName; // NBT in both assets/ and data/ reignofnether/structures
    public final double maxHealth; // 0 = use the Building's maxHealth
    public final int attackRange; // garrisoned units' attack range; 0 = use the Building's own range
    public final float productionSpeed; // multiplier on production speed, 1.0 = normal
    public final float garrisonDamage; // multiplier on garrisoned units' damage, 1.0 = normal
    @Nullable
    public final ResourceCost upgradeCost; // cost and duration of upgrading TO this level; null for level 1

    private BuildingLevel(Builder builder) {
        this.structureName = builder.structureName;
        this.maxHealth = builder.maxHealth;
        this.attackRange = builder.attackRange;
        this.productionSpeed = builder.productionSpeed;
        this.garrisonDamage = builder.garrisonDamage;
        this.upgradeCost = builder.upgradeCost;
    }

    public static Builder of(String structureName) {
        return new Builder(structureName);
    }

    public static class Builder {
        private final String structureName;
        private double maxHealth = 0;
        private int attackRange = 0;
        private float productionSpeed = 1.0f;
        private float garrisonDamage = 1.0f;
        private ResourceCost upgradeCost = null;

        private Builder(String structureName) {
            this.structureName = structureName;
        }

        public Builder maxHealth(double maxHealth) {
            this.maxHealth = maxHealth;
            return this;
        }

        public Builder attackRange(int attackRange) {
            this.attackRange = attackRange;
            return this;
        }

        public Builder productionSpeed(float productionSpeed) {
            this.productionSpeed = productionSpeed;
            return this;
        }

        public Builder garrisonDamage(float garrisonDamage) {
            this.garrisonDamage = garrisonDamage;
            return this;
        }

        public Builder upgradeCost(ResourceCost upgradeCost) {
            this.upgradeCost = upgradeCost;
            return this;
        }

        public BuildingLevel build() {
            return new BuildingLevel(this);
        }
    }
}
