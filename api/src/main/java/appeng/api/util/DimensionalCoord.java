/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.util;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.dimension.DimensionType;

/**
 * Represents a location in the Minecraft Universe
 */
public class DimensionalCoord extends WorldCoord {

    private final WorldAccess world;
    private final DimensionType dimension;

    public DimensionalCoord(final DimensionalCoord coordinate) {
        super(coordinate.x, coordinate.y, coordinate.z);
        this.world = coordinate.world;
        this.dimension = coordinate.dimension;
    }

    public DimensionalCoord(final BlockEntity tileEntity) {
        super(tileEntity);
        this.world = tileEntity.getWorld();
        this.dimension = this.world.getDimension();
    }

    public DimensionalCoord(final WorldAccess world, final int x, final int y, final int z) {
        super(x, y, z);
        this.world = world;
        this.dimension = world.getDimension();
    }

    public DimensionalCoord(final WorldAccess world, final BlockPos pos) {
        super(pos);
        this.world = world;
        this.dimension = world.getDimension();
    }

    @Override
    public DimensionalCoord copy() {
        return new DimensionalCoord(this);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ this.dimension.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof DimensionalCoord && this.isEqual((DimensionalCoord) obj);
    }

    @Override
    public String toString() {
        return "dimension=" + this.dimension + ", " + super.toString();
    }

    public boolean isInWorld(final WorldAccess world) {
        return this.world == world;
    }

    public WorldAccess getWorld() {
        return this.world;
    }

    private boolean isEqual(final DimensionalCoord c) {
        return this.x == c.x && this.y == c.y && this.z == c.z && c.world == this.world;
    }

}
