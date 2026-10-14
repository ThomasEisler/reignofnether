package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// the Tavern's mercenaries: the usual vindicator / pillager models in red and black clothes with a red bandana
@OnlyIn(Dist.CLIENT)
public class MercenaryRenderer {
    private static final ResourceLocation SWORD = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/mercenary.png");
    private static final ResourceLocation ARCHER = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/mercenary_archer.png");

    public static class Sword extends VindicatorUnitRenderer {
        public Sword(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(VindicatorUnit unit) { return SWORD; }
    }

    public static class Archer extends PillagerUnitRenderer {
        public Archer(EntityRendererProvider.Context context) { super(context); }
        @Override public ResourceLocation getTextureLocation(PillagerUnit unit) { return ARCHER; }
    }
}
