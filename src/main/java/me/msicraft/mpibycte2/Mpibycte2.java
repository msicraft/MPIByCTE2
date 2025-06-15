package me.msicraft.mpibycte2;

import com.mojang.logging.LogUtils;
import me.msicraft.mpibycte2.command.ConfigCommand;
import me.msicraft.mpibycte2.command.RandomGearCommand;
import me.msicraft.mpibycte2.config.MpiConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Mpibycte2.MODID)
public class Mpibycte2 {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "mpibycte2";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public Mpibycte2() {
        MpiConfig.loadConfig();

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(RandomGearCommand.class);
        MinecraftForge.EVENT_BUS.register(ConfigCommand.class);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info(MODID + " Enabled");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info(MODID + " Disabled");
    }

}
