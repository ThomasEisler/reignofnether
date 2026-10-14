package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

// Experience of army units (not workers, who have their own professions): damage dealt and kills give experience;
// at 3 thresholds the unit goes up a rank (★ Aguerri, ★★ Vétéran, ★★★ Élite), each one +10 % health and damage.
// The rank is a max health modifier (saved with the unit and sent to players by the game itself), so players read
// it back from there to draw the stars (UnitRankClientEvents).
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class UnitRankEvents {

    public static final int[] XP_FOR_RANK = { 0, 40, 120, 300 };
    public static final double BONUS_PER_RANK = 0.10;
    public static final int KILL_XP = 15;
    public static final String[] RANK_NAMES = { "Recrue", "Aguerri", "Vétéran", "Élite" };

    private static final UUID RANK_HEALTH_UUID = UUID.fromString("6c1b9a3e-2f4d-4e8a-9b7c-5d2e1f0a3b44");
    private static final String XP_TAG = "rtsXp";

    public static boolean canRankUp(Entity entity) {
        return entity instanceof Unit && !(entity instanceof WorkerUnit) && entity instanceof LivingEntity;
    }

    // 0 to 3, read from the health modifier (works on players' side too)
    public static int getRank(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null)
            return 0;
        AttributeModifier mod = attr.getModifier(RANK_HEALTH_UUID);
        return mod == null ? 0 : (int) Math.round(mod.getAmount() / BONUS_PER_RANK);
    }

    // damage dealt by ranked units is raised; damage dealt gives experience
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent evt) {
        LivingEntity target = evt.getEntity();
        if (target.level().isClientSide() || !(evt.getSource().getEntity() instanceof LivingEntity attacker) || !canRankUp(attacker))
            return;
        if (target instanceof Unit tu && tu.getOwnerName().equals(((Unit) attacker).getOwnerName()))
            return;
        int rank = getRank(attacker);
        if (rank > 0)
            evt.setAmount((float) (evt.getAmount() * (1 + BONUS_PER_RANK * rank)));
        addXp(attacker, Math.min(evt.getAmount(), target.getHealth()));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent evt) {
        if (!evt.getEntity().level().isClientSide() && evt.getSource().getEntity() instanceof LivingEntity killer && canRankUp(killer)
                && evt.getEntity() instanceof Unit)
            addXp(killer, KILL_XP);
    }

    private static void addXp(LivingEntity unit, float amount) {
        float xp = unit.getPersistentData().getFloat(XP_TAG) + amount;
        unit.getPersistentData().putFloat(XP_TAG, xp);
        int rank = getRank(unit);
        int newRank = rank;
        while (newRank < 3 && xp >= XP_FOR_RANK[newRank + 1])
            newRank++;
        if (newRank > rank)
            setRank(unit, newRank);
    }

    private static void setRank(LivingEntity unit, int rank) {
        AttributeInstance attr = unit.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null)
            return;
        float before = unit.getMaxHealth();
        attr.removeModifier(RANK_HEALTH_UUID);
        attr.addPermanentModifier(new AttributeModifier(RANK_HEALTH_UUID, "rts rank", BONUS_PER_RANK * rank,
            AttributeModifier.Operation.MULTIPLY_TOTAL));
        unit.heal(unit.getMaxHealth() - before);
        if (unit.level() instanceof ServerLevel level) {
            level.playSound(null, unit.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.7f, 1.4f);
            for (ServerPlayer p : level.players())
                level.sendParticles(p, ParticleTypes.TOTEM_OF_UNDYING, true, unit.getX(), unit.getY() + unit.getBbHeight(), unit.getZ(),
                    25, 0.4, 0.5, 0.4, 0.3);
        }
        String owner = ((Unit) unit).getOwnerName();
        if (!owner.isBlank())
            HudClientboundPacket.showTempMessageI18n(owner, "Une unité devient " + RANK_NAMES[rank] + " " + "★".repeat(rank));
    }
}
