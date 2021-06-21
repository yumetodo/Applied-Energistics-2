package appeng.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.state.properties.StructureMode;
import net.minecraft.tileentity.StructureBlockTileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import appeng.core.AELog;
import appeng.core.Api;
import appeng.test.predicates.ItemsExistPredicate;

public class TestManager {
    private static final Map<MinecraftServer, TestManager> MANAGERS = new WeakHashMap<>();
    private List<AETest> tests = null;
    private final ServerWorld overworld;
    private int nextTestX = 0;
    private static int TEST_Y = 10, TEST_Z = 0;

    private TestManager(MinecraftServer server) {
        this.overworld = server.getWorld(ServerWorld.OVERWORLD);
    }

    public static TestManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, TestManager::new);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START)
            return;

        TestManager manager = get(ServerLifecycleHooks.getCurrentServer());

        if (manager.tests != null) {
            for (AETest test : manager.tests) {
                test.tick();
            }
        }
    }

    public void startTesting() {
        if (tests == null) {
            tests = new ArrayList<>();
            fillTests();
        }
    }

    private void addTest(String testName, int maxTicks, TestPredicate... predicates) {
        // Place test
        BlockPos structureBlockPos = new BlockPos(nextTestX, TEST_Y, TEST_Z);
        nextTestX += 16;
        // 1) set structure block
        overworld.setBlockState(structureBlockPos, Blocks.STRUCTURE_BLOCK.getDefaultState());
        StructureBlockTileEntity tile = (StructureBlockTileEntity) overworld.getTileEntity(structureBlockPos);
        // 2) set correct mode and structure
        tile.setMode(StructureMode.LOAD);
        tile.setName(new ResourceLocation("appliedenergistics2", testName));
        // 3) place! :)
        tile.func_242688_a(overworld, false);

        // Add test to list
        AETest test = new AETest(overworld, structureBlockPos, tile.getStructureSize(), maxTicks);
        tests.add(test);
        for (TestPredicate predicate : predicates) {
            test.addPredicate(predicate);
        }

        AELog.info("Started test %s with timeout %d and %d predicates", testName, maxTicks, predicates.length);
    }

    private void fillTests() {
        ItemStack matterBall64 = Api.instance().definitions().materials().matterBall().stack(64);
        addTest("test_condenser", 100, new ItemsExistPredicate(matterBall64));
        addTest("test_condenser", 1000, new ItemsExistPredicate(matterBall64));
    }

    static {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class,
                TestManager::onServerTick);
    }
}
