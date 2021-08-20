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

package appeng.capabilities;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;

import appeng.api.storage.IStorageMonitorableAccessor;
import appeng.core.AppEng;

/**
 * Utility class that holds various capabilities, both by AE2 and other Mods.
 */
public final class Capabilities {

    private Capabilities() {
    }

    public static final BlockApiLookup<IItemHandler, Direction> ITEM = BlockApiLookup.get(
            new ResourceLocation("forge:item"), IItemHandler.class, Direction.class);

    public static final BlockApiLookup<IEnergyStorage, Direction> ENERGY = BlockApiLookup.get(
            new ResourceLocation("forge:energy"), IEnergyStorage.class, Direction.class);

    public static final ItemApiLookup<IEnergyStorage, ContainerItemContext> ENERGY_STORAGE = ItemApiLookup.get(
            new ResourceLocation("forge:energy"), IEnergyStorage.class, ContainerItemContext.class);

    public static final BlockApiLookup<IStorageMonitorableAccessor, Direction> GRID_STORAGE_ACCESSOR = BlockApiLookup
            .get(AppEng.makeId("storage"), IStorageMonitorableAccessor.class, Direction.class);

}
