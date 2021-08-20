/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 - 2015 AlgorithmX2
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

package appeng.api.features;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;

import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.TunnelType;

/**
 * A Registry for how p2p Tunnels are attuned
 */
public final class P2PTunnelAttunementInternal {
    private static final int INITIAL_CAPACITY = 40;

    private static final Map<ItemStack, TunnelType> tunnels = new HashMap<>(INITIAL_CAPACITY);
    private static final Map<String, TunnelType> modIdTunnels = new HashMap<>(INITIAL_CAPACITY);
    private static final List<ApiAttunement<?>> apiAttunements = new ArrayList<>();

    public static void init() {
        P2PTunnelAttunement.addNewItemAttunement = P2PTunnelAttunementInternal::addNewItemAttunement;
        P2PTunnelAttunement.addNewModAttunement = P2PTunnelAttunementInternal::addNewModAttunement;
        P2PTunnelAttunement.addNewApiAttunement = P2PTunnelAttunementInternal::addNewApiAttunement;
        P2PTunnelAttunement.getTunnelTypeByItem = P2PTunnelAttunementInternal::getTunnelTypeByItem;
    }

    private static void addNewItemAttunement(@Nonnull ItemStack trigger, @Nullable TunnelType type) {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(type, "type");
        Preconditions.checkArgument(!trigger.isEmpty(), "!trigger.isEmpty()");
        tunnels.put(trigger, type);
    }

    private static void addNewModAttunement(@Nonnull final String modId, @Nullable final TunnelType type) {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(type, "type");
        modIdTunnels.put(modId, type);
    }

    private static <T> void addNewApiAttunement(ItemApiLookup<?, T> api, Function<ItemStack, T> contextProvider,
            TunnelType type) {
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(contextProvider, "contextProvider");
        Objects.requireNonNull(type, "type");
        apiAttunements.add(new ApiAttunement<>(api, contextProvider, type));
    }

    @Nullable
    private static TunnelType getTunnelTypeByItem(final ItemStack trigger) {
        if (!trigger.isEmpty()) {
            // First match exact items
            for (final Map.Entry<ItemStack, TunnelType> entry : tunnels.entrySet()) {
                final ItemStack is = entry.getKey();

                if (is.getItem() == trigger.getItem()) {
                    return entry.getValue();
                }

                if (ItemStack.isSame(is, trigger)) {
                    return entry.getValue();
                }
            }

            // Check provided APIs
            for (var apiAttunement : apiAttunements) {
                if (apiAttunement.hasApi(trigger)) {
                    return apiAttunement.type();
                }
            }

            // Use the mod id as last option.
            for (final Map.Entry<String, TunnelType> entry : modIdTunnels.entrySet()) {
                var id = Registry.ITEM.getKey(trigger.getItem());
                if (id.getNamespace().equals(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    record ApiAttunement<T> (
            ItemApiLookup<?, T> api,
            Function<ItemStack, T> contextProvider,
            TunnelType type) {
        public boolean hasApi(ItemStack stack) {
            return api.find(stack, contextProvider.apply(stack)) != null;
        }
    }

}
