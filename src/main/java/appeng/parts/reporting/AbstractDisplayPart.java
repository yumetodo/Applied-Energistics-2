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

package appeng.parts.reporting;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import appeng.core.AppEng;
import appeng.items.parts.PartModels;

/**
 * A more sophisticated part overlapping all 3 textures.
 *
 * Subclass this if you need want a new part and need all 3 textures. For more
 * concrete implementations, the direct abstract subclasses might be a better
 * alternative.
 *
 * @author AlgorithmX2
 * @author yueh
 * @version rv3
 * @since rv3
 */
public abstract class AbstractDisplayPart extends AbstractReportingPart {

    // The base chassis of all display parts
    @PartModels
    protected static final Identifier MODEL_BASE = new Identifier(AppEng.MOD_ID, "part/display_base");

    // Models that contain the status indicator light
    @PartModels
    protected static final Identifier MODEL_STATUS_OFF = new Identifier(AppEng.MOD_ID,
            "part/display_status_off");
    @PartModels
    protected static final Identifier MODEL_STATUS_ON = new Identifier(AppEng.MOD_ID,
            "part/display_status_on");
    @PartModels
    protected static final Identifier MODEL_STATUS_HAS_CHANNEL = new Identifier(AppEng.MOD_ID,
            "part/display_status_has_channel");

    public AbstractDisplayPart(final ItemStack is) {
        super(is, true);
    }

    @Override
    public boolean isLightSource() {
        return false;
    }

}
