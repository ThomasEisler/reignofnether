package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.exploration.ExplorerDogClientEvents;
import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.VillagerUnitProfessionLayer;
import com.solegendary.reignofnether.unit.units.villagers.ExplorerUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// The Explorer rides a big dog: the dog (a wolf drawn only on screen, see ExplorerDogClientEvents) is drawn
// first, then the villager, raised onto its back with its legs in the riding pose.
@OnlyIn(Dist.CLIENT)
public class ExplorerUnitRenderer extends AbstractVillagerUnitRenderer<ExplorerUnit> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/entities/villager_unit.png");

    public static final float DOG_SCALE = 1.5f;   // a mount-sized dog
    public static final float SEAT_HEIGHT = 0.6f; // how far the villager is raised to sit on its back

    public ExplorerUnitRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerUnitModel<>(context.bakeLayer(VillagerUnitModel.LAYER_LOCATION)) {
            @Override
            public void setupAnim(ExplorerUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
                this.riding = true;
                super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            }
        }, 0.6F);
        this.addLayer(new VillagerUnitProfessionLayer(this, context.getResourceManager(), "villager"));
    }

    @Override
    public void render(ExplorerUnit explorer, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        Wolf dog = ExplorerDogClientEvents.getDog(explorer);
        if (dog != null) {
            @SuppressWarnings("unchecked")
            EntityRenderer<Wolf> dogRenderer = (EntityRenderer<Wolf>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dog);
            pose.pushPose();
            pose.scale(DOG_SCALE, DOG_SCALE, DOG_SCALE);
            dogRenderer.render(dog, yaw, partialTicks, pose, buffers, light);
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(0, SEAT_HEIGHT, 0);
        super.render(explorer, yaw, partialTicks, pose, buffers, light);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(ExplorerUnit explorer) {
        return TEXTURE;
    }
}
