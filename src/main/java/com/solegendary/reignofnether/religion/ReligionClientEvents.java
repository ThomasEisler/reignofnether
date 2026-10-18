package com.solegendary.reignofnether.religion;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.territory.Territory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// religion on the player's screen: the "expel" button shown when a foreign prophet on one's land is selected
public class ReligionClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    @Nullable
    public static Button getExpelButton(LivingEntity selected) {
        if (!(selected instanceof ProphetUnit p) || MC.player == null)
            return null;
        String me = MC.player.getName().getString();
        if (p.getOwnerName().equals(me) || !PlayerClientEvents.isRTSPlayer(me))
            return null;
        String land = Territory.ownerAt(new ArrayList<>(BuildingClientEvents.getBuildings()), PlayerClientEvents::isRTSPlayer, p.getX(), p.getZ());
        if (!me.equals(land))
            return null;
        return new Button(
            "Expel prophet",
            14,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/barrier.png"),
            (Keybinding) null,
            () -> false,
            () -> false,
            () -> Religions.faith(true, me) >= ReligionServerEvents.EXPEL_FAITH,
            () -> ReligionServerboundPacket.expel(p.getId()),
            null,
            List.of(
                FormattedCharSequence.forward("Expulser ce prophète", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward(ReligionServerEvents.EXPEL_FAITH + " de foi (vous en avez " + Religions.faith(true, me) + ")", Style.EMPTY),
                FormattedCharSequence.forward("Il rentre chez lui et ne revient pas sur vos terres avant 5 min.", Style.EMPTY),
                FormattedCharSequence.forward("Une expulsion toutes les 3 min ; son propriétaire ne va pas aimer.", Style.EMPTY))
        );
    }
}
