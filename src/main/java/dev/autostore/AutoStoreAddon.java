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
        dev.autostore.trail.TrailController.INSTANCE=new dev.autostore.trail.TrailController();
        MeteorClient.EVENT_BUS.subscribe(dev.autostore.trail.TrailController.INSTANCE);
    }
    @Override public String getPackage() { return "dev.autostore"; }
}
