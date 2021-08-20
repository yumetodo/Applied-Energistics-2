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

package appeng.items.tools.powered.powersink;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraftforge.energy.IEnergyStorage;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnits;
import appeng.api.implementations.items.IAEItemPowerStorage;

/**
 * The capability provider to expose chargable items to other mods.
 */
public class PoweredItemCapabilities implements IEnergyStorage {

    private final ContainerItemContext context;

    public PoweredItemCapabilities(ContainerItemContext context) {
        this.context = context;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        var current = context.getItemVariant();
        var inserted = 0;
        if (current.getItem() instanceof IAEItemPowerStorage powerStorage) {
            var is = current.toStack();

            var convertedOffer = PowerUnits.TR.convertTo(PowerUnits.AE, maxReceive);
            var overflow = powerStorage.injectAEPower(is, convertedOffer,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE);
            inserted = maxReceive - (int) PowerUnits.AE.convertTo(PowerUnits.TR, overflow);

            if (!simulate && inserted > 0) {
                try (var tx = Transaction.openOuter()) {
                    context.exchange(ItemVariant.of(is), 1, tx);
                }
            }
        }

        return inserted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        var current = context.getItemVariant();
        if (current.getItem() instanceof IAEItemPowerStorage powerStorage) {
            return (int) PowerUnits.AE.convertTo(PowerUnits.TR, powerStorage.getAECurrentPower(current.toStack()));
        }

        return 0;
    }

    @Override
    public int getMaxEnergyStored() {
        var current = context.getItemVariant();
        if (current.getItem() instanceof IAEItemPowerStorage powerStorage) {
            return (int) PowerUnits.AE.convertTo(PowerUnits.TR, powerStorage.getAEMaxPower(current.toStack()));
        }
        return 0;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

}
