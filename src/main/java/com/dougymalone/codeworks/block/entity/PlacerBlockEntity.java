package com.dougymalone.codeworks.block.entity;

import com.dougymalone.codeworks.registry.ModBlockEntities;
import com.dougymalone.codeworks.script.ScriptException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 1-slot programmable placer.
 * <p>
 * TODO(Create): accept intake from Create belts/funnels into {@link #stored}
 * once Codeworks is wired as a Create addon. For now, player insert only
 * (right-click with a block item; shift-click empty hand to extract).
 */
public class PlacerBlockEntity extends ScriptedMachineBlockEntity {
	public static final String DEFAULT_SCRIPT = """
		// Codeworks Placer — same bus as Breaker + Channel Adapter
		channel = "line1";
		speed = 8;
		range = 1;
		string signal = Receive("broke");
		if (signal != "" && HasStoredBlock()) {
		  if (!GetExtended("gate")) {
		    Pulse("gate");
		    Wait(8);
		  }
		  if (Place()) {
		    Send("broke", "");
		    Log("placed after " + signal);
		  } else {
		    Log("cannot place");
		  }
		  Wait(10);
		} else if (HasStoredBlock() && Sense() == "air") {
		  Place();
		  Wait(20);
		} else {
		  Wait(10);
		}
		""";

	/** Single inventory slot — one stack of one block item at a time. */
	private ItemStack stored = ItemStack.EMPTY;

	public PlacerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.PLACER, pos, state, DEFAULT_SCRIPT);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, PlacerBlockEntity placer) {
		placer.tickServer(level, pos, state);
	}

	@Override
	protected String machineTypeId() {
		return "placer";
	}

	@Override
	protected String defaultScript() {
		return DEFAULT_SCRIPT;
	}

	public ItemStack getStored() {
		return stored;
	}

	public boolean tryInsertFromPlayer(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!(held.getItem() instanceof BlockItem) || held.isEmpty()) {
			return false;
		}
		if (!stored.isEmpty()) {
			if (ItemStack.isSameItemSameComponents(stored, held) && stored.getCount() < getMaxStoreCount(stored)) {
				int space = getMaxStoreCount(stored) - stored.getCount();
				int moved = Math.min(space, held.getCount());
				stored.grow(moved);
				held.shrink(moved);
				setStatus("Stored " + stored.getCount() + "× " + stored.getHoverName().getString());
				setChanged();
				return true;
			}
			return false;
		}
		ItemStack taken = held.split(1);
		stored = taken;
		setStatus("Stored " + stored.getHoverName().getString());
		setChanged();
		return true;
	}

	public void extractToPlayer(Player player) {
		if (stored.isEmpty()) {
			setStatus("Empty");
			setChanged();
			return;
		}
		ItemStack toGive = stored.copy();
		if (!player.addItem(toGive)) {
			Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), toGive);
		}
		stored = ItemStack.EMPTY;
		setStatus("Extracted");
		setChanged();
	}

	private static int getMaxStoreCount(ItemStack stack) {
		// v1: one block at a time (stack size 1) — keeps the contract simple for Create later
		return 1;
	}

	@Override
	public boolean hasStoredBlock() {
		return !stored.isEmpty() && stored.getItem() instanceof BlockItem;
	}

	@Override
	public boolean place() throws ScriptException {
		if (!(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		if (isOnCooldown()) {
			return false;
		}
		if (!hasStoredBlock()) {
			setStatus("No block stored");
			setChanged();
			return false;
		}
		BlockPos target = frontPos();
		BlockState existing = serverLevel.getBlockState(target);
		if (!existing.canBeReplaced()) {
			setStatus("Front blocked");
			setChanged();
			return false;
		}
		BlockItem blockItem = (BlockItem) stored.getItem();
		Direction face = facing();
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), face.getOpposite(), target, false);
		BlockPlaceContext context = new MachinePlaceContext(serverLevel, stored.copyWithCount(1), hit);
		BlockState toPlace = blockItem.getBlock().getStateForPlacement(context);
		if (toPlace == null) {
			toPlace = blockItem.getBlock().defaultBlockState();
		}
		if (!toPlace.canSurvive(serverLevel, target)) {
			setStatus("Cannot survive there");
			setChanged();
			return false;
		}
		boolean placed = serverLevel.setBlock(target, toPlace, 3);
		if (!placed) {
			return false;
		}
		stored.shrink(1);
		if (stored.isEmpty()) {
			stored = ItemStack.EMPTY;
		}
		startActionCooldown();
		setStatus("Placed");
		setChanged();
		return true;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !stored.isEmpty()) {
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stored);
			stored = ItemStack.EMPTY;
		}
		super.preRemoveSideEffects(pos, state);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		stored = input.read("Stored", ItemStack.CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!stored.isEmpty()) {
			output.store("Stored", ItemStack.CODEC, stored);
		}
	}

	/** Playerless place context so machines can call getStateForPlacement. */
	private static final class MachinePlaceContext extends BlockPlaceContext {
		private MachinePlaceContext(Level level, ItemStack stack, BlockHitResult hit) {
			super(level, null, InteractionHand.MAIN_HAND, stack, hit);
		}
	}
}
