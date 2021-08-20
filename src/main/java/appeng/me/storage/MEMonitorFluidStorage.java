/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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

package appeng.me.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.StorageFilter;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.StorageChannels;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import appeng.util.fluid.AEFluidStack;

public class MEMonitorFluidStorage implements IMEMonitor<IAEFluidStack>, ITickingMonitor {
    private final Storage<FluidVariant> handler;
    private IItemList<IAEFluidStack> frontBuffer = StorageChannels.fluids().createList();
    private IItemList<IAEFluidStack> backBuffer = StorageChannels.fluids().createList();
    private final HashMap<IMEMonitorHandlerReceiver<IAEFluidStack>, Object> listeners = new HashMap<>();
    private IActionSource mySource;
    private StorageFilter mode = StorageFilter.EXTRACTABLE_ONLY;

    public MEMonitorFluidStorage(final Storage<FluidVariant> handler) {
        this.handler = handler;
    }

    // Called after insert or extract, by default it calls onTick to rebuild the cache instantly.
    protected void onInsertOrExtract() {
        this.onTick();
    }

    @Override
    public void addListener(final IMEMonitorHandlerReceiver<IAEFluidStack> l, final Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    @Override
    public void removeListener(final IMEMonitorHandlerReceiver<IAEFluidStack> l) {
        this.listeners.remove(l);
    }

    @Override
    public IAEFluidStack injectItems(final IAEFluidStack input, final Actionable type, final IActionSource src) {

        try (var tx = Transaction.openOuter()) {
            var filled = this.handler.insert(input.getFluid(), input.getStackSize(), tx);

            if (filled == 0) {
                return input.copy();
            }

            if (type == Actionable.MODULATE) {
                tx.commit();
                this.onInsertOrExtract();
            }

            if (filled >= input.getStackSize()) {
                return null;
            }

            return input.copy().setStackSize(input.getStackSize() - filled);
        }

    }

    @Override
    public IAEFluidStack extractItems(final IAEFluidStack request, final Actionable type, final IActionSource src) {

        try (var tx = Transaction.openOuter()) {

            var drained = this.handler.extract(request.getFluid(), request.getStackSize(), tx);

            if (drained <= 0) {
                return null;
            }

            if (type == Actionable.MODULATE) {
                tx.commit();
                this.onInsertOrExtract();
            }

            return request.copy().setStackSize(drained);
        }

    }

    @Override
    public IStorageChannel<IAEFluidStack> getChannel() {
        return StorageChannels.fluids();
    }

    @Override
    public TickRateModulation onTick() {
        // Flip back & front buffer and start building a new list
        var tmp = backBuffer;
        backBuffer = frontBuffer;
        frontBuffer = tmp;
        frontBuffer.resetStatus();

        // Rebuild the front buffer
        try (var tx = Transaction.openOuter()) {
            for (var view : this.handler.iterable(tx)) {
                if (view.isResourceBlank()) {
                    continue;
                }

                // Skip resources that cannot be extracted if that filter was enabled
                if (getMode() == StorageFilter.EXTRACTABLE_ONLY) {
                    // Use an inner TX to prevent two tanks that can be extracted from only mutually exclusively
                    // from not being influenced by our extraction test here.
                    try (var innerTx = tx.openNested()) {
                        var extracted = view.extract(view.getResource(), FluidConstants.DROPLET, innerTx);
                        // If somehow extracting the minimal amount doesn't work, check if everything could be extracted
                        // because the tank might have a minimum (or fixed) allowed extraction amount.
                        if (extracted == 0) {
                            extracted = view.extract(view.getResource(), view.getAmount(), innerTx);
                        }
                        if (extracted == 0) {
                            // We weren't able to simulate extraction of any fluid, so skip this one
                            continue;
                        }
                    }
                }

                frontBuffer.addStorage(AEFluidStack.of(view.getResource(), view.getAmount()));
            }
        }

        // Diff the front-buffer against the backbuffer
        var changes = new ArrayList<IAEFluidStack>();
        for (var stack : frontBuffer) {
            var old = backBuffer.findPrecise(stack);
            if (old == null) {
                changes.add(stack.copy()); // new entry
            } else if (old.getStackSize() != stack.getStackSize()) {
                var change = stack.copy();
                change.decStackSize(old.getStackSize());
                changes.add(change); // changed amount
            }
        }
        // Account for removals
        for (var oldStack : backBuffer) {
            if (frontBuffer.findPrecise(oldStack) == null) {
                changes.add(oldStack.copy().setStackSize(-oldStack.getStackSize()));
            }
        }

        if (!changes.isEmpty()) {
            this.postDifference(changes);
        }

        return !changes.isEmpty() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    private void postDifference(final Iterable<IAEFluidStack> a) {
        if (a != null) {
            final Iterator<Entry<IMEMonitorHandlerReceiver<IAEFluidStack>, Object>> i = this.listeners.entrySet()
                    .iterator();
            while (i.hasNext()) {
                final Entry<IMEMonitorHandlerReceiver<IAEFluidStack>, Object> l = i.next();
                final IMEMonitorHandlerReceiver<IAEFluidStack> key = l.getKey();
                if (key.isValid(l.getValue())) {
                    key.postChange(this, a, this.getActionSource());
                } else {
                    i.remove();
                }
            }
        }
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(final IAEFluidStack input) {
        return false;
    }

    @Override
    public boolean canAccept(final IAEFluidStack input) {
        return true;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(final int i) {
        return true;
    }

    @Override
    public IItemList<IAEFluidStack> getAvailableItems(IItemList<IAEFluidStack> out) {
        for (var stack : frontBuffer) {
            out.addStorage(stack);
        }

        return out;
    }

    @Override
    public IItemList<IAEFluidStack> getStorageList() {
        return this.frontBuffer;
    }

    private StorageFilter getMode() {
        return this.mode;
    }

    public void setMode(final StorageFilter mode) {
        this.mode = mode;
    }

    private IActionSource getActionSource() {
        return this.mySource;
    }

    @Override
    public void setActionSource(final IActionSource mySource) {
        this.mySource = mySource;
    }

}
