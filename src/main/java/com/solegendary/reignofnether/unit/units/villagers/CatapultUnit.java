package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.goals.RangedAttackBuildingGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

// Catapulte (Siege Workshop): slow, long range, lobs a boulder in a high arc. The boulder hurts every enemy unit where
// it lands (SPLASH_RADIUS) and smashes buildings hard (BUILDING_DAMAGE). Built on the pillager (it "loads" like a
// crossbow, which drives the arm animation in CatapultRenderer) but drawn as a wooden siege engine; no voice.
public class CatapultUnit extends PillagerUnit {

    final static public float attackDamage = 18.0f;    // to units, around the impact
    final static public float buildingDamage = 70.0f;  // to the building hit
    final static public float attacksPerSecond = 0.16f; // excludes the loading time
    final static public float maxHealth = 150.0f;
    final static public float movementSpeed = 0.15f;
    final static public float attackRange = 28.0F;
    public static final float SPLASH_RADIUS = 2.5f;
    private static final String BOULDER_TAG = "rtsBoulder";

    public CatapultUnit(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public ResourceCost getCost() { return CatapultProd.cost; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), attackRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), 20)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0.5)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    // a wooden machine: no villager voice
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.WOOD_HIT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.WOOD_BREAK; }
    @Override public boolean isPushable() { return false; }

    // the crossbow's bolt is swapped for a boulder thrown in a high arc at the target
    @Override
    public void shootCrossbowProjectile(LivingEntity user, @Nullable LivingEntity target, Projectile bolt, float angle, float velocity) {
        bolt.discard();
        Vec3 aim = null;
        if (target != null)
            aim = target.position();
        else if (getAttackBuildingGoal() instanceof RangedAttackBuildingGoal<?> rabg && rabg.getBuildingTarget() != null)
            aim = Vec3.atCenterOf(rabg.getBuildingTarget().centrePos);
        else if (getRangedAttackGroundGoal() != null && getRangedAttackGroundGoal().getGroundTarget() != null)
            aim = Vec3.atCenterOf(getRangedAttackGroundGoal().getGroundTarget());
        if (aim != null)
            throwBoulder(aim);
    }

    private void throwBoulder(Vec3 aim) {
        Snowball boulder = new Snowball(level(), this);
        boulder.setItem(new ItemStack(Items.COBBLESTONE));
        boulder.getPersistentData().putBoolean(BOULDER_TAG, true);
        boulder.setPos(getX(), getY() + 2.0, getZ());
        double dx = aim.x - getX(), dz = aim.z - getZ();
        double dist = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        // 45 degree lob: speed for that distance (gravity 0.03), a bit more for air drag and the height difference
        double speed = Math.sqrt(dist * 0.03) * 1.08 + Math.max(0, aim.y - getY()) * 0.01;
        boulder.shoot(dx / dist, 1.0, dz / dist, (float) speed, 1.5f);
        level().addFreshEntity(boulder);
        playSound(SoundEvents.CROSSBOW_SHOOT, 2.0f, 0.5f);
    }

    @Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class Boulders {
        @SubscribeEvent
        public static void onImpact(ProjectileImpactEvent evt) {
            Projectile p = evt.getProjectile();
            if (!(p.level() instanceof ServerLevel level) || !p.getPersistentData().getBoolean(BOULDER_TAG) ||
                !(p.getOwner() instanceof CatapultUnit catapult))
                return;
            p.getPersistentData().remove(BOULDER_TAG); // once
            Vec3 at = evt.getRayTraceResult().getLocation();
            String owner = catapult.getOwnerName();
            // units around the impact
            DamageSource src = level.damageSources().mobProjectile(p, catapult);
            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SPLASH_RADIUS))) {
                if (le == catapult || !le.isAlive())
                    continue;
                if (le instanceof Unit u && AlliancesServerEvents.isAlliedOrOwned(u.getOwnerName(), owner))
                    continue;
                le.hurt(src, catapult.getUnitAttackDamage());
            }
            // the building hit (or right next to the impact)
            BlockPos bp = BlockPos.containing(at);
            for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
                if (AlliancesServerEvents.isAlliedOrOwned(b.ownerName, owner))
                    continue;
                if (b.isPosInsideBuilding(bp, 2)) {
                    b.destroyRandomBlocks(buildingDamage);
                    break;
                }
            }
            // smash
            level.playSound(null, bp, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.8f, 1.3f);
            BlockState stone = net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState();
            for (ServerPlayer pl : level.players()) {
                level.sendParticles(pl, ParticleTypes.EXPLOSION, true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                level.sendParticles(pl, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, stone), true,
                    at.x, at.y + 0.3, at.z, 30, 1, 0.5, 1, 0.2);
            }
        }
    }
}
