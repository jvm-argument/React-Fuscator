package fixture;

import net.fabricmc.api.*;

public final class SmokeEntrypoint implements ModInitializer,ClientModInitializer {
    @Override public void onInitialize(){check();System.out.println("RF_FABRIC_MAIN_OK");}
    @Override public void onInitializeClient(){check();System.out.println("RF_FABRIC_CLIENT_OK");}
    private void check(){if(new ProtectionService().run()!=37)throw new AssertionError("Semantics changed");}
}
