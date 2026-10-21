package com.solegendary.reignofnether.rampart;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// One block of a rampart gate: a portcullis panel across the wall when closed (blocks everything, full height so it
// can't be jumped), slid away into the wall when open (no collision, nothing drawn). Opened and closed by
// RampartServerEvents when the owner's units come near.
public class RampartGateBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS; // direction of the wall
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    private static final VoxelShape PANEL_X = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape PANEL_Z = Block.box(6, 0, 0, 10, 16, 16);

    public RampartGateBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, OPEN);
    }

    // a shut gate is aimed at as a whole block (the thin panel was hard to click from the RTS camera, so it couldn't be
    // attacked); it doesn't hide its neighbours' faces though (see getOcclusionShape)
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (state.getValue(OPEN))
            return Shapes.empty();
        return Shapes.block();
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(OPEN))
            return Shapes.empty();
        return state.getValue(AXIS) == Direction.Axis.X ? PANEL_X : PANEL_Z;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
