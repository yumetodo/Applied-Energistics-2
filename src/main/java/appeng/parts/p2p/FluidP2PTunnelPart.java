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

package appeng.parts.p2p;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Iterators;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.ExtractionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.InsertionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.PowerUnits;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;

public class FluidP2PTunnelPart extends CapabilityP2PTunnelPart<FluidP2PTunnelPart, Storage<FluidVariant>> {

    private static final P2PModels MODELS = new P2PModels("part/p2p/p2p_tunnel_fluids");

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    public FluidP2PTunnelPart(final ItemStack is) {
        super(is, FluidStorage.SIDED);
        inputHandler = new InputFluidHandler();
        outputHandler = new OutputFluidHandler();
        emptyHandler = Storage.empty();
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }

    private class InputFluidHandler implements InsertionOnlyStorage<FluidVariant> {
        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            long total = 0;

            final int outputTunnels = FluidP2PTunnelPart.this.getOutputs().size();
            final long amount = maxAmount;

            if (outputTunnels == 0 || amount == 0) {
                return 0;
            }

            final long amountPerOutput = amount / outputTunnels;
            long overflow = amountPerOutput == 0 ? amount : amount % amountPerOutput;

            for (FluidP2PTunnelPart target : FluidP2PTunnelPart.this.getOutputs()) {
                try (CapabilityGuard capabilityGuard = target.getAdjacentCapability()) {
                    final Storage<FluidVariant> output = capabilityGuard.get();
                    final long toSend = amountPerOutput + overflow;

                    final long received = output.insert(resource, toSend, transaction);

                    overflow = toSend - received;
                    total += received;
                }
            }

            queueTunnelDrain(PowerUnits.TR, total, transaction);

            return total;

        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator(TransactionContext transaction) {
            return Collections.emptyIterator();
        }
    }

    private class OutputFluidHandler implements ExtractionOnlyStorage<FluidVariant> {
        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            try (CapabilityGuard input = getInputCapability()) {
                long extracted = input.get().extract(resource, maxAmount, transaction);

                queueTunnelDrain(PowerUnits.TR, extracted, transaction);

                return extracted;
            }
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator(TransactionContext transaction) {
            try (CapabilityGuard input = getInputCapability()) {
                return Iterators.transform(
                        input.get().iterator(transaction),
                        PowerDrainingStorageView::new);
            }
        }
    }

    /**
     * Queues power drain when fluid is extracted through this.
     */
    private class PowerDrainingStorageView implements StorageView<FluidVariant> {
        private final StorageView<FluidVariant> delegate;

        public PowerDrainingStorageView(StorageView<FluidVariant> delegate) {
            this.delegate = delegate;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            long extracted = delegate.extract(resource, maxAmount, transaction);

            queueTunnelDrain(PowerUnits.TR, extracted, transaction);

            return extracted;
        }

        @Override
        public boolean isResourceBlank() {
            return delegate.isResourceBlank();
        }

        @Override
        public FluidVariant getResource() {
            return delegate.getResource();
        }

        @Override
        public long getAmount() {
            return delegate.getAmount();
        }

        @Override
        public long getCapacity() {
            return delegate.getCapacity();
        }
    }
}
