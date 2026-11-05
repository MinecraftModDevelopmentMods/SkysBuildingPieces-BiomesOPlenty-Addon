package zone.moddev.mc.skysbuildingpiecesbop.registrycontrol;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Reads the installed BOP registry without loading any Sky production mods. */
@Mod("bopregistrycontrol")
public final class BopRegistryControl {
    public BopRegistryControl() {
        NeoForge.EVENT_BUS.addListener(this::started);
    }

    private void started(ServerStartedEvent event) {
        try {
            RegistrySnapshot.write();
            org.apache.logging.log4j.LogManager.getLogger().info("BOP_REGISTRY_CONTROL_PASS");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot save the BOP registry snapshot", failure);
        } finally {
            event.getServer().halt(false);
        }
    }
}
