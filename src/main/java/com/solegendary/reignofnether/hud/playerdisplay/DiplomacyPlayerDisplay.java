package com.solegendary.reignofnether.hud.playerdisplay;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AllianceAction;
import com.solegendary.reignofnether.alliance.AllianceServerboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.diplomacy.DiplomacyClient;
import com.solegendary.reignofnether.diplomacy.DiplomacyServerEvents;
import com.solegendary.reignofnether.diplomacy.DiplomacyServerboundPacket;
import com.solegendary.reignofnether.diplomacy.Proposal;
import com.solegendary.reignofnether.diplomacy.Stance;
import com.solegendary.reignofnether.gamerules.GameruleClient;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.hud.RectZone;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientEvents;
import com.solegendary.reignofnether.resources.ResourcesServerboundPacket;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

// Dashboard for RTS players to make/break alliances and send resources to each other
public class DiplomacyPlayerDisplay extends AbstractPlayerDisplay {

    private static final Minecraft MC = Minecraft.getInstance();
    private Resources resourcesToSend = new Resources("", 0,0,0);

    public DiplomacyPlayerDisplay(RTSPlayer rtsPlayer) {
        super(rtsPlayer);
        resourcesToSend = new Resources(rtsPlayer.name,0,0,0);
    }

    public DiplomacyPlayerDisplay(AbstractClientPlayer player) {
        super(player);
    }

    private static final int MAX_ASK = 2000;
    private static final int RESOURCE_FRAME_WIDTH = Button.DEFAULT_ICON_FRAME_SIZE * 2; // frame containing a resource value + icon
    private static final int ALLIANCE_FRAME_WIDTH = (Button.DEFAULT_ICON_FRAME_SIZE * 9); // stance buttons + stance (and opinion) text
    public static final int DISPLAY_WIDTH = // total width of a player display
            PLAYER_FRAME_WIDTH +
                    RESOURCE_FRAME_WIDTH * 4 +
                    (int) (Button.DEFAULT_ICON_FRAME_SIZE * 2.5f) +
                    ALLIANCE_FRAME_WIDTH;


    private boolean isAllied() {
        return MC.player != null && AlliancesClient.isAllied(MC.player.getName().getString(), playerName);
    }

    protected boolean isRTSPlayer() {
        return PlayerClientEvents.isRTSPlayer(playerName);
    }

    private boolean isBotCiv() {
        RTSPlayer p = PlayerClientEvents.getRTSPlayer(playerName);
        return p != null && p.isBot();
    }

    // bots are never "logged in" but can always be dealt with
    @Override
    public boolean isPlayerLoggedIn() {
        return super.isPlayerLoggedIn() || isBotCiv();
    }

    private String myName() {
        return MC.player == null ? "" : MC.player.getName().getString();
    }

    private boolean allianceRequested() {
        return AlliancesClient.outboundPendingAlliances.contains(playerName);
    }

    private boolean allianceReceived() {
        return AlliancesClient.inboundPendingAlliances.contains(playerName);
    }

    private Button renderTradeResources(GuiGraphics guiGraphics, ResourceName resourceName,
                                        int x, int y, int mouseX, int mouseY) {
        String iconPath;
        String value;
        int color;
        switch (resourceName) {
            case FOOD -> {
                iconPath = "textures/icons/items/wheat.png";
                value = String.valueOf(resourcesToSend.food);
                color = resourcesToSend.food < 0 ? 0xFF6060 : 0xE8BC5F;
            }
            case WOOD -> {
                iconPath = "textures/icons/items/wood.png";
                value = String.valueOf(resourcesToSend.wood);
                color = resourcesToSend.wood < 0 ? 0xFF6060 : 0xA3753B;
            }
            default -> {
                iconPath = "textures/icons/items/iron_ore.png";
                value = String.valueOf(resourcesToSend.ore);
                color = resourcesToSend.ore < 0 ? 0xFF6060 : 0xFFF4ED;
            }
        }
        MyRenderer.renderFrameWithBg(guiGraphics,
                x, y,
                RESOURCE_FRAME_WIDTH,
                Button.DEFAULT_ICON_FRAME_SIZE,
                0xA0000000
        );
        guiGraphics.drawString(
                MC.font,
                value,
                x + (Button.DEFAULT_ICON_SIZE / 2) + 1,
                y + (Button.DEFAULT_ICON_SIZE / 2) + 1,
                color
        );

        Button changeResourceButton = new Button(
                "Change resources",
                Button.DEFAULT_ICON_SIZE,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/corner_plus.png"),
                (Keybinding) null,
                () -> false,
                () -> false,
                this::isPlayerLoggedIn,
                () -> {
                    switch (resourceName) {
                        case FOOD -> resourcesToSend.food += Keybindings.shiftMod.isDown() ? 1000 : 100;
                        case WOOD -> resourcesToSend.wood += Keybindings.shiftMod.isDown() ? 1000 : 100;
                        default -> resourcesToSend.ore += Keybindings.shiftMod.isDown() ? 1000 : 100;
                    }
                    Resources res = ResourcesClientEvents.getOwnResources();
                    if (res != null) {
                        resourcesToSend.food = Math.min(resourcesToSend.food, Math.max(res.food, 0));
                        resourcesToSend.wood = Math.min(resourcesToSend.wood, Math.max(res.wood, 0));
                        resourcesToSend.ore = Math.min(resourcesToSend.ore, Math.max(res.ore, 0));
                    }
                },
                () -> {
                    switch (resourceName) {
                        case FOOD -> resourcesToSend.food -= Keybindings.shiftMod.isDown() ? 1000 : 100;
                        case WOOD -> resourcesToSend.wood -= Keybindings.shiftMod.isDown() ? 1000 : 100;
                        default -> resourcesToSend.ore -= Keybindings.shiftMod.isDown() ? 1000 : 100;
                    }
                    // below 0 = asked for in exchange
                    resourcesToSend.food = Math.max(resourcesToSend.food, -MAX_ASK);
                    resourcesToSend.wood = Math.max(resourcesToSend.wood, -MAX_ASK);
                    resourcesToSend.ore = Math.max(resourcesToSend.ore, -MAX_ASK);
                },
                List.of(FormattedCharSequence.forward(switch (resourceName) {
                        case FOOD -> "Nourriture";
                        case WOOD -> "Bois";
                        default -> "Minerai";
                    } + " : clic gauche +100, clic droit -100 (Maj : 1000)", Style.EMPTY),
                    FormattedCharSequence.forward("Positif = je donne, négatif (rouge) = je demande en échange", Style.EMPTY))
        );
        changeResourceButton.bgIconResource = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, iconPath);
        changeResourceButton.render(guiGraphics, x + RESOURCE_FRAME_WIDTH, y, mouseX, mouseY);

        if (!isPlayerLoggedIn()) {
            guiGraphics.fill(
                    x, y,
                    x + RESOURCE_FRAME_WIDTH,
                    y + Button.DEFAULT_ICON_FRAME_SIZE,
                    0x99000000
            );
        }
        return changeResourceButton;
    }

    private List<Button> renderTradeConfirms(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        Button confirmButton = new Button(
                "Confirm trade",
                Button.DEFAULT_ICON_SIZE,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/tick.png"),
                (Keybinding) null,
                () -> false,
                () -> false,
                () -> hasTrade() && isPlayerLoggedIn(),
                this::sendResources,
                null,
                List.of(FormattedCharSequence.forward("Envoyer (cadeau) ou proposer l'échange", Style.EMPTY))
        );
        confirmButton.render(guiGraphics, x, y, mouseX, mouseY);

        Button cancelButton = new Button(
                "Cancel trade",
                Button.DEFAULT_ICON_SIZE,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/cross.png"),
                (Keybinding) null,
                () -> false,
                () -> false,
                this::hasTrade,
                this::resetResources,
                null,
                List.of(FormattedCharSequence.forward(I18n.get("alliances.reignofnether.tooltip.cancel_send_resources"), Style.EMPTY))
        );
        cancelButton.render(guiGraphics, x + Button.DEFAULT_ICON_FRAME_SIZE, y, mouseX, mouseY);

        return List.of(confirmButton, cancelButton);
    }

    private Button stanceButton(String name, String icon, String tooltip, Runnable onClick) {
        return new Button(
                name,
                Button.DEFAULT_ICON_SIZE,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, icon),
                (Keybinding) null,
                () -> false,
                () -> false,
                this::isPlayerLoggedIn,
                onClick,
                null,
                List.of(FormattedCharSequence.forward(tooltip, Style.EMPTY))
        );
    }

    // war / peace / alliance, what a bot thinks of me, and the buttons to change it (or answer an offer)
    private List<Button> renderStance(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        List<Button> buttons = new ArrayList<>();
        String me = myName();
        Stance stance = DiplomacyClient.getStance(me, playerName);
        Proposal offer = DiplomacyClient.getProposal(playerName, me);
        String text;
        if (offer != null) {
            text = switch (offer.kind()) {
                case PEACE -> "Propose la paix";
                case ALLIANCE -> "Propose une alliance";
                case TRADE -> "Offre " + DiplomacyServerEvents.describeTrade(offer.food(), offer.wood(), offer.ore());
            };
            buttons.add(stanceButton("Accept offer", "textures/hud/tick.png", "Accepter", () -> DiplomacyServerboundPacket.send("accepter", playerName)));
            buttons.add(stanceButton("Refuse offer", "textures/hud/cross.png", "Refuser", () -> DiplomacyServerboundPacket.send("refuser", playerName)));
        } else {
            text = stance.frName;
            switch (stance) {
                case WAR -> buttons.add(stanceButton("Propose peace", "textures/icons/items/book.png", "Proposer la paix",
                        () -> DiplomacyServerboundPacket.send("paix", playerName)));
                case PEACE -> {
                    buttons.add(stanceButton("Propose alliance", "textures/icons/items/sweet_berries.png", "Proposer une alliance",
                            () -> DiplomacyServerboundPacket.send("alliance", playerName)));
                    buttons.add(stanceButton("Declare war", "textures/icons/items/crossed_swords.png", "Déclarer la guerre",
                            () -> DiplomacyServerboundPacket.send("guerre", playerName)));
                }
                case ALLIANCE -> buttons.add(stanceButton("Break alliance", "textures/hud/cross.png", "Rompre l'alliance (retour à la paix)",
                        () -> DiplomacyServerboundPacket.send("rompre", playerName)));
            }
        }
        var opinion = DiplomacyClient.getOpinion(playerName, me);
        if (opinion != null)
            text += " · opinion " + (opinion.value() > 0 ? "+" : "") + opinion.value();

        for (int i = 0; i < buttons.size(); i++)
            buttons.get(i).render(guiGraphics, x + i * Button.DEFAULT_ICON_FRAME_SIZE, y, mouseX, mouseY);
        int textX = x + 2 * Button.DEFAULT_ICON_FRAME_SIZE;
        int drawStringStartOffset = (Button.DEFAULT_ICON_SIZE / 2) + 1;
        int maxWidth = ALLIANCE_FRAME_WIDTH - 2 * Button.DEFAULT_ICON_FRAME_SIZE;
        String shown = MC.font.plainSubstrByWidth(text, maxWidth - drawStringStartOffset * 2);
        MyRenderer.renderFrameWithBg(guiGraphics, textX, y, Math.min(maxWidth, drawStringStartOffset * 2 + MC.font.width(shown)),
                Button.DEFAULT_ICON_FRAME_SIZE, 0xA0000000 | (offer != null ? 0x2060A0 : stance.colour & 0x7F7F7F));
        guiGraphics.drawString(MC.font, shown, textX + drawStringStartOffset, y + (Button.DEFAULT_ICON_SIZE / 2) + 1,
                offer != null ? 0xFFFFFF : stance.colour);
        // the reason a bot is unhappy, when hovering the text
        if (opinion != null && !opinion.reason().isEmpty() && mouseX >= textX && mouseX < textX + maxWidth &&
                mouseY >= y && mouseY < y + Button.DEFAULT_ICON_FRAME_SIZE)
            guiGraphics.renderTooltip(MC.font, Component.literal("Dernier grief : " + opinion.reason()), mouseX, mouseY);
        return buttons;
    }

    private Button renderAllianceButton(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        Button allyRequestButton = new Button(
                "Request Alliance",
                Button.DEFAULT_ICON_SIZE,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/sweet_berries.png"),
                (Keybinding) null,
                () -> false,
                () -> false,
                this::isPlayerLoggedIn,
                this::requestAlliance,
                null,
                List.of(FormattedCharSequence.forward(I18n.get("alliances.reignofnether.tooltip.request_alliance"), Style.EMPTY))
        );
        Button allyCancelRequestButton = new Button(
                "Cancel Request Alliance",
                Button.DEFAULT_ICON_SIZE,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/cross.png"),
                (Keybinding) null,
                () -> false,
                () -> false,
                () -> true,
                this::cancelAllianceRequest,
                null,
                List.of(FormattedCharSequence.forward(I18n.get("alliances.reignofnether.tooltip.cancel_request_alliance"), Style.EMPTY))
        );
        allyCancelRequestButton.bgIconResource = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/sweet_berries.png");
        Button allyConfirmButton = new Button(
                "Accept Alliance",
                Button.DEFAULT_ICON_SIZE,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/tick.png"),
                (Keybinding) null,
                () -> false,
                () -> false,
                () -> true,
                this::acceptAllianceRequest,
                null,
                List.of(FormattedCharSequence.forward(I18n.get("alliances.reignofnether.tooltip.accept_alliance"), Style.EMPTY))
        );
        Button disbandButton = new Button(
                "Disband Alliance",
                Button.DEFAULT_ICON_SIZE,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/crossed_swords.png"),
                (Keybinding) null,
                () -> false,
                () -> false,
                this::isAllied,
                this::disbandAlliance,
                null,
                List.of(FormattedCharSequence.forward(I18n.get("alliances.reignofnether.tooltip.disband_alliance"), Style.EMPTY))
        );

        Button renderedButton;
        String allianceStatusStr;
        int frameBgColour = 0xA0000000 | PlayerColors.getPlayerAllianceColorHex(playerName);

        if (!isAllied() && !allianceRequested() && !allianceReceived()) {
            allianceStatusStr = I18n.get("alliances.reignofnether.tooltip.status.enemy");
            allyRequestButton.render(guiGraphics, x, y, mouseX, mouseY);
            renderedButton = allyRequestButton;
        } else if (!isAllied() && allianceRequested()) {
            allianceStatusStr = I18n.get("alliances.reignofnether.tooltip.status.requested");
            allyCancelRequestButton.render(guiGraphics, x, y, mouseX, mouseY);
            renderedButton = allyCancelRequestButton;
        } else if (!isAllied() && allianceReceived()) {
            allianceStatusStr = I18n.get("alliances.reignofnether.tooltip.status.accept");
            allyConfirmButton.render(guiGraphics, x, y, mouseX, mouseY);
            renderedButton = allyConfirmButton;
        } else {
            allianceStatusStr = I18n.get("alliances.reignofnether.tooltip.status.allied");
            disbandButton.render(guiGraphics, x, y, mouseX, mouseY);
            renderedButton = disbandButton;
            if (AlliancesClient.canControlAlly(playerName)) {
                allianceStatusStr += I18n.get("alliances.reignofnether.tooltip.status.allied.plural");
            }
        }

        int drawStringStartOffset = Button.DEFAULT_ICON_FRAME_SIZE + (Button.DEFAULT_ICON_SIZE / 2) + 1;
        int frameWidth = drawStringStartOffset + MC.font.width(allianceStatusStr);
        MyRenderer.renderFrameWithBg(
                guiGraphics,
                x + Button.DEFAULT_ICON_FRAME_SIZE,
                y,
                frameWidth,
                Button.DEFAULT_ICON_FRAME_SIZE,
                frameBgColour
        );
        guiGraphics.drawString(
                MC.font,
                allianceStatusStr,
                x + drawStringStartOffset,
                y + (Button.DEFAULT_ICON_SIZE / 2) + 1,
                0xFFFFFF
        );
        if (!isPlayerLoggedIn()) {
            guiGraphics.pose().translate(0,0,1);
            guiGraphics.fill(
                    x + Button.DEFAULT_ICON_FRAME_SIZE,
                    y,
                    x + (Button.DEFAULT_ICON_FRAME_SIZE * 2) + 4 + MC.font.width(allianceStatusStr),
                    y + Button.DEFAULT_ICON_FRAME_SIZE,
                    0x99000000
            );
        }
        return renderedButton;
    }

    private boolean hasTrade() {
        return resourcesToSend.food != 0 || resourcesToSend.wood != 0 || resourcesToSend.ore != 0;
    }

    private void sendResources() {
        if (MC.player != null) {
            DiplomacyServerboundPacket.send("echange", playerName, resourcesToSend.food, resourcesToSend.wood, resourcesToSend.ore);
            resetResources();
        }
    }

    private void resetResources() {
        this.resourcesToSend.food = 0;
        this.resourcesToSend.wood = 0;
        this.resourcesToSend.ore = 0;
    }

    private void requestAlliance() {
        if (GameruleClient.lockAlliances && MC.player != null) {
            MC.player.sendSystemMessage(Component.translatable("alliance.reignofnether.alliances_lock"));
            return;
        }
        if (GameruleClient.coopMode && MC.player != null) {
            MC.player.sendSystemMessage(Component.translatable("alliance.reignofnether.alliances_lock_coop"));
            return;
        }
        AllianceServerboundPacket.doAllianceAction(AllianceAction.REQUEST, playerName);
        AlliancesClient.outboundPendingAlliances.add(playerName);
    }

    private void cancelAllianceRequest() {
        AllianceServerboundPacket.doAllianceAction(AllianceAction.CANCEL_REQUEST, playerName);
        AlliancesClient.outboundPendingAlliances.removeIf(p -> p.equals(playerName));
    }

    private void acceptAllianceRequest() {
        AllianceServerboundPacket.doAllianceAction(AllianceAction.ACCEPT_REQUEST, playerName);
    }

    private void disbandAlliance() {
        if (GameruleClient.lockAlliances && MC.player != null) {
            MC.player.sendSystemMessage(Component.translatable("alliance.reignofnether.alliances_lock"));
            return;
        }
        if (GameruleClient.coopMode && MC.player != null) {
            MC.player.sendSystemMessage(Component.translatable("alliance.reignofnether.alliances_lock_coop"));
            return;
        }
        AllianceServerboundPacket.doAllianceAction(AllianceAction.DISBAND, playerName);
    }

    private final Button gotoFpvPlayerButton = new Button(
            "Go to player",
            Button.DEFAULT_ICON_SIZE,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/map.png"),
            (Keybinding) null,
            () -> false,
            () -> false,
            this::isAllied,
            () -> {
                if (this.player != null)
                    OrthoviewClientEvents.centreCameraOnPos(this.player.position());
            },
            null,
            List.of(FormattedCharSequence.forward(I18n.get("alliances.reignofnether.tooltip.goto_player"), Style.EMPTY))
    );

    // render and return all relevant buttons
    public ArrayList<Button> render(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        super.render(guiGraphics, x, y);
        ArrayList<Button> renderedButtons = new ArrayList<>();
        x += PLAYER_FRAME_WIDTH;

        if (isRTSPlayer()) {
            renderedButtons.add(this.renderTradeResources(guiGraphics, ResourceName.FOOD, x, y, mouseX, mouseY));
            x += RESOURCE_FRAME_WIDTH + Button.DEFAULT_ICON_FRAME_SIZE;
            renderedButtons.add(this.renderTradeResources(guiGraphics, ResourceName.WOOD, x, y, mouseX, mouseY));
            x += RESOURCE_FRAME_WIDTH + Button.DEFAULT_ICON_FRAME_SIZE;
            renderedButtons.add(this.renderTradeResources(guiGraphics, ResourceName.ORE, x, y, mouseX, mouseY));
            x += RESOURCE_FRAME_WIDTH + (Button.DEFAULT_ICON_FRAME_SIZE * 1.25f);
            renderedButtons.addAll(this.renderTradeConfirms(guiGraphics, x, y, mouseX, mouseY));
            x += (Button.DEFAULT_ICON_FRAME_SIZE * 2.25f);
        } else {
            gotoFpvPlayerButton.render(guiGraphics, x, y, mouseX, mouseY);
            renderedButtons.add(gotoFpvPlayerButton);
            x += (Button.DEFAULT_ICON_FRAME_SIZE * 1.25f);
        }
        if (isRTSPlayer())
            renderedButtons.addAll(this.renderStance(guiGraphics, x, y, mouseX, mouseY));
        else
            renderedButtons.add(this.renderAllianceButton(guiGraphics, x, y, mouseX, mouseY));
        return renderedButtons;
    }

    @Override
    public RectZone getRectZone(int blitX, int blitY, int borderWidth) {
        return new RectZone(
                blitX - borderWidth, blitY - borderWidth,
                blitX + DISPLAY_WIDTH + borderWidth,
                blitY + Button.DEFAULT_ICON_FRAME_SIZE + borderWidth
        );
    }
}
