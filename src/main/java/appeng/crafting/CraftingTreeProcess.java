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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.core.Api;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;

/**
 * A crafting tree process is what represents a pattern in the crafting process. It has a parent node (its output), and
 * a list of child nodes for its inputs.
 */
public class CraftingTreeProcess {

    private final CraftingTreeNode parent;
    final ICraftingPatternDetails details;
    private final CraftingJob job;
    private final Map<CraftingTreeNode, Long> nodes = new HashMap<>();
    private final int depth;
    boolean possible = true;
    private long crafts = 0;
    private boolean containerItems;
    /**
     * If true, we perform this pattern by 1 at the time. This ensures that container items or outputs get reused when
     * possible.
     */
    private boolean limitQty;
    private long bytes = 0;

    public CraftingTreeProcess(final ICraftingGrid cc, final CraftingJob job, final ICraftingPatternDetails details,
            final CraftingTreeNode craftingTreeNode, final int depth) {
        this.parent = craftingTreeNode;
        this.details = details;
        this.job = job;
        this.depth = depth;

        if (details.isCraftable()) {
            final IAEItemStack[] list = details.getSparseInputs();

            updateLimitQty(true);

            if (this.containerItems) {
                for (int x = 0; x < list.length; x++) {
                    final IAEItemStack part = list[x];
                    if (part != null) {
                        this.nodes.put(new CraftingTreeNode(cc, job, part.copy(), this, x, depth + 1),
                                part.getStackSize());
                    }
                }
            } else {
                // this is minor different then below, this slot uses the pattern, but kinda fudges it.
                // TODO: what is the difference?
                for (final IAEItemStack part : details.getInputs()) {
                    for (int x = 0; x < list.length; x++) {
                        final IAEItemStack comparePart = list[x];
                        if (part != null && part.equals(comparePart)) {
                            // use the first slot...
                            this.nodes.put(new CraftingTreeNode(cc, job, part.copy(), this, x, depth + 1),
                                    part.getStackSize());
                            break;
                        }
                    }
                }
            }
        } else {
            updateLimitQty(false);

            for (final IAEItemStack part : details.getInputs()) {
                this.nodes.put(new CraftingTreeNode(cc, job, part.copy(), this, -1, depth + 1), part.getStackSize());
            }
        }
    }

    /**
     * Check if this pattern has one of its outputs as input. If that's the case, update {@code limitQty} to make sure
     * we simulate this pattern one by one.
     */
    private void updateLimitQty(boolean checkContainerItems) {
        for (final IAEItemStack part : details.getInputs()) {
            final ItemStack g = part.createItemStack();

            boolean isAnInput = false;
            for (final IAEItemStack a : details.getOutputs()) {
                if (!g.isEmpty() && a != null && a.equals(g)) {
                    isAnInput = true;
                }
            }

            if (isAnInput) {
                this.limitQty = true;
            }

            if (checkContainerItems && g.getItem().hasContainerItem(g)) {
                this.limitQty = this.containerItems = true;
            }
        }
    }

    boolean notRecursive(final ICraftingPatternDetails details) {
        return this.parent == null || this.parent.notRecursive(details);
    }

    long getTimes(final long remaining, final long stackSize) {
        if (this.limitQty) {
            return 1;
        }
        return remaining / stackSize + (remaining % stackSize != 0 ? 1 : 0);
    }

    /**
     * Craft this pattern {@code patternTimes} times, using the items in {@code inv}, and putting the results in
     * {@code inv}.
     */
    void request(final MECraftingInventory inv, final long patternTimes, final IActionSource src)
            throws CraftBranchFailure, InterruptedException {
        this.job.handlePausing();

        // request and remove inputs...
        for (final Entry<CraftingTreeNode, Long> entry : this.nodes.entrySet()) {
            final IAEItemStack stack = entry.getKey().request(inv, entry.getValue() * patternTimes, src);

            if (this.containerItems) {
                final ItemStack is = Platform.getContainerItem(stack.createItemStack());
                final IAEItemStack o = Api.instance().storage().getStorageChannel(IItemStorageChannel.class)
                        .createStack(is);
                if (o != null) {
                    this.bytes++;
                    inv.injectItems(o, Actionable.MODULATE, src);
                }
            }
        }

        // by now we must have succeeded, otherwise an exception would have been thrown by request() above

        // add crafting results..
        for (final IAEItemStack out : this.details.getOutputs()) {
            final IAEItemStack o = out.copy();
            o.setStackSize(o.getStackSize() * patternTimes);
            inv.injectItems(o, Actionable.MODULATE, src);
        }

        this.crafts += patternTimes;
    }

    void reportBytes(final CraftingJob job) {
        for (final CraftingTreeNode pro : this.nodes.keySet()) {
            pro.reportBytes(job);
        }

        job.addBytes(8 + this.crafts + this.bytes);
    }

    IAEItemStack getAmountCrafted(IAEItemStack what2) {
        for (final IAEItemStack is : this.details.getOutputs()) {
            if (is.equals(what2)) {
                what2 = what2.copy();
                what2.setStackSize(is.getStackSize());
                return what2;
            }
        }

        // more fuzzy!
        for (final IAEItemStack is : this.details.getOutputs()) {
            if (is.getItem() == what2.getItem()
                    && (is.getItem().isDamageable() || is.getItemDamage() == what2.getItemDamage())) {
                // TODO: this OR looks broken, investigate!
                what2 = is.copy();
                what2.setStackSize(is.getStackSize());
                return what2;
            }
        }

        throw new IllegalStateException("Crafting Tree construction failed.");
    }

    void setSimulate() {
        this.crafts = 0;
        this.bytes = 0;

        for (final CraftingTreeNode pro : this.nodes.keySet()) {
            pro.setSimulate();
        }
    }

    void tryAssignToCpu(final MECraftingInventory storage, final CraftingCPUCluster craftingCPUCluster,
            final IActionSource src)
            throws CraftBranchFailure {
        // Mark pattern to be crafted.
        craftingCPUCluster.addCrafting(this.details, this.crafts);

        for (final CraftingTreeNode pro : this.nodes.keySet()) {
            pro.tryAssignToCpu(storage, craftingCPUCluster, src);
        }
    }

    void populatePlan(final IItemList<IAEItemStack> plan) {
        for (IAEItemStack i : this.details.getOutputs()) {
            i = i.copy();
            i.setCountRequestable(i.getStackSize() * this.crafts);
            plan.addRequestable(i);
        }

        for (final CraftingTreeNode pro : this.nodes.keySet()) {
            pro.populatePlan(plan);
        }
    }
}
