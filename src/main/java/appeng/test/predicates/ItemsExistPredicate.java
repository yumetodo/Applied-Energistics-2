package appeng.test.predicates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.test.AETest;
import appeng.test.TestPredicate;
import appeng.util.Platform;

public class ItemsExistPredicate implements TestPredicate {
    private final List<ItemStack> stacks;

    public ItemsExistPredicate(ItemStack... stacks) {
        this.stacks = Arrays.asList(stacks);
    }

    @Override
    public boolean test(AETest aeTest) {
        List<ItemStack> stacksCopy = new ArrayList<>();
        for (ItemStack stack : stacks) {
            stacksCopy.add(stack.copy());
        }

        for (BlockPos pos : aeTest.getInsidePositions()) {
            TileEntity te = aeTest.world.getTileEntity(pos);
            if (te == null)
                continue;

            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).orElse(null);
            if (handler == null)
                continue;

            for (int i = 0; i < stacksCopy.size(); ++i) {
                stacksCopy.set(i, extractReturnRemainder(handler, stacksCopy.get(i)));
            }
        }

        for (ItemStack stack : stacksCopy) {
            if (!stack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private static ItemStack extractReturnRemainder(IItemHandler handler, ItemStack source) {
        if (source.isEmpty())
            return source;
        int extracted = 0;
        for (int i = 0; i < handler.getSlots(); ++i) {
            if (Platform.itemComparisons().isSameItem(source, handler.getStackInSlot(i))) {
                extracted += handler.extractItem(i, handler.getStackInSlot(i).getCount(), true).getCount();
            }
        }

        if (extracted >= source.getCount()) {
            return ItemStack.EMPTY;
        } else {
            source = source.copy();
            source.shrink(extracted);
            return source;
        }
    }
}
