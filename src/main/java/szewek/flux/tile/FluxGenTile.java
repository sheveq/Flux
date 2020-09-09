package szewek.flux.tile;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.LockableTileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.IIntArray;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullSupplier;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import szewek.fl.util.FluidsUtil;
import szewek.fl.util.IntPair;
import szewek.flux.F;
import szewek.flux.FluxCfg;
import szewek.flux.container.FluxGenContainer;
import szewek.flux.data.FluxGenValues;
import szewek.flux.energy.EnergyCache;
import szewek.flux.tile.part.GeneratorEnergy;
import szewek.flux.util.FieldIntArray;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FluxGenTile extends LockableTileEntity implements ITickableTileEntity {
	public static final int fluidCap = 4000;
	private final EnergyCache energyCache = new EnergyCache(this);
	private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
	private final AtomicBoolean isDirty = new AtomicBoolean();
	private final Tank tank = new Tank(isDirty);
	private final GeneratorEnergy energy = new GeneratorEnergy(1_000_000);
	private int workTicks, maxWork, energyGen, workSpeed;
	private boolean isReady;
	public boolean receivedRedstone;
	protected final IIntArray tileData = FieldIntArray.of(this, new String[]{"workTicks", "maxWork", "energyGen", "workSpeed"}, new FieldIntArray.Extended() {
		@Override
		public int translate(int i) {
			return i - 6;
		}

		@Override
		public int get(int i) {
			if (i < 2) {
				return energy.getEnergy16Bit(i == 1);
			}
			return tank.getData(i - 2);
		}

		@Override
		public void set(int i, int v) {
			if (i < 2) {
				energy.setEnergy16Bit(i == 1, v);
			} else {
				tank.setData(i - 2, v);
			}
		}

		@Override
		public int size() {
			return 10;
		}
	});

	public FluxGenTile() {
		super(F.T.FLUXGEN);
	}

	@Override
	public void fromTag(BlockState blockState, CompoundNBT compound) {
		super.fromTag(blockState, compound);
		energy.readNBT(compound);
		workTicks = compound.getInt("WorkTicks");
		maxWork = compound.getInt("MaxWork");
		energyGen = compound.getInt("Gen");
		workSpeed = compound.getInt("WorkSpeed");
		ItemStackHelper.loadAllItems(compound, items);
		List<FluidStack> fluidList = NonNullList.withSize(tank.fluids.length, FluidStack.EMPTY);
		FluidsUtil.loadAllFluids(compound, fluidList);
		for (int i = 0; i < tank.fluids.length; i++) {
			tank.fluids[i] = fluidList.get(i);
		}
	}

	@Override
	public CompoundNBT write(CompoundNBT compound) {
		super.write(compound);
		energy.writeNBT(compound);
		compound.putInt("WorkTicks", workTicks);
		compound.putInt("MaxWork", maxWork);
		compound.putInt("Gen", energyGen);
		compound.putInt("WorkSpeed", workSpeed);
		ItemStackHelper.saveAllItems(compound, items);
		FluidsUtil.saveAllFluids(compound, Arrays.asList(tank.fluids), true);

		return compound;
	}

	@Override
	public void tick() {
		assert world != null;
		if (world.isRemote) return;
		if (!isReady) {
			if (world.getRedstonePowerFromNeighbors(pos) > 0)
				receivedRedstone = true;
			isReady = true;
		}
		if (!receivedRedstone) {
			if ((maxWork == 0 && ForgeHooks.getBurnTime(items.get(0)) > 0) || workTicks >= maxWork) {
				workTicks = 0;
				maxWork = updateWork();
			} else if (energy.generate(energyGen)) {
				workTicks += workSpeed;
				if (maxWork <= workTicks) {
					maxWork = 0;
					energyGen = 0;
				}
				markDirty();
			}
		}
		energy.share(energyCache);
		if (isDirty.getAndSet(false)) markDirty();
	}

	private int updateWork() {
		ItemStack fuel = items.get(0);
		int f = ForgeHooks.getBurnTime(fuel);
		if (f == 0) return 0;
		ItemStack catalyst = items.get(1);
		energyGen = FluxCfg.ENERGY.fluxGenBaseEnergy.get();
		IntPair genCat = FluxGenValues.CATALYSTS.get(catalyst.getItem());
		if (genCat.r <= catalyst.getCount()) {
			energyGen *= genCat.l;
			if (genCat.r > 0) catalyst.grow(-genCat.r);
		}
		IntPair genHot = FluxGenValues.HOT_FLUIDS.get(tank.fluids[0].getFluid());
		if (genHot.r <= tank.fluids[0].getAmount()) {
			f *= genHot.l;
			if (genHot.r > 0) tank.fluids[0].grow(-genHot.r);
		}
		IntPair genCold = FluxGenValues.COLD_FLUIDS.get(tank.fluids[1].getFluid());
		if (genCold.r <= tank.fluids[1].getAmount()) {
			workSpeed = genCold.l < genCat.l ? genCat.l - genCold.l : 1;
			if (genCold.r > 0) tank.fluids[1].grow(-genCold.r);
		} else {
			workSpeed = 1;
		}
		fuel.grow(-1);
		isDirty.set(true);
		return f;
	}

	public Tank getTank() {
		return tank;
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction dir) {
		if (!removed) {
			if (cap == CapabilityEnergy.ENERGY) {
				return energy.lazyCast();
			} else if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
				return tank.lazy.cast();
			}
		}
		return super.getCapability(cap, dir);
	}

	@Override
	public void remove() {
		super.remove();
		energyCache.clear();
		energy.invalidate();
		tank.lazy.invalidate();
	}

	@Override
	public int getSizeInventory() {
		return 2;
	}

	@Override
	public boolean isEmpty() {
		return items.get(0).isEmpty() && items.get(1).isEmpty();
	}

	@Override
	public ItemStack getStackInSlot(int i) {
		if (i < 0 || i >= items.size())
			throw new IndexOutOfBoundsException("Getting slot " + i + " outside range [0," + items.size() + ")");
		return items.get(i);
	}

	@Override
	public ItemStack decrStackSize(int i, int count) {
		return i >= 0 && i <= items.size() && count > 0 && !items.get(i).isEmpty() ? items.get(i).split(count) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeStackFromSlot(int i) {
		if (i >= 0 && i <= items.size()) {
			ItemStack stack = items.get(i);
			items.set(i, ItemStack.EMPTY);
			return stack;
		}
		return ItemStack.EMPTY;
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack stack) {
		if (i >= 0 && i <= items.size()) {
			if (stack.getCount() > 64) stack.setCount(64);
			items.set(i, stack);
		}
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public boolean isUsableByPlayer(PlayerEntity player) {
		return player.world.getTileEntity(pos) == this && pos.distanceSq(player.getPositionVec(), true) <= 64.0;
	}

	@Override
	public void clear() {
		items.clear();
	}

	@Override
	protected ITextComponent getDefaultName() {
		return new TranslationTextComponent("container.flux.fluxgen");
	}

	@Override
	protected Container createMenu(int id, PlayerInventory playerInv) {
		return new FluxGenContainer(id, playerInv, this, tileData);
	}

	static class Tank implements IFluidHandler, NonNullSupplier<IFluidHandler> {
		private final FluidStack[] fluids = {FluidStack.EMPTY, FluidStack.EMPTY};
		private final LazyOptional<IFluidHandler> lazy = LazyOptional.of(this);
		private final AtomicBoolean isDirty;

		Tank(AtomicBoolean dirty) {
			isDirty = dirty;
		}

		private int getData(int i) {
			if (i >= 4) return 0;
			FluidStack fs = fluids[i >> 1];
			if (i % 2 == 0) {
				return ((ForgeRegistry<Fluid>) ForgeRegistries.FLUIDS).getID(fs.getFluid());
			}
			return fs.getAmount();
		}
		private void setData(int i, int v) {
			if (i >= 4) return;
			FluidStack fs = fluids[i >> 1];
			if (i % 2 == 0) {
				fluids[i >> 1] = new FluidStack(((ForgeRegistry<Fluid>) ForgeRegistries.FLUIDS).getValue(v), fs.getAmount());
			} else {
				if (!fs.isEmpty()) {
					fs.setAmount(v);
				}
			}
		}

		@Override
		public int getTanks() {
			return 2;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return fluids[tank];
		}

		@Override
		public int getTankCapacity(int tank) {
			return fluidCap;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return false;
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (resource.getAmount() <= 0) {
				return 0;
			}
			int s;
			if (FluxGenValues.HOT_FLUIDS.has(resource.getFluid())) {
				s = 0;
			} else if (FluxGenValues.COLD_FLUIDS.has(resource.getFluid())) {
				s = 1;
			} else {
				return 0;
			}
			FluidStack fs = fluids[s];
			if (!fs.isEmpty() && !fs.isFluidEqual(resource)) {
				return 0;
			}
			int l = fluidCap - fs.getAmount();
			if (l > resource.getAmount()) {
				l = resource.getAmount();
			}
			if (l > 0 && action.execute()) {
				if (fs.isEmpty()) {
					fluids[s] = resource.copy();
				} else {
					fs.grow(l);
				}
				isDirty.set(true);
			}
			return l;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return FluidStack.EMPTY;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return FluidStack.EMPTY;
		}

		@Nonnull
		@Override
		public IFluidHandler get() {
			return this;
		}
	}
}
