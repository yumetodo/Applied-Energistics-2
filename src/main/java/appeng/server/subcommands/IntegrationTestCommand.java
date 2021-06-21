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
        builder.then(literal("yes").executes(ctx -> {
            yes(ctx);
            return 1;
        }));
    }

    @Override
    public void call(MinecraftServer srv, CommandContext<CommandSource> ctx, CommandSource sender) {

    }

    private void yes(CommandContext<CommandSource> ctx) {
        TestManager.get(ServerLifecycleHooks.getCurrentServer()).startTesting();
    }
}
