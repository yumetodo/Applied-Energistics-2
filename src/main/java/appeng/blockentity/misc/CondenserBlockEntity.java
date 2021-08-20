/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.blockentity.misc;

import java.util.Collections;
import java.util.Iterator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.InsertionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.CondenserOutput;
import appeng.api.config.Settings;
import appeng.api.implementations.items.IStorageComponent;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.IStorageMonitorableAccessor;
import appeng.api.storage.StorageChannels;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.blockentity.AEBaseInvBlockEntity;
import appeng.blockentity.inventory.AppEngInternalInventory;
import appeng.core.definitions.AEItems;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.inv.InvOperation;
import appeng.util.inv.WrapperChainedItemHandler;
import appeng.util.inv.WrapperFilteredItemHandler;
import appeng.util.inv.filter.AEItemFilters;

public class CondenserBlockEntity extends AEBaseInvBlockEntity implements IConfigManagerHost, IConfigurableObject {

    public static final int BYTE_MULTIPLIER = 8;

    private final ConfigManager cm = new ConfigManager(this);

    private final AppEngInternalInventory outputSlot = new AppEngInternalInventory(this, 1);
    private final AppEngInternalInventory storageSlot = new AppEngInternalInventory(this, 1);
    private final IItemHandler inputSlot = new CondenseItemHandler();
    private final Storage<FluidVariant> fluidHandler = new FluidHandler();
    private final MEHandler meHandler = new MEHandler();

    private final IItemHandler externalInv = new WrapperChainedItemHandler(this.inputSlot,
            new WrapperFilteredItemHandler(this.outputSlot, AEItemFilters.EXTRACT_ONLY));
    private final IItemHandler combinedInv = new WrapperChainedItemHandler(this.inputSlot, this.outputSlot,
            this.storageSlot);

    private double storedPower = 0;

    public CondenserBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
        this.cm.registerSetting(Settings.CONDENSER_OUTPUT, CondenserOutput.TRASH);
    }

    @Override
    public CompoundTag save(final CompoundTag data) {
        super.save(data);
        this.cm.writeToNBT(data);
        data.putDouble("storedPower", this.getStoredPower());
        return data;
    }

    @Override
    public void load(final CompoundTag data) {
        super.load(data);
        this.cm.readFromNBT(data);
        this.setStoredPower(data.getDouble("storedPower"));
    }

    public double getStorage() {
        final ItemStack is = this.storageSlot.getStackInSlot(0);
        if (!is.isEmpty() && is.getItem() instanceof IStorageComponent sc) {
            if (sc.isStorageComponent(is)) {
                return sc.getBytes(is) * BYTE_MULTIPLIER;
            }
        }
        return 0;
    }

    public void addPower(final double rawPower) {
        this.setStoredPower(this.getStoredPower() + rawPower);
        this.setStoredPower(Math.max(0.0, Math.min(this.getStorage(), this.getStoredPower())));

        final double requiredPower = this.getRequiredPower();
        final ItemStack output = this.getOutput();
        while (requiredPower <= this.getStoredPower() && !output.isEmpty() && requiredPower > 0) {
            if (this.canAddOutput(output)) {
                this.setStoredPower(this.getStoredPower() - requiredPower);
                this.addOutput(output);
            } else {
                break;
            }
        }
    }

    private boolean canAddOutput(final ItemStack output) {
        return this.outputSlot.insertItem(0, output, true).isEmpty();
    }

    /**
     * make sure you validate with canAddOutput prior to this.
     *
     * @param output to be added output
     */
    private void addOutput(final ItemStack output) {
        this.outputSlot.insertItem(0, output, false);
    }

    IItemHandler getOutputSlot() {
        return this.outputSlot;
    }

    private ItemStack getOutput() {

        return switch ((CondenserOutput) this.cm.getSetting(Settings.CONDENSER_OUTPUT)) {
            case MATTER_BALLS -> AEItems.MATTER_BALL.stack();
            case SINGULARITY -> AEItems.SINGULARITY.stack();
            default -> ItemStack.EMPTY;
        };
    }

    public double getRequiredPower() {
        return ((CondenserOutput) this.cm.getSetting(Settings.CONDENSER_OUTPUT)).requiredPower;
    }

    @Override
    public IItemHandler getInternalInventory() {
        return this.combinedInv;
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removed, final ItemStack added) {
        if (inv == this.outputSlot) {
            this.meHandler.outputChanged(added, removed);
        }
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Settings settingName, final Enum<?> newValue) {
        this.addPower(0);
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.cm;
    }

    public double getStoredPower() {
        return this.storedPower;
    }

    private void setStoredPower(final double storedPower) {
        this.storedPower = storedPower;
    }

    public IItemHandler getExternalInv() {
        return externalInv;
    }

    public Storage<FluidVariant> getFluidHandler() {
        return fluidHandler;
    }

    public MEHandler getMEHandler() {
        return meHandler;
    }

    @NotNull
    @Override
    public IItemHandler getItemHandlerForSide(@NotNull Direction side) {
        return this.externalInv;
    }

    private class CondenseItemHandler implements IItemHandler {

        @Override
        public int getSlots() {
            // We only expose the void slot
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot == 0;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            // The void slot never has any content
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0) {
                return stack;
            }
            if (!simulate && !stack.isEmpty()) {
                CondenserBlockEntity.this.addPower(stack.getCount());
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }
    }

    /**
     * A fluid handler that exposes a 1 bucket tank that can only be filled, and - when filled - will add power to this
     * condenser.
     */
    private class FluidHandler extends SnapshotParticipant<Double> implements InsertionOnlyStorage<FluidVariant> {
        private double pendingEnergy = 0;

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            // We allow up to a bucket per insert
            var amount = Math.min(FluidConstants.BUCKET, maxAmount);
            updateSnapshots(transaction);
            pendingEnergy += amount / (double) FluidConstants.BUCKET / StorageChannels.fluids().transferFactor();
            return amount;
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator(TransactionContext transaction) {
            return Collections.emptyIterator();
        }

        @Override
        protected Double createSnapshot() {
            return pendingEnergy;
        }

        @Override
        protected void readSnapshot(Double snapshot) {
            pendingEnergy = snapshot;
        }

        @Override
        protected void onFinalCommit() {
            CondenserBlockEntity.this.addPower(pendingEnergy);
            pendingEnergy = 0;
        }
    }

    /**
     * This is used to expose a fake ME subnetwork that is only composed of this condenser. The purpose of this is to
     * enable the condenser to override the {@link appeng.api.storage.IMEInventoryHandler#validForPass(int)} method to
     * make sure a condenser is only ever used if an item can't go anywhere else.
     */
    private class MEHandler implements IStorageMonitorableAccessor, IStorageMonitorable {
        private final CondenserItemInventory itemInventory = new CondenserItemInventory(CondenserBlockEntity.this);

        void outputChanged(ItemStack added, ItemStack removed) {
            this.itemInventory.updateOutput(added, removed);
        }

        @Nullable
        @Override
        public IStorageMonitorable getInventory(IActionSource src) {
            return this;
        }

        @Override
        public <T extends IAEStack<T>> IMEMonitor<T> getInventory(IStorageChannel<T> channel) {
            if (channel == StorageChannels.items()) {
                return (IMEMonitor<T>) this.itemInventory;
            } else {
                return new CondenserVoidInventory<>(CondenserBlockEntity.this, channel);
            }
        }
    }
}
