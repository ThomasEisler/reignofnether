package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.keybinds.Keybinding;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductionItemList {
    Map<ProductionItem, Keybinding> productions = new LinkedHashMap<>();

    public ProductionItemList() {

    }

    public void add(ProductionItem production, Keybinding keybind) {
        productions.put(production, keybind);
    }

    public void remove(ProductionItem production) {
        productions.remove(production);
    }

    public List<Button> getButtons(ProductionPlacement placement) {
        List<Button> buttons = new ArrayList<>();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            for (Map.Entry<ProductionItem, Keybinding> production : productions.entrySet()) {
                if (!com.solegendary.reignofnether.tech.Tech.hasTech(true, placement.ownerName,
                        com.solegendary.reignofnether.tech.Tech.getRequiredForProduction(production.getKey())))
                    continue; // building upgrade not discovered yet in the technology tree
                StartProductionButton button = production.getKey().getStartButton(placement, production.getValue());
                if (button != null)
                    buttons.add(button);
            }
        }
        return buttons;
    }

    public List<ProductionItem> get() {
        return new ArrayList<>(productions.keySet());
    }
}
