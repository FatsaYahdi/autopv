package saki.asv;

import saki.asv.config.Manager;
import saki.asv.command.Command;
import saki.asv.vault.VaultManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

public class AutoStoreVaultClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Manager.load();
        Command.register();

        ClientTickEvents.END_CLIENT_TICK.register(VaultManager::onClientTick);
        ScreenEvents.AFTER_INIT.register(
            (client, screen, scaledWidth, scaledHeight) ->
                VaultManager.onScreenOpen(screen)
        );
    }
}
