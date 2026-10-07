package fixture;

import org.bukkit.command.*;
import org.bukkit.event.*;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SmokePlugin extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        check();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("RF_PLUGIN_ENABLED");
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        check();
        getLogger().info("RF_EVENT_HANDLER_OK");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        check();
        sender.sendMessage("RF_COMMAND_OK");
        return true;
    }

    private void check() {
        if (new ProtectionService().run() != 37) {
            throw new AssertionError("Semantics changed");
        }
    }
}
