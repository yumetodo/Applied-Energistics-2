package appeng.server.subcommands;

import static net.minecraft.command.Commands.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.command.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import appeng.server.ISubCommand;
import appeng.test.TestManager;

public class IntegrationTestCommand implements ISubCommand {
    @Override
    public void addArguments(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("start").executes(ctx -> {
            TestManager.get(ServerLifecycleHooks.getCurrentServer()).startTests();
            return 1;
        }));

        builder.then(literal("clean").executes(ctx -> {
            TestManager.get(ServerLifecycleHooks.getCurrentServer()).cleanTests();
            return 1;
        }));
    }

    @Override
    public void call(MinecraftServer srv, CommandContext<CommandSource> ctx, CommandSource sender) {

    }
}
