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

package appeng.items.misc;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import appeng.items.AEBaseItem;
import appeng.util.Platform;

/**
 * Dummy item to display the fluid Icon
 *
 * @author DrummerMC
 * @version rv6 - 2018-01-22
 * @since rv6 2018-01-22
 */
public class FluidDummyItem extends AEBaseItem {

    public FluidDummyItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return Platform.getDescriptionId(this.getFluid(stack));
    }

    public FluidVariant getFluid(ItemStack is) {
        if (is.hasTag()) {
            return FluidVariant.fromNbt(is.getTag());
        }
        return FluidVariant.blank();
    }

    public void setFluid(ItemStack is, FluidVariant fluid) {
        if (fluid.isBlank()) {
            is.setTag(null);
        } else {
            is.setTag(fluid.toNbt());
        }
    }

    @Override
    public void fillItemCategory(CreativeModeTab group, NonNullList<ItemStack> items) {
        // Don't show this item in CreativeTabs
    }
}
