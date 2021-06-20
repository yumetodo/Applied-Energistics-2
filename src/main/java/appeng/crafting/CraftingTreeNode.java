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

package appeng.crafting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;

import net.minecraft.world.World;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.core.Api;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/**
 * A crafting tree node is what represents a single requested stack in the crafting process. It can either be the
 * top-level requested stack (slot is then -1, parent is null), or a stack used in a pattern (slot is then the position
 * of this stack in the pattern, parent is the parent node).
 */
public class CraftingTreeNode {

    /**
     * Stack of this node. Note: the count is not necessarily correct at construction time, it's set in request()
     */
    private final IAEItemStack what;
    /**
     * Parent process, or null for the top level node.
     */
    private final CraftingTreeProcess parent;
    /**
     * Child pattern nodes that can produce this stack.
     */
    private final ArrayList<CraftingTreeProcess> nodes = new ArrayList<>();
    private final boolean canEmit;
    /**
     * Slot in the parent process, or -1 for the top level node.
     */
    private final int slot;
    private final CraftingJob job;
    private final World world;
    /**
     * List of things that need to be extracted from the network inventory when the job starts, for this node. This is a
     * heuristic that sometimes extracts too many items. See also {@code leftoverNetworkInventory} in
     * {@link CraftingJob}.
     */
    private final IItemList<IAEItemStack> usedNetworkItems = Api.instance().storage()
            .getStorageChannel(IItemStorageChannel.class).createList();
    /**
     * True when extraction from the network is not possible anymore, to improve the heuristic.
     */
    private boolean exhausted = false;
    // =====
    // Result reporting
    // =====
    private int bytes = 0;
    private long missing = 0;
    private long howManyEmitted = 0;

    private boolean sim;

    public CraftingTreeNode(final ICraftingGrid cc, final CraftingJob job, final IAEItemStack wat,
            final CraftingTreeProcess par, final int slot, final int depth) {
        this.what = wat;
        this.parent = par;
        this.slot = slot;
        this.world = job.getWorld();
        this.job = job;
        this.sim = false;

        this.canEmit = cc.canEmitFor(this.what);

        if (this.canEmit) {
            return; // if you can emit for something, you can't make it with patterns.
        }

        for (final ICraftingPatternDetails details : cc.getCraftingFor(this.what,
                this.parent == null ? null : this.parent.details, slot, this.world)) {
            if (this.parent == null || this.parent.notRecursive(details)) {
                this.nodes.add(new CraftingTreeProcess(cc, job, details, this, depth + 1));
            }
        }
    }

    /**
     * Return true if adding this pattern as a child would not cause recursion.
     */
    boolean notRecursive(final ICraftingPatternDetails details) {
        Collection<IAEItemStack> o = details.getOutputs();

        for (final IAEItemStack i : o) {
            if (i.equals(this.what)) {
                return false;
            }
        }

        o = details.getInputs();

        for (final IAEItemStack i : o) {
            if (i.equals(this.what)) {
                return false;
            }
        }

        return this.parent == null || this.parent.notRecursive(details);
    }

    IAEItemStack request(final MECraftingInventory inv, long requestedAmount, final IActionSource src)
            throws CraftBranchFailure, InterruptedException {
        this.job.handlePausing();

        // Lists the modifications that are done to {@code usedNetworkItems} during this request,
        // so that they can be undone if this request fails.
        // This is just a heuristic, and sometimes all failing paths are not caught, so the crafting could try
        // to use items that are not really necessary, but it's better than nothing.
        final List<IAEItemStack> usedNetworkItemsCopy = new ArrayList<>();

        // First try to collect items from the inventory
        this.what.setStackSize(requestedAmount);
        if (this.getSlot() >= 0 && this.parent != null && this.parent.details.isCraftable()) {
            // Special case: if this is a crafting pattern and there is a parent, also try to use a substitute input.
            final Collection<IAEItemStack> itemList; // All possible substitute inputs, and fuzzy matching stacks.
            final IItemList<IAEItemStack> inventoryList = inv.getItemList();

            if (this.parent.details.canSubstitute()) {
                final List<IAEItemStack> substitutes = this.parent.details.getSubstituteInputs(this.slot);
                itemList = new ArrayList<>(substitutes.size());

                for (IAEItemStack stack : substitutes) {
                    itemList.addAll(inventoryList.findFuzzy(stack, FuzzyMode.IGNORE_ALL));
                }
            } else {
                itemList = Lists.newArrayList();

                final IAEItemStack item = inventoryList.findPrecise(this.what);

                if (item != null) {
                    itemList.add(item);
                }
            }

            for (IAEItemStack fuzz : itemList) {
                if (this.parent.details.isValidItemForSlot(this.getSlot(),
                        fuzz.copy().setStackSize(1).createItemStack(), this.world)) {
                    fuzz = fuzz.copy();
                    fuzz.setStackSize(requestedAmount);

                    final IAEItemStack available = inv.extractItems(fuzz, Actionable.MODULATE, src);

                    if (available != null) {
                        // Heuristic to build the set of things that need to be extracted from the network.
                        // Assume things come from the network first.
                        if (!this.exhausted) {
                            final IAEItemStack is = this.job.extractFromNetworkInventory(available);

                            if (is != null) {
                                usedNetworkItemsCopy.add(is.copy());
                                this.usedNetworkItems.add(is);
                            }
                        }

                        this.bytes += available.getStackSize();
                        requestedAmount -= available.getStackSize();

                        if (requestedAmount == 0) {
                            return available;
                        }
                    }
                }
            }
        } else {
            final IAEItemStack available = inv.extractItems(this.what, Actionable.MODULATE, src);

            if (available != null) {
                // Heuristic to build the set of things that need to be extracted from the network.
                // Assume things come from the network first.
                if (!this.exhausted) {
                    final IAEItemStack is = this.job.extractFromNetworkInventory(available);

                    if (is != null) {
                        usedNetworkItemsCopy.add(is.copy());
                        this.usedNetworkItems.add(is);
                    }
                }

                this.bytes += available.getStackSize();
                requestedAmount -= available.getStackSize();

                if (requestedAmount == 0) {
                    return available;
                }
            }
        }

        // Try to emit if possible, never fails obviously.
        if (this.canEmit) {
            final IAEItemStack wat = this.what.copy();
            wat.setStackSize(requestedAmount);

            this.howManyEmitted = wat.getStackSize();
            this.bytes += wat.getStackSize();

            return wat;
        }

        // Make sure future extractions from the crafting inventory don't trigger network inventory extractions.
        // (See variable javadoc)
        this.exhausted = true;

        // Try to make the pattern
        if (this.nodes.size() == 1) {
            final CraftingTreeProcess pro = this.nodes.get(0);

            while (pro.possible && requestedAmount > 0) {
                final IAEItemStack madeWhat = pro.getAmountCrafted(this.what);

                // Try to produce the request items and put them in {@code inv}.
                pro.request(inv, pro.getTimes(requestedAmount, madeWhat.getStackSize()), src);

                // By now we should have succeeded, as request throws an exception in case of failure.
                madeWhat.setStackSize(requestedAmount);

                final IAEItemStack available = inv.extractItems(madeWhat, Actionable.MODULATE, src);

                if (available != null) {
                    this.bytes += available.getStackSize();
                    requestedAmount -= available.getStackSize();

                    if (requestedAmount <= 0) {
                        return available;
                    }
                } else {
                    // An exception should have been thrown above, so this shouldn't happen.
                    // If it does, make sure we stop iterating.
                    // TODO: evaluate if this could be replaced by a break
                    pro.possible = false;
                }
            }
        } else if (this.nodes.size() > 1) {
            // With multiple patterns to make this, try each branch separately.
            for (final CraftingTreeProcess pro : this.nodes) {
                try {
                    while (pro.possible && requestedAmount > 0) {
                        // Copy the entire inventory for the request and use it.
                        // If an exception is thrown the copy will simply discard the result.
                        final MECraftingInventory subInv = new MECraftingInventory(inv, true, true);
                        // Request 1 by 1 to make sure the production can be split across multiple branches.
                        pro.request(subInv, 1, src);

                        this.what.setStackSize(requestedAmount);
                        final IAEItemStack available = subInv.extractItems(this.what, Actionable.MODULATE, src);

                        if (available != null) {
                            // Everything went well, try to apply modifications to the parent inventory.
                            if (!subInv.commit(src)) {
                                // Not supposed to fail, if it does just throw an exception, it's equivalent to the
                                // request failing.
                                throw new CraftBranchFailure(this.what, requestedAmount);
                            }

                            this.bytes += available.getStackSize();
                            requestedAmount -= available.getStackSize();

                            if (requestedAmount <= 0) {
                                return available;
                            }
                        } else {
                            // Again, not supposed to happen.
                            // TODO: evaluate if this could be replaced by a break
                            pro.possible = false;
                        }
                    }
                } catch (final CraftBranchFailure fail) {
                    // TODO: evaluate if this is useful.
                    pro.possible = true;
                }
            }
        }

        if (this.sim) {
            // If simulating, just report what's missing
            this.missing += requestedAmount;
            this.bytes += requestedAmount;
            final IAEItemStack rv = this.what.copy();
            rv.setStackSize(requestedAmount);
            return rv;
        }

        // This branch has failed, so we must re-inject the items we marked as used in the network so they don't get
        // extracted when the job is attempted.
        // TODO: looks funky, the single pattern case can throw an exception that short-circuits this cleanup.
        for (final IAEItemStack o : usedNetworkItemsCopy) {
            this.job.reinjectIntoNetworkInventory(o.copy());
            o.setStackSize(-o.getStackSize());
            this.usedNetworkItems.add(o);
        }

        throw new CraftBranchFailure(this.what, requestedAmount);
    }

    void reportBytes(final CraftingJob job) {
        job.addBytes(8 + this.bytes);

        for (final CraftingTreeProcess pro : this.nodes) {
            pro.reportBytes(job);
        }
    }

    void setSimulate() {
        this.sim = true;
        this.missing = 0;
        this.bytes = 0;
        this.usedNetworkItems.resetStatus();
        this.exhausted = false;

        for (final CraftingTreeProcess pro : this.nodes) {
            pro.setSimulate();
        }
    }

    /**
     * Try to recursively assign the job to a CPU.
     *
     * @param storage A copy of the network inventory, on which to act to make sure everything can still be extracted.
     */
    public void tryAssignToCpu(final MECraftingInventory storage, final CraftingCPUCluster craftingCPUCluster,
            final IActionSource src) throws CraftBranchFailure {
        // Try to move items from the network to the crafting CPU.
        for (final IAEItemStack i : this.usedNetworkItems) {
            // Extract from network (copy).
            final IAEItemStack ex = storage.extractItems(i, Actionable.MODULATE, src);

            if (ex == null || ex.getStackSize() != i.getStackSize()) {
                throw new CraftBranchFailure(i, i.getStackSize());
            }

            // Insert into the CPU's internal inventory.
            craftingCPUCluster.addStorage(ex);
        }

        // Mark emitted items
        if (this.howManyEmitted > 0) {
            final IAEItemStack i = this.what.copy().reset();
            i.setStackSize(this.howManyEmitted);
            craftingCPUCluster.addEmitable(i);
        }

        // Recurse
        for (final CraftingTreeProcess pro : this.nodes) {
            pro.tryAssignToCpu(storage, craftingCPUCluster, src);
        }
    }

    void populatePlan(final IItemList<IAEItemStack> plan) {
        if (this.missing > 0) {
            final IAEItemStack o = this.what.copy();
            o.setStackSize(this.missing);
            plan.add(o);
        }

        if (this.howManyEmitted > 0) {
            final IAEItemStack i = this.what.copy();
            i.setCountRequestable(this.howManyEmitted);
            plan.addRequestable(i);
        }

        for (final IAEItemStack i : this.usedNetworkItems) {
            plan.add(i.copy());
        }

        for (final CraftingTreeProcess pro : this.nodes) {
            pro.populatePlan(plan);
        }
    }

    int getSlot() {
        return this.slot;
    }
}
