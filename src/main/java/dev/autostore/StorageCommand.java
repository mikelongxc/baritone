package dev.autostore;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.autostore.core.Loop;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/** Compatibility shortcut for old .autostore users. */
public final class StorageCommand extends Command {
    public StorageCommand() { super("autostore","Use #store then #mine minecraft:sand."); }
    private int action(Runnable run) {
        try { run.run(); }
        catch(RuntimeException e) { error(e instanceof Loop.SafetyException?e.getMessage():"Storage command failed."); }
        return SINGLE_SUCCESS;
    }
    @Override public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(ctx -> action(this::status));
        builder.then(literal("status").executes(ctx -> action(this::status)));
        builder.then(literal("pause").executes(ctx -> action(() -> Controller.INSTANCE.manualPause())));
        builder.then(literal("stop").executes(ctx -> action(() -> Controller.INSTANCE.manualStop())));
        builder.then(literal("resume").executes(ctx -> action(() -> Controller.INSTANCE.resume())));
        builder.then(literal("add").executes(ctx -> action(() -> {Controller.INSTANCE.addDepot();info("Storage saved. Run #mine minecraft:sand.");})));
    }
    private void status() {
        Controller c=Controller.INSTANCE;
        info("%s | %s",c.loop.state,c.statusReason());
        info("Use #store at a chest, then #mine minecraft:sand. No quarry, corridor or arming step.");
    }
}
