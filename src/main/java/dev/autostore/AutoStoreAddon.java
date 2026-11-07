package dev.autostore;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;

public final class AutoStoreAddon extends MeteorAddon {
    @Override public void onInitialize() {
        DependencyGate.check();
        Controller.INSTANCE = new Controller();
        MeteorClient.EVENT_BUS.subscribe(Controller.INSTANCE);
        Commands.add(new StorageCommand());
    }
    @Override public String getPackage() { return "dev.autostore"; }
}
