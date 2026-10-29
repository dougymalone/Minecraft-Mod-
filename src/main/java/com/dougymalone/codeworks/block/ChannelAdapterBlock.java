package com.dougymalone.codeworks.block;

import com.dougymalone.codeworks.block.entity.ChannelAdapterBlockEntity;
import com.dougymalone.codeworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Thin panel attached to a host block face (piston, lamp, etc.).
 * {@link #FACING} points toward the host. Emits redstone when pulsed or powered
 * over the shared wireless bus used by Breaker, Placer, and future machines.
 */
public class ChannelAdapterBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
	public static final BooleanProperty POWERED = BooleanProperty.create("powered");

	private static final VoxelShape SHAPE_NORTH = Block.box(2.0, 2.0, 14.0, 14.0, 14.0, 16.0);
	private static final VoxelShape SHAPE_SOUTH = Block.box(2.0, 2.0, 0.0, 14.0, 14.0, 2.0);
	private static final VoxelShape SHAPE_WEST = Block.box(14.0, 2.0, 2.0, 16.0, 14.0, 14.0);
	private static final VoxelShape SHAPE_EAST = Block.box(0.0, 2.0, 2.0, 2.0, 14.0, 14.0);
	private static final VoxelShape SHAPE_UP = Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0);
	private static final VoxelShape SHAPE_DOWN = Block.box(2.0, 14.0, 2.0, 14.0, 16.0, 14.0);

	public ChannelAdapterBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case NORTH -> SHAPE_NORTH;
			case SOUTH -> SHAPE_SOUTH;
			case WEST -> SHAPE_WEST;
			case EAST -> SHAPE_EAST;
			case UP -> SHAPE_UP;
			case DOWN -> SHAPE_DOWN;
		};
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		// Clicked host face → adapter placed in adjacent cell, facing back toward host.
		Direction towardHost = context.getClickedFace().getOpposite();
		BlockPos placePos = context.getClickedPos();
		BlockPos hostPos = placePos.relative(towardHost);
		if (!canSurviveOn(context.getLevel(), hostPos, towardHost.getOpposite())) {
			return null;
		}
		return this.defaultBlockState().setValue(FACING, towardHost).setValue(POWERED, false);
	}

	private static boolean canSurviveOn(LevelReader level, BlockPos hostPos, Direction outwardFromHost) {
		BlockState host = level.getBlockState(hostPos);
		return !host.isAir() && host.isFaceSturdy(level, hostPos, outwardFromHost);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction towardHost = state.getValue(FACING);
		return canSurviveOn(level, pos.relative(towardHost), towardHost.getOpposite());
	}

	@Override
	protected BlockState updateShape(
		BlockState state,
		LevelReader level,
		ScheduledTickAccess ticks,
		BlockPos pos,
		Direction direction,
		BlockPos neighborPos,
		BlockState neighborState,
		RandomSource random
	) {
		if (direction == state.getValue(FACING) && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ChannelAdapterBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
			&& level.getBlockEntity(pos) instanceof ChannelAdapterBlockEntity adapter) {
			adapter.openConfigurator(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return state.getValue(POWERED) ? 15 : 0;
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		if (!state.getValue(POWERED)) {
			return 0;
		}
		// Strong power into the host face.
		return direction == state.getValue(FACING) ? 15 : 0;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
		if (!oldState.is(state.getBlock())) {
			updateNeighbors(level, pos, state);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
		updateNeighbors(level, pos, state);
	}

	@Override
	protected void neighborChanged(
		BlockState state,
		Level level,
		BlockPos pos,
		Block neighbor,
		@Nullable Orientation orientation,
		boolean movedByPiston
	) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ChannelAdapterBlockEntity adapter) {
			adapter.onNeighborChanged();
		}
	}

	public static void updateNeighbors(Level level, BlockPos pos, BlockState state) {
		Direction towardHost = state.getValue(FACING);
		Direction up = towardHost.getAxis().isHorizontal() ? Direction.UP : Direction.NORTH;
		Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, towardHost.getOpposite(), up);
		level.updateNeighborsAt(pos, state.getBlock(), orientation);
		level.updateNeighborsAt(pos.relative(towardHost), state.getBlock(), orientation);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.CHANNEL_ADAPTER, ChannelAdapterBlockEntity::serverTick);
	}
}
