package appeng.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.state.properties.StructureMode;
import net.minecraft.tileentity.StructureBlockTileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.*;
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
    private static final int TEST_Y = 10, TEST_Z = 0;
    private boolean printedResult = false;

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

            manager.printResult();
        }
    }

    public void startTests() {
        if (tests == null) {
            tests = new ArrayList<>();
            fillTests();
        }
    }

    public void cleanTests() {
        if (tests != null) {
            for (AETest test : tests) {
                test.clean();
            }
            tests = null;
            nextTestX = 0;
            printedResult = false;
        }
    }

    private void printResult() {
        // Check if the result was already printed.
        if (printedResult)
            return;

        // Make sure that all tests have finished.
        for (AETest test : tests) {
            if (!test.hasFailed() && !test.hasSucceeded()) {
                return;
            }
        }

        printedResult = true;
        int failedCount = 0;
        int succeededCount = 0;
        for (AETest test : tests) {
            if (test.hasFailed()) {
                failedCount++;
            }
            if (test.hasSucceeded()) {
                succeededCount++;
            }
        }
        IFormattableTextComponent result = new StringTextComponent("");
        result.appendSibling(formatComponent("AE2 testing finished (%d tests)\n", succeededCount + failedCount));
        if (failedCount == 0) {
            result.appendSibling(formatComponentColored("All succeeded!\n", TextFormatting.GREEN));
        } else {
            result.appendSibling(formatComponentColored("There were %d failed tests! Listing them... :(",
                    TextFormatting.RED, failedCount));
            for (AETest test : tests) {
                if (test.hasFailed()) {
                    result.appendSibling(formatComponent("\n" + test.name));
                }
            }
        }

        overworld.getServer().getPlayerList().getPlayers().forEach(player -> {
            player.sendStatusMessage(result, false);
        });
    }

    private static IFormattableTextComponent formatComponentColored(String format, TextFormatting color,
            Object... arguments) {
        return formatComponent(format, arguments).setStyle(Style.EMPTY.setColor(Color.fromTextFormatting(color)));
    }

    private static IFormattableTextComponent formatComponent(String format, Object... arguments) {
        return new StringTextComponent(String.format(format, arguments));
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
        AETest test = new AETest(testName, overworld, structureBlockPos, tile.getStructureSize(), maxTicks);
        tests.add(test);
        for (TestPredicate predicate : predicates) {
            test.addPredicate(predicate);
        }

        AELog.info("Started test %s with timeout %d and %d predicates", testName, maxTicks, predicates.length);
    }

    private void fillTests() {
        ItemStack matterBall32 = Api.instance().definitions().materials().matterBall().stack(32);
        addTest("test_condenser", 100, new ItemsExistPredicate(matterBall32));
        addTest("test_condenser", 1000, new ItemsExistPredicate(matterBall32));
        // This test should craft a chest through an export bus, using 4 oak planks and 4 birch planks
        addTest("test_substitution", 100, new ItemsExistPredicate(new ItemStack(Items.CHEST)));
    }

    static {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class,
                TestManager::onServerTick);
    }
}
