package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.barbarian.BarbarianServerEvents;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RavagerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// vanilla ravager, with red harness for barbarians
@OnlyIn(Dist.CLIENT)
public class RavagerUnitRenderer extends RavagerRenderer {
    private static final ResourceLocation BARBARIAN = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/barbarian_ravager.png");

    public RavagerUnitRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Ravager ravager) {
        return BarbarianServerEvents.isBarbarian(ravager) ? BARBARIAN : super.getTextureLocation(ravager);
    }
}
