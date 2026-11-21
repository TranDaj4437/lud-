package com.custom.nodashbhop;

import com.custom.nodashbhop.wheel.WheelClientBootstrap;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(NoDashBhopMod.MOD_ID)
public class NoDashBhopMod {
    public static final String MOD_ID = "jcraftnodashbhop";

    public static final GameRules.Key<GameRules.BooleanValue> RULE_NO_DASH =
            GameRules.register(
                    "jcraftNoStandNoDash",
                    GameRules.Category.PLAYER,
                    GameRules.BooleanValue.create(true)
            );

    public NoDashBhopMod() {
        CombinedNetwork.register();
        MinecraftForge.EVENT_BUS.register(new NoDashServerHandler());
        MinecraftForge.EVENT_BUS.register(new StandArrowSkinHandler());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            MinecraftForge.EVENT_BUS.register(new NoDashClientHandler());
            MinecraftForge.EVENT_BUS.register(new JCraftHudHider());
            CS2BhopEngine bhopEngine = new CS2BhopEngine();
            MinecraftForge.EVENT_BUS.register(bhopEngine);
            MinecraftForge.EVENT_BUS.register(new CS2VelocityHud(bhopEngine));
            WheelClientBootstrap.init();
        });
    }
}
