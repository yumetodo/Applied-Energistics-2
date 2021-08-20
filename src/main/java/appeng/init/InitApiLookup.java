package appeng.init;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;

import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.parts.PartApiLookup;
import appeng.blockentity.AEBaseInvBlockEntity;
import appeng.blockentity.misc.CondenserBlockEntity;
import appeng.blockentity.misc.FluidInterfaceBlockEntity;
import appeng.blockentity.misc.ItemInterfaceBlockEntity;
import appeng.blockentity.powersink.AEBasePoweredBlockEntity;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.capabilities.Capabilities;
import appeng.core.definitions.AEBlockEntities;
import appeng.debug.ItemGenBlockEntity;
import appeng.items.tools.powered.powersink.PoweredItemCapabilities;
import appeng.parts.misc.FluidInterfacePart;
import appeng.parts.misc.ItemInterfacePart;
import appeng.parts.networking.EnergyAcceptorPart;
import appeng.parts.p2p.FEP2PTunnelPart;
import appeng.parts.p2p.FluidP2PTunnelPart;
import appeng.parts.p2p.ItemP2PTunnelPart;

public final class InitApiLookup {

    private InitApiLookup() {
    }

    public static void init() {

        // Allow forwarding of API lookups to parts for the cable bus
        PartApiLookup.addHostType(AEBlockEntities.CABLE_BUS);

        // Forward to interfaces
        initItemInterface();
        initFluidInterface();
        initCondenser();
        initMEChest();
        initMisc();
        initEnergyAcceptors();
        initP2P();

        Capabilities.ITEM.registerFallback((world, pos, state, blockEntity, direction) -> {
            if (blockEntity instanceof AEBaseInvBlockEntity baseInvBlockEntity) {
                if (direction == null) {
                    return baseInvBlockEntity.getInternalInventory();
                } else {
                    return baseInvBlockEntity.getItemHandlerForSide(direction);
                }
            }
            return null;
        });

        Capabilities.ENERGY.registerFallback((world, pos, state, blockEntity, direction) -> {
            if (blockEntity instanceof AEBasePoweredBlockEntity poweredBlockEntity) {
                return poweredBlockEntity.getEnergyStorage(direction);
            }
            return null;
        });
    }

    private static void initP2P() {
        PartApiLookup.register(Capabilities.ITEM, (part, context) -> part.getExposedApi(), ItemP2PTunnelPart.class);
        PartApiLookup.register(Capabilities.ENERGY, (part, context) -> part.getExposedApi(), FEP2PTunnelPart.class);
        PartApiLookup.register(FluidStorage.SIDED, (part, context) -> part.getExposedApi(), FluidP2PTunnelPart.class);
    }

    private static void initEnergyAcceptors() {
        PartApiLookup.register(Capabilities.ENERGY, (part, context) -> part.getEnergyAdapter(),
                EnergyAcceptorPart.class);
        // The block version is handled by the generic fallback registration for AEBasePoweredBlockEntity
    }

    private static void initItemInterface() {
        PartApiLookup.register(Capabilities.ITEM, (part, context) -> part.getInterfaceDuality().getStorage(),
                ItemInterfacePart.class);
        Capabilities.ITEM.registerForBlockEntities((blockEntity, context) -> {
            return ((ItemInterfaceBlockEntity) blockEntity).getInterfaceDuality().getStorage();
        }, AEBlockEntities.INTERFACE);
        PartApiLookup.register(Capabilities.GRID_STORAGE_ACCESSOR,
                (part, context) -> part.getInterfaceDuality().getGridStorageAccessor(), ItemInterfacePart.class);
        Capabilities.GRID_STORAGE_ACCESSOR.registerForBlockEntities((blockEntity, context) -> {
            return ((ItemInterfaceBlockEntity) blockEntity).getInterfaceDuality().getGridStorageAccessor();
        }, AEBlockEntities.INTERFACE);
    }

    private static void initFluidInterface() {
        PartApiLookup.register(FluidStorage.SIDED, (part, context) -> part.getDualityFluidInterface().getTanks(),
                FluidInterfacePart.class);
        FluidStorage.SIDED.registerForBlockEntities((blockEntity, context) -> {
            return ((FluidInterfaceBlockEntity) blockEntity).getDualityFluidInterface().getTanks();
        }, AEBlockEntities.FLUID_INTERFACE);
        PartApiLookup.register(Capabilities.GRID_STORAGE_ACCESSOR,
                (part, context) -> part.getDualityFluidInterface().getGridStorageAccessor(), FluidInterfacePart.class);
        Capabilities.GRID_STORAGE_ACCESSOR.registerForBlockEntities((blockEntity, context) -> {
            return ((FluidInterfaceBlockEntity) blockEntity).getDualityFluidInterface().getGridStorageAccessor();
        }, AEBlockEntities.FLUID_INTERFACE);
    }

    private static void initCondenser() {
        // Condenser will always return its external inventory, even when context is null
        // (unlike the base class it derives from)
        Capabilities.ITEM.registerForBlockEntities((blockEntity, context) -> {
            return ((CondenserBlockEntity) blockEntity).getExternalInv();
        }, AEBlockEntities.CONDENSER);
        FluidStorage.SIDED.registerForBlockEntities(((blockEntity, context) -> {
            return ((CondenserBlockEntity) blockEntity).getFluidHandler();
        }), AEBlockEntities.CONDENSER);
        Capabilities.GRID_STORAGE_ACCESSOR.registerForBlockEntities((blockEntity, context) -> {
            return ((CondenserBlockEntity) blockEntity).getMEHandler();
        }, AEBlockEntities.CONDENSER);
    }

    private static void initMEChest() {
        FluidStorage.SIDED.registerForBlockEntities(((blockEntity, context) -> {
            var chest = (ChestBlockEntity) blockEntity;
            return chest.getFluidHandler(context);
        }), AEBlockEntities.CHEST);
        Capabilities.GRID_STORAGE_ACCESSOR.registerForBlockEntities((blockEntity, context) -> {
            return ((ChestBlockEntity) blockEntity).getMEHandler(context);
        }, AEBlockEntities.CHEST);
    }

    private static void initMisc() {
        Capabilities.ITEM.registerForBlockEntities((blockEntity, context) -> {
            return ((ItemGenBlockEntity) blockEntity).getItemHandler();
        }, AEBlockEntities.DEBUG_ITEM_GEN);
        Capabilities.ENERGY.registerSelf(AEBlockEntities.DEBUG_ENERGY_GEN);
    }

    private static void initPoweredItem() {
        Capabilities.ENERGY_STORAGE.registerFallback((itemStack, context) -> {
            if (itemStack.getItem() instanceof IAEItemPowerStorage) {
                return new PoweredItemCapabilities(context);
            }
            return null;
        });
    }

}
