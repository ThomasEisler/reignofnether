package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;

// Tirailleur mercenaire (Tavern): a hired crossbowman, like a pillager but ready at once and dearer; red and black
// clothes with a red bandana (MercenaryRenderer).
public class MercenaryArcherUnit extends PillagerUnit {

    public MercenaryArcherUnit(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public ResourceCost getCost() { return MercenaryArcherProd.cost; }
}
