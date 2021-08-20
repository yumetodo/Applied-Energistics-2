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

package appeng.parts.misc;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

import appeng.me.storage.MEMonitorFluidStorage;

/**
 * Wraps an Fluid Handler in such a way that it can be used as an IMEInventory for fluids.
 *
 * @author BrockWS
 * @version rv6 - 22/05/2018
 * @since rv6 22/05/2018
 */
public class FluidHandlerAdapter extends MEMonitorFluidStorage {
    private final Runnable alertDevice;

    FluidHandlerAdapter(Storage<FluidVariant> fluidHandler, Runnable alertDevice) {
        super(fluidHandler);
        this.alertDevice = alertDevice;
    }

    @Override
    protected void onInsertOrExtract() {
        this.alertDevice.run();
    }
}
