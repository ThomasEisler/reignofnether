package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.buildings.monsters.*;
import com.solegendary.reignofnether.building.buildings.neutral.*;
import com.solegendary.reignofnether.building.buildings.piglins.*;
import com.solegendary.reignofnether.building.buildings.villagers.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

public class Buildings {
    public static final Mausoleum MAUSOLEUM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "mausoleum"), new Mausoleum());
    public static final SpruceStockpile SPRUCE_STOCKPILE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "spruce_stockpile"), new SpruceStockpile());
    public static final HauntedHouse HAUNTED_HOUSE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "haunted_house"), new HauntedHouse());
    public static final PumpkinFarm PUMPKIN_FARM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "pumpkin_farm"), new PumpkinFarm());
    public static final DarkWatchtower DARK_WATCHTOWER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "dark_watchtower"), new DarkWatchtower());
    public static final Graveyard GRAVEYARD = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "graveyard"), new Graveyard());
    public static final Dungeon DUNGEON = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "dungeon"), new Dungeon());
    public static final SpiderLair SPIDER_LAIR = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "spider_lair"), new SpiderLair());
    public static final SlimePit SLIME_PIT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "slime_pit"), new SlimePit());
    public static final Laboratory LABORATORY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "laboratory"), new Laboratory());
    public static final Stronghold STRONGHOLD = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "stronghold"), new Stronghold());
    public static final SpruceBridge SPRUCE_BRIDGE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "spruce_bridge"), new SpruceBridge());
    public static final SculkCatalyst SCULK_CATALYST = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "sculk_catalyst"), new SculkCatalyst());
    public static final CentralPortal CENTRAL_PORTAL = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "central_portal"), new CentralPortal());
    public static final PortalBasic PORTAL_BASIC = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "portal_basic"), new PortalBasic());
    public static final PortalPocket PORTAL_POCKET = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "portal_pocket"), new PortalPocket());
    public static final PortalCivilian PORTAL_CIVILIAN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "portal_civilian"), new PortalCivilian());
    public static final PortalMilitary PORTAL_MILITARY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "portal_military"), new PortalMilitary());
    public static final PortalTransport PORTAL_TRANSPORT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "portal_transport"), new PortalTransport());
    public static final NetherwartFarm NETHERWART_FARM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "netherwart_farm"), new NetherwartFarm());
    public static final Bastion BASTION = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bastion"), new Bastion());
    public static final HoglinStables HOGLIN_STABLES = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "hoglin_stables"), new HoglinStables());
    public static final FlameSanctuary FLAME_SANCTUARY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "flame_sanctuary"), new FlameSanctuary());
    public static final WitherShrine WITHER_SHRINE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wither_shrine"), new WitherShrine());
    public static final BasaltSprings BASALT_SPRINGS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "basalt_springs"), new BasaltSprings());
    public static final Fortress FORTRESS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "fortress"), new Fortress());
    public static final BlackstoneBridge BLACKSTONE_BRIDGE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "blackstone_bridge"), new BlackstoneBridge());
    public static final TownCentre TOWN_CENTRE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "town_centre"), new TownCentre());
    public static final OakStockpile OAK_STOCKPILE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "oak_stockpile"), new OakStockpile());
    public static final VillagerHouse VILLAGER_HOUSE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "villager_house"), new VillagerHouse());
    public static final WheatFarm WHEAT_FARM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wheat_farm"), new WheatFarm());
    public static final Watchtower WATCHTOWER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "watchtower"), new Watchtower());
    public static final Barracks BARRACKS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "barracks"), new Barracks());
    public static final Blacksmith BLACKSMITH = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "blacksmith"), new Blacksmith());
    public static final WitchHut WITCH_HUT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "witch_hut"), new WitchHut());
    public static final ArcaneTower ARCANE_TOWER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "arcane_tower"), new ArcaneTower());
    public static final Library LIBRARY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "library"), new Library());
    public static final Castle CASTLE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "castle"), new Castle());
    public static final IronGolemBuilding IRON_GOLEM_BUILDING = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "iron_golem_building"), new IronGolemBuilding());
    public static final OakBridge OAK_BRIDGE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "oak_bridge"), new OakBridge());
    public static final Beacon BEACON = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "beacon"), new Beacon());
    public static final CapturableBeacon CAPTURABLE_BEACON = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "capturable_beacon"), new CapturableBeacon());
    public static final EndPortal END_PORTAL = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "end_portal"), new EndPortal());
    public static final HealingFountain HEALING_FOUNTAIN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "healing_fountain"), new HealingFountain());
    public static final NeutralTransportPortal NEUTRAL_TRANSPORT_PORTAL = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "neutral_transport_portal"), new NeutralTransportPortal());
    public static final AltarOfDarkness ALTAR_OF_DARKNESS = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "altar_of_darkness"), new AltarOfDarkness());
    public static final ShrineOfProsperity SHRINE_OF_PROSPERITY = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "shrine_of_prosperity"), new ShrineOfProsperity());
    public static final InfernalPortal INFERNAL_PORTAL = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "infernal_portal"), new InfernalPortal());
    public static final MonsterMarket MONSTER_MARKET = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "monster_market"), new MonsterMarket());
    public static final PiglinMarket PIGLIN_MARKET = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "piglin_market"), new PiglinMarket());
    public static final VillagerMarket VILLAGER_MARKET = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "villager_market"), new VillagerMarket());
    public static final com.solegendary.reignofnether.building.buildings.villagers.Sawmill SAWMILL = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "sawmill"), new com.solegendary.reignofnether.building.buildings.villagers.Sawmill());
    public static final com.solegendary.reignofnether.building.buildings.villagers.Hospice HOSPICE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "hospice"), new com.solegendary.reignofnether.building.buildings.villagers.Hospice());
    public static final com.solegendary.reignofnether.building.buildings.villagers.Mill MILL = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "mill"), new com.solegendary.reignofnether.building.buildings.villagers.Mill());
    public static final com.solegendary.reignofnether.building.buildings.villagers.Mine MINE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "mine"), new com.solegendary.reignofnether.building.buildings.villagers.Mine());
    public static final com.solegendary.reignofnether.building.buildings.villagers.Tavern TAVERN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "tavern"), new com.solegendary.reignofnether.building.buildings.villagers.Tavern());
    public static final com.solegendary.reignofnether.building.buildings.villagers.SiegeWorkshop SIEGE_WORKSHOP = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "siege_workshop"), new com.solegendary.reignofnether.building.buildings.villagers.SiegeWorkshop());
    public static final com.solegendary.reignofnether.building.buildings.villagers.Port PORT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "port"), new com.solegendary.reignofnether.building.buildings.villagers.Port());
    public static final com.solegendary.reignofnether.building.buildings.neutral.Ruin RUIN_1 = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ruin_1"), new com.solegendary.reignofnether.building.buildings.neutral.Ruin("ruin_1"));
    public static final com.solegendary.reignofnether.building.buildings.neutral.Ruin RUIN_2 = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "ruin_2"), new com.solegendary.reignofnether.building.buildings.neutral.Ruin("ruin_2"));
    public static final BarbarianCamp BARBARIAN_CAMP = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "barbarian_camp"), new BarbarianCamp());
    public static final BarbarianOutbuilding BARBARIAN_TENT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "barbarian_tent"),
        new BarbarianOutbuilding("Barbarian Tent", "barbarian_tent", 120d, net.minecraft.world.level.block.Blocks.RED_WOOL, "textures/block/red_wool.png"));
    public static final BarbarianOutbuilding BARBARIAN_TOWER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "barbarian_tower"),
        new BarbarianOutbuilding("Barbarian Tower", "barbarian_tower", 180d, net.minecraft.world.level.block.Blocks.SPRUCE_LOG, "textures/block/spruce_log.png"));
    public static final BarbarianOutbuilding BARBARIAN_CAMPFIRE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "barbarian_campfire"),
        new BarbarianOutbuilding("Barbarian Campfire", "barbarian_campfire", 80d, net.minecraft.world.level.block.Blocks.CAMPFIRE, "textures/block/campfire_log_lit.png"));
    public static final BarbarianOutbuilding BARBARIAN_LOOT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "barbarian_loot"),
        new BarbarianOutbuilding("Barbarian Loot Store", "barbarian_loot", 150d, net.minecraft.world.level.block.Blocks.BARREL, "textures/block/barrel_side.png"));
    public static final BarbarianOutbuilding BARBARIAN_FORGE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "barbarian_forge"),
        new BarbarianOutbuilding("Barbarian Forge", "barbarian_forge", 200d, net.minecraft.world.level.block.Blocks.ANVIL, "textures/block/anvil.png"));
    public static final BarbarianOutbuilding BARBARIAN_ALARM = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "barbarian_alarm"),
        new BarbarianOutbuilding("Barbarian Alarm Horn", "barbarian_alarm", 120d, net.minecraft.world.level.block.Blocks.BELL, "textures/item/goat_horn.png"));
    public static final BarbarianOutbuilding BARBARIAN_CAGES = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "barbarian_cages"),
        new BarbarianOutbuilding("Barbarian Cages", "barbarian_cages", 150d, net.minecraft.world.level.block.Blocks.IRON_BARS, "textures/block/iron_bars.png"));
    public static final BarbarianOutbuilding BARBARIAN_STABLE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "barbarian_stable"),
        new BarbarianOutbuilding("Barbarian Stable", "barbarian_stable", 200d, net.minecraft.world.level.block.Blocks.HAY_BLOCK, "textures/block/hay_block_side.png"));
    public static final BarbarianOutbuilding BARBARIAN_RAVAGER_PEN = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "barbarian_ravager_pen"),
        new BarbarianOutbuilding("Barbarian Ravager Pen", "barbarian_ravager_pen", 200d, net.minecraft.world.level.block.Blocks.COBBLESTONE_WALL, "textures/block/cobblestone.png"));
    public static final BarbarianOutbuilding BARBARIAN_CHIEF_TENT = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "barbarian_chief_tent"),
        new BarbarianOutbuilding("Barbarian Chief Tent", "barbarian_chief_tent", 250d, net.minecraft.world.level.block.Blocks.BLACK_WOOL, "textures/block/black_wool.png"));

    // ramparts: segments of wall and gates, shaped by rampart/RampartShapes
    public static final com.solegendary.reignofnether.rampart.RampartBuilding PALISADE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "palisade"),
        new com.solegendary.reignofnether.rampart.RampartBuilding(com.solegendary.reignofnether.rampart.RampartBuilding.Kind.PALISADE, "Palisade", 150d, net.minecraft.world.level.block.Blocks.SPRUCE_LOG, "textures/block/spruce_log.png"));
    public static final com.solegendary.reignofnether.rampart.RampartBuilding STONE_RAMPART = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "stone_rampart"),
        new com.solegendary.reignofnether.rampart.RampartBuilding(com.solegendary.reignofnether.rampart.RampartBuilding.Kind.STONE, "Stone Rampart", 300d, net.minecraft.world.level.block.Blocks.STONE_BRICKS, "textures/block/stone_bricks.png"));
    public static final com.solegendary.reignofnether.rampart.RampartBuilding WOODEN_GATE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wooden_gate"),
        new com.solegendary.reignofnether.rampart.RampartBuilding(com.solegendary.reignofnether.rampart.RampartBuilding.Kind.WOODEN_GATE, "Wooden Gate", 220d, net.minecraft.world.level.block.Blocks.SPRUCE_TRAPDOOR, "textures/block/spruce_trapdoor.png"));
    public static final com.solegendary.reignofnether.rampart.RampartBuilding IRON_GATE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "iron_gate"),
        new com.solegendary.reignofnether.rampart.RampartBuilding(com.solegendary.reignofnether.rampart.RampartBuilding.Kind.IRON_GATE, "Portcullis", 450d, net.minecraft.world.level.block.Blocks.IRON_BARS, "textures/block/iron_bars.png"));

    public static final com.solegendary.reignofnether.rampart.RampartTowerBuilding RAMPART_TOWER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "rampart_tower"),
        new com.solegendary.reignofnether.rampart.RampartTowerBuilding(com.solegendary.reignofnether.rampart.RampartBuilding.Kind.TOWER, "Rampart Tower", 500d, net.minecraft.world.level.block.Blocks.CHISELED_STONE_BRICKS, "textures/block/chiseled_stone_bricks.png"));
    // level 1 and 3 of each family (level 2 above): reached by clicking a built wall (see rampart/RampartUpgrades)
    public static final com.solegendary.reignofnether.rampart.RampartBuilding REINFORCED_RAMPART = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "reinforced_rampart"),
        new com.solegendary.reignofnether.rampart.RampartBuilding(com.solegendary.reignofnether.rampart.RampartBuilding.Kind.REINFORCED, "Reinforced Rampart", 500d, net.minecraft.world.level.block.Blocks.DEEPSLATE_BRICKS, "textures/block/deepslate_bricks.png"));
    public static final com.solegendary.reignofnether.rampart.RampartBuilding REINFORCED_GATE = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "reinforced_gate"),
        new com.solegendary.reignofnether.rampart.RampartBuilding(com.solegendary.reignofnether.rampart.RampartBuilding.Kind.REINFORCED_GATE, "Reinforced Portcullis", 650d, net.minecraft.world.level.block.Blocks.IRON_BARS, "textures/block/iron_bars.png"));
    public static final com.solegendary.reignofnether.rampart.RampartTowerBuilding WOODEN_RAMPART_TOWER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "wooden_rampart_tower"),
        new com.solegendary.reignofnether.rampart.RampartTowerBuilding(com.solegendary.reignofnether.rampart.RampartBuilding.Kind.WOODEN_TOWER, "Palisade Tower", 300d, net.minecraft.world.level.block.Blocks.SPRUCE_LOG, "textures/block/spruce_log.png"));
    public static final com.solegendary.reignofnether.rampart.RampartTowerBuilding REINFORCED_RAMPART_TOWER = register(ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "reinforced_rampart_tower"),
        new com.solegendary.reignofnether.rampart.RampartTowerBuilding(com.solegendary.reignofnether.rampart.RampartBuilding.Kind.REINFORCED_TOWER, "Reinforced Tower", 800d, net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE, "textures/block/polished_deepslate.png"));

    private static <T extends Building> T register(ResourceLocation id, T building) {
        return Registry.register(ReignOfNetherRegistries.BUILDING, id, building);
    }

    public static void init() {}
}
