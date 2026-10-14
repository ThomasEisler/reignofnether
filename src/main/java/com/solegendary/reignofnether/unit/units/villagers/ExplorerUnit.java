package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// Éclaireur: a fast, unarmed villager (cartographer clothes) that sees far and uncovers the clouds over
// unexplored land. Its dog is drawn by ExplorerDogClientEvents, it is not an entity of its own.
public class ExplorerUnit extends Vindicator implements Unit, VillagerDataHolder {
    public static final Abilities ABILITIES = new Abilities();

    //region
    @Override
    public void updateAbilityButtons() {
        abilities = ABILITIES.clone();
    }
    Object2ObjectArrayMap<Ability, Float> cooldowns = Unit.createCooldownMap();
    Object2ObjectArrayMap<Ability, Integer> charges = new Object2ObjectArrayMap<>();
    @Override public Object2ObjectArrayMap<Ability, Float> getAbilityCooldowns() { return cooldowns; }
    @Override public boolean hasAutocast(Ability ability) { return autocast == ability; }
    @Override public void setAutocast(Ability autocast) { this.autocast = autocast; }
    @Override public Object2ObjectArrayMap<Ability, Integer> getAbilityCharges() { return charges; }

    Ability autocast;

    private int eatingTicksLeft = 0;
    public void setEatingTicksLeft(int amount) { eatingTicksLeft = amount; }
    public int getEatingTicksLeft() { return eatingTicksLeft; }
    private BlockPos anchorPos = new BlockPos(0,0,0);
    public void setAnchor(BlockPos bp) { anchorPos = bp; }
    public BlockPos getAnchor() { return anchorPos; }

    private final ArrayList<Checkpoint> checkpoints = new ArrayList<>();
    public ArrayList<Checkpoint> getCheckpoints() { return checkpoints; }

    GarrisonGoal garrisonGoal;
    public GarrisonGoal getGarrisonGoal() { return garrisonGoal; }
    public boolean canGarrison() { return getGarrisonGoal() != null; }

    UsePortalGoal usePortalGoal;
    public UsePortalGoal getUsePortalGoal() { return usePortalGoal; }
    public boolean canUsePortal() { return getUsePortalGoal() != null; }

    public Abilities getAbilities() {return abilities;}
    public List<ItemStack> getItems() {return items;}
    public MoveToTargetBlockGoal getMoveGoal() {return moveGoal;}
    public SelectedTargetGoal<? extends LivingEntity> getTargetGoal() {return targetGoal;}
    public ReturnResourcesGoal getReturnResourcesGoal() {return null;}
    public int getMaxResources() {return 0;}

    private MoveToTargetBlockGoal moveGoal;
    private SelectedTargetGoal<? extends LivingEntity> targetGoal;

    public LivingEntity getFollowTarget() { return followTarget; }
    public boolean getHoldPosition() { return holdPosition; }
    public void setHoldPosition(boolean holdPosition) { this.holdPosition = holdPosition; }

    private LivingEntity followTarget = null;
    private boolean holdPosition = false;

    public String getOwnerName() { return this.entityData.get(ownerDataAccessor); }
    public void setOwnerName(String name) { this.entityData.set(ownerDataAccessor, name); }
    public static final EntityDataAccessor<String> ownerDataAccessor =
            SynchedEntityData.defineId(ExplorerUnit.class, EntityDataSerializers.STRING);

    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(ExplorerUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
            SynchedEntityData.defineId(ExplorerUnit.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<VillagerData> VILLAGER_DATA =
            SynchedEntityData.defineId(ExplorerUnit.class, EntityDataSerializers.VILLAGER_DATA);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
        this.entityData.define(VILLAGER_DATA, new VillagerData(VillagerType.PLAINS, VillagerProfession.CARTOGRAPHER, 2));
    }

    @Override
    public VillagerData getVillagerData() { return this.entityData.get(VILLAGER_DATA); }

    @Override
    public void setVillagerData(@NotNull VillagerData data) { this.entityData.set(VILLAGER_DATA, data); }

    @Nullable
    public ResourceCost getCost() {return ResourceCosts.EXPLORER;}

    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // endregion

    final static public float maxHealth = 25.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.34f; // villagers: 0.25
    final static public int sightRange = 40;          // villagers: 16

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    public ExplorerUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
        updateAbilityButtons();
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.VILLAGER_AMBIENT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.VILLAGER_DEATH; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.VILLAGER_HURT; }
    @Override protected void pickUpItem(ItemEntity pItemEntity) { }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, ExplorerUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, ExplorerUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, ExplorerUnit.armorValue)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), ExplorerUnit.sightRange)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0)
                .add(Attributes.ATTACK_DAMAGE, 0);
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.addAdditionalSaveData(pCompound);
        this.addUnitSaveData(pCompound);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.readUnitSaveData(pCompound);
    }

    public void initialiseGoals() {
        this.usePortalGoal = new UsePortalGoal(this);
        this.moveGoal = new MoveToTargetBlockGoal(this, false, 0);
        this.targetGoal = new SelectedTargetGoal<>(this, true, true);
        this.garrisonGoal = new GarrisonGoal(this);
    }

    @Override
    protected void registerGoals() {
        initialiseGoals();
        this.goalSelector.addGoal(2, usePortalGoal);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(2, garrisonGoal);
        this.goalSelector.addGoal(3, moveGoal);
        this.goalSelector.addGoal(4, new RandomLookAroundUnitGoal(this));
    }

    @Override
    public AABB getInflatedSelectionBox() {
        AABB aabb = this.getBoundingBox().inflate(0.2f, 0, 0.2f);
        aabb.setMaxY(aabb.maxY + 0.2f);
        return aabb;
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        return pSpawnData;
    }
}
