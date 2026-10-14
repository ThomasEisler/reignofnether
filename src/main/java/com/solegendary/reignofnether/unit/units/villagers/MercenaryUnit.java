package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.level.Level;

// Mercenaire (Tavern): a hired swordsman, like a vindicator but ready at once and dearer; red and black clothes with
// a red bandana so they're told apart from the barracks' soldiers (MercenaryRenderer).
public class MercenaryUnit extends VindicatorUnit {

    public MercenaryUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public ResourceCost getCost() { return MercenaryProd.cost; }
}
