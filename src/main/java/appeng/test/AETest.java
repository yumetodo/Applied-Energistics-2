package appeng.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

public class AETest {
    public final String name;
    public final ServerWorld world;
    public final BlockPos position;
    public final BlockPos size;
    private int remainingTicksBeforeTimeout;
    private final List<TestPredicate> remainingPredicates = new ArrayList<>();

    public AETest(String name, ServerWorld world, BlockPos position, BlockPos size, int maxTicks) {
        this.name = name;
        this.world = world;
        this.position = position;
        this.size = size;
        this.remainingTicksBeforeTimeout = maxTicks;

        updateResult();
    }

    public void addPredicate(TestPredicate predicate) {
        remainingPredicates.add(predicate);
    }

    public boolean hasFailed() {
        return remainingTicksBeforeTimeout == 0 && remainingPredicates.size() > 0;
    }

    public boolean hasSucceeded() {
        return remainingPredicates.size() == 0;
    }

    public void tick() {
        if (remainingTicksBeforeTimeout > 0) {
            // Replace redstone ore by redstone blocks
            for (BlockPos pos : getInsidePositions()) {
                if (world.getBlockState(pos).matchesBlock(Blocks.REDSTONE_ORE)) {
                    world.setBlockState(pos, Blocks.REDSTONE_BLOCK.getDefaultState());
                }
            }

            remainingPredicates.removeIf(predicate -> predicate.test(this));
            --remainingTicksBeforeTimeout;

            updateResult();
        }
    }

    public Iterable<BlockPos> getInsidePositions() {
        return BlockPos.getAllInBoxMutable(position.add(0, 1, 0),
                position.add(size.getX() - 1, size.getY(), size.getZ() - 1));
    }

    private void updateResult() {
        BlockPos beaconBase = position.add(-2, 0, -2);
        for (int i = -1; i <= 1; ++i) {
            for (int j = -1; j <= 1; ++j) {
                world.setBlockState(beaconBase.add(i, 0, j), Blocks.IRON_BLOCK.getDefaultState());
            }
        }
        world.setBlockState(beaconBase.add(0, 1, 0), Blocks.BEACON.getDefaultState());
        Block glass = hasSucceeded() ? Blocks.GREEN_STAINED_GLASS
                : hasFailed() ? Blocks.RED_STAINED_GLASS : Blocks.ORANGE_STAINED_GLASS;
        world.setBlockState(beaconBase.add(0, 2, 0), glass.getDefaultState());
    }

    void clean() {
        // Clean contents
        for (BlockPos pos : getInsidePositions()) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
        }
        // Clean structure block
        world.setBlockState(position, Blocks.AIR.getDefaultState());
        // Clean beacon base
        BlockPos beaconBase = position.add(-2, 0, -2);
        for (int i = -1; i <= 1; ++i) {
            for (int j = -1; j <= 1; ++j) {
                world.setBlockState(beaconBase.add(i, 0, j), Blocks.AIR.getDefaultState());
            }
        }
        // Clean beacon and glass
        world.setBlockState(beaconBase.add(0, 1, 0), Blocks.AIR.getDefaultState());
        world.setBlockState(beaconBase.add(0, 2, 0), Blocks.AIR.getDefaultState());
    }
}
