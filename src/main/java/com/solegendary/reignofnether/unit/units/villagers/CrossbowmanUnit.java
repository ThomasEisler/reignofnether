package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Arbalétrier (barracks, after Archery): a heavy crossbowman in an iron helmet. Shoots slower than the pillager but
// harder and further, and hits riders and their horses MOUNTED_BONUS times harder.
public class CrossbowmanUnit extends PillagerUnit {

    final static public float attackDamage = 11.0f;
    final static public float attacksPerSecond = 0.42f; // excludes crossbow charge time
    final static public float maxHealth = 50.0f;
    final static public float movementSpeed = 0.22f;
    final static public float attackRange = 22.0F;
    public static final float MOUNTED_BONUS = 1.75f;

    public CrossbowmanUnit(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public ResourceCost getCost() { return CrossbowmanProd.cost; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, 2.0)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), attackRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), 22)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    @Override
    public void setupEquipmentAndUpgradesServer() {
        super.setupEquipmentAndUpgradesServer();
        if (getItemBySlot(EquipmentSlot.HEAD).isEmpty())
            setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    }

    @Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class MountedBonus {
        @SubscribeEvent
        public static void onHurt(LivingHurtEvent evt) {
            LivingEntity target = evt.getEntity();
            if (evt.getSource().getEntity() instanceof CrossbowmanUnit && (target.isPassenger() || target.isVehicle()))
                evt.setAmount(evt.getAmount() * MOUNTED_BONUS);
        }
    }
}
