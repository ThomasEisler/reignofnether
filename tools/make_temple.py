# Placeholder model of the Temple (7 x 7, ground row at y = 0), until a real one is built in game.
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
import nbt

W = 7
b = {}
for x in range(W):
    for z in range(W):
        b[(x, 0, z)] = 'minecraft:polished_andesite'          # ground row
        edge = x in (0, W - 1) or z in (0, W - 1)
        corner = x in (0, W - 1) and z in (0, W - 1)
        for y in range(1, 5):
            if corner:
                b[(x, y, z)] = 'minecraft:quartz_pillar[axis=y]'
            elif edge:
                door = z == 0 and x == 3 and y <= 2
                window = y == 3 and (x == 3 or z == 3)
                if not door:
                    b[(x, y, z)] = 'minecraft:glass_pane' if window else 'minecraft:smooth_quartz'
        if not edge and (x, z) != (3, 3):
            b[(x, 1, z)] = 'minecraft:white_carpet'
# stepped roof and a bell under a little tower
for y, inset in ((5, 0), (6, 1), (7, 2)):
    for x in range(inset, W - inset):
        for z in range(inset, W - inset):
            b[(x, y, z)] = 'minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]' if y == 7 else 'minecraft:smooth_quartz'
b[(3, 1, 3)] = 'minecraft:gold_block'
b[(3, 2, 3)] = 'minecraft:candle[candles=3,lit=true,waterlogged=false]'
b[(3, 8, 3)] = 'minecraft:gold_block'
b[(3, 4, 1)] = 'minecraft:bell[attachment=ceiling,facing=north,powered=false]'

root = nbt.structure(b, 3465)
for d in ('src/main/resources/data/reignofnether/structures', 'src/main/resources/assets/reignofnether/structures'):
    nbt.write(os.path.join(d, 'temple.nbt'), root)
print(len(b), 'blocks')
