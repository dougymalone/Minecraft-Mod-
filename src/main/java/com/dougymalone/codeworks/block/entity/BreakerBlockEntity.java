package com.dougymalone.codeworks.block.entity;

import com.dougymalone.codeworks.block.BreakerBlock;
import com.dougymalone.codeworks.channel.ChannelIds;
import com.dougymalone.codeworks.channel.MachineIndex;
import com.dougymalone.codeworks.network.OpenBreakerChannelPayload;
import com.dougymalone.codeworks.registry.ModBlockEntities;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
/**
 * Actuator only — no local script. Channel selector UI; Central Command calls {@link #tryHarvest()}.
 */
public class BreakerBlockEntity extends BlockEntity {
	private static final int DEFAULT_COOLDOWN = 11; // ~speed 10

	private String channel = "";
	private int harvestCooldown;
	private boolean indexed;

	public BreakerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BREAKER, pos, state);
	}

	public String getChannel() {
		return channel;
	}

	public void setChannelFromPlayer(String raw) {
		MachineIndex.unregisterBreaker(this);
		channel = ChannelIds.sanitize(raw);
		MachineIndex.registerBreaker(this);
		indexed = true;
		setChanged();
	}

	public void openChannelSelector(ServerPlayer player) {
		String known = level == null ? "" : String.join("\n", MachineIndex.knownChannels(level));
		ServerPlayNetworking.send(player, new OpenBreakerChannelPayload(worldPosition, channel, known));
	}

	public boolean tryHarvest() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		ensureIndexed();
		if (harvestCooldown > 0) {
			return false;
		}
		Direction facing = getBlockState().getValue(BreakerBlock.FACING);
		BlockPos targetPos = worldPosition.relative(facing);
		BlockState target = serverLevel.getBlockState(targetPos);
		if (target.isAir() || isUnbreakable(serverLevel, targetPos, target)) {
			return false;
		}
		boolean destroyed = serverLevel.destroyBlock(targetPos, true, null);
		if (destroyed) {
			harvestCooldown = DEFAULT_COOLDOWN;
			setChanged();
		}
		return destroyed;
	}

	/** Called each server tick so cooldown drains even when idle. */
	public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, BreakerBlockEntity breaker) {
		breaker.ensureIndexed();
		if (breaker.harvestCooldown > 0) {
			breaker.harvestCooldown--;
		}
	}

	private void ensureIndexed() {
		if (!indexed && level != null && !level.isClientSide()) {
			MachineIndex.registerBreaker(this);
			indexed = true;
		}
	}

	private static boolean isUnbreakable(ServerLevel level, BlockPos pos, BlockState state) {
		return state.getDestroySpeed(level, pos) < 0.0F
			|| state.is(Blocks.BEDROCK)
			|| state.is(Blocks.BARRIER)
			|| state.is(BlockTags.FEATURES_CANNOT_REPLACE);
	}

	@Override
	public void setRemoved() {
		MachineIndex.unregisterBreaker(this);
		indexed = false;
		super.setRemoved();
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		indexed = false;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		channel = ChannelIds.sanitize(input.getStringOr("Channel", ""));
		harvestCooldown = input.getIntOr("HarvestCooldown", 0);
		indexed = false;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("Channel", channel);
		output.putInt("HarvestCooldown", harvestCooldown);
	}
}
