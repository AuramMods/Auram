package dev.auram.connection;

import dev.tonimatas.packetfixer.common.Config;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("auram_connection_timeout")
public final class AuramConnectionTimeout {
    private static final Logger LOGGER = LogManager.getLogger("Auram Connection Timeout");
    private static final String READ_TIMEOUT = "forge.readTimeout";
    private static final int LEGACY_DEFAULT = 120;
    private static final int PACK_DEFAULT = 600;

    public AuramConnectionTimeout() {
        int packTimeout = Config.getTimeout();
        if (packTimeout == LEGACY_DEFAULT) {
            // Existing servers can retain the old properties file because Packet Fixer rewrites it at startup.
            try {
                var field = Config.class.getDeclaredField("timeout");
                field.setAccessible(true);
                field.setInt(null, PACK_DEFAULT);
            } catch (ReflectiveOperationException | RuntimeException error) {
                LOGGER.warn("Could not update Packet Fixer's legacy login timeout", error);
            }
            packTimeout = PACK_DEFAULT;
        }

        String timeout = System.getProperty(READ_TIMEOUT);
        LOGGER.info("Packet Fixer login timeout is {} seconds", Config.getTimeout());
        if (timeout != null) {
            LOGGER.info("Forge socket read timeout is {} seconds from JVM arguments", timeout);
            return;
        }

        System.setProperty(READ_TIMEOUT, Integer.toString(packTimeout));
        LOGGER.info("Forge socket read timeout is {} seconds from the pack", packTimeout);
    }
}
