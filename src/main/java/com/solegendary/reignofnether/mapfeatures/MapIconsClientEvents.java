package com.solegendary.reignofnether.mapfeatures;

import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.matchstart.MatchStartScreen;
import com.solegendary.reignofnether.minimap.MinimapClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.tutorial.TutorialClientEvents;
import com.solegendary.reignofnether.tutorial.TutorialStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// icons of the map's resources on the minimap: a fish for fishing spots, meat (or wool, a feather...) for herds.
// A place shows once it has been seen (out of the fog of war) at least once.
public class MapIconsClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final float ICON_SCALE = 0.6f; // items are 16 px

    private static List<HerdsClientboundPacket.HerdInfo> herds = new ArrayList<>();
    private static final Set<Long> discovered = new HashSet<>();

    public static void setHerds(List<HerdsClientboundPacket.HerdInfo> newHerds) {
        herds = newHerds;
    }

    private static boolean isDiscovered(BlockPos pos) {
        if (!FogOfWarClientEvents.isEnabled())
            return true;
        long key = new net.minecraft.world.level.ChunkPos(pos).toLong();
        if (discovered.contains(key))
            return true;
        if (FogOfWarClientEvents.isInBrightChunk(pos)) {
            discovered.add(key);
            return true;
        }
        return false;
    }

    private static ItemStack herdIcon(String species) {
        return new ItemStack(switch (species) {
            case "minecraft:sheep" -> Items.MUTTON;
            case "minecraft:pig" -> Items.PORKCHOP;
            case "minecraft:chicken" -> Items.CHICKEN;
            default -> Items.BEEF;
        });
    }

    private static void drawIcon(GuiGraphics g, BlockPos pos, ItemStack icon) {
        if (!MinimapClientEvents.isWorldXZinsideMap(pos.getX(), pos.getZ()) || !isDiscovered(pos))
            return;
        Vec2 at = MinimapClientEvents.worldPosToMinimapScreen(pos.getX(), pos.getZ());
        g.pose().pushPose();
        g.pose().translate(at.x, at.y, 300);
        g.pose().scale(ICON_SCALE, ICON_SCALE, 1);
        g.renderFakeItem(icon, -8, -8);
        g.pose().popPose();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) // after the minimap itself
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post evt) {
        if (!OrthoviewClientEvents.isEnabled() || MC.isPaused() || !HudClientEvents.enabled || !MinimapClientEvents.isMapReady()
            || !TutorialClientEvents.isAtOrPastStage(TutorialStage.MINIMAP_CLICK) || MC.screen instanceof MatchStartScreen)
            return;
        GuiGraphics g = evt.getGuiGraphics();
        ItemStack fish = new ItemStack(Items.COD);
        for (BlockPos spot : FishingClientEvents.getSpots())
            drawIcon(g, spot, fish);
        for (HerdsClientboundPacket.HerdInfo herd : herds)
            drawIcon(g, herd.home(), herdIcon(herd.species()));
    }
}
