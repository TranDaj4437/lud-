package com.custom.nodashbhop;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class CombinedNetwork {
    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NoDashBhopMod.MOD_ID, "main"),
            () -> PROTOCOL,
            s -> true,
            s -> true
    );

    private CombinedNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(
                0,
                SyncStatePacket.class,
                SyncStatePacket::encode,
                SyncStatePacket::decode,
                SyncStatePacket::handle
        );
    }

    public static void syncAllFromConfig() {
        CHANNEL.send(PacketDistributor.ALL.noArg(), SyncStatePacket.fromCurrentConfig());
    }

    public static void syncPlayerFromConfig(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), SyncStatePacket.fromCurrentConfig());
    }

    public static final class SyncStatePacket {
        private final boolean noDashEnabled;
        private final boolean bhopEnabled;
        private final boolean autoBhop;
        private final boolean scrollJump;
        private final double airAccel;
        private final double wishCap;
        private final double maxSpeed;
        private final double landPreserve;
        private final double jumpBoost;

        public SyncStatePacket(
                boolean noDashEnabled,
                boolean bhopEnabled,
                boolean autoBhop,
                boolean scrollJump,
                double airAccel,
                double wishCap,
                double maxSpeed,
                double landPreserve,
                double jumpBoost
        ) {
            this.noDashEnabled = noDashEnabled;
            this.bhopEnabled = bhopEnabled;
            this.autoBhop = autoBhop;
            this.scrollJump = scrollJump;
            this.airAccel = airAccel;
            this.wishCap = wishCap;
            this.maxSpeed = maxSpeed;
            this.landPreserve = landPreserve;
            this.jumpBoost = jumpBoost;
        }

        public static SyncStatePacket fromCurrentConfig() {
            return new SyncStatePacket(
                    CS2BhopConfig.noDashEnabled,
                    CS2BhopConfig.bhopEnabled,
                    CS2BhopConfig.autoBhopHoldSpace,
                    CS2BhopConfig.scrollWheelJump,
                    CS2BhopConfig.airAccelerate,
                    CS2BhopConfig.wishSpeedCap,
                    CS2BhopConfig.maxBhopSpeed,
                    CS2BhopConfig.landingPreserve,
                    CS2BhopConfig.jumpBoost
            );
        }

        public static void encode(SyncStatePacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.noDashEnabled);
            buf.writeBoolean(msg.bhopEnabled);
            buf.writeBoolean(msg.autoBhop);
            buf.writeBoolean(msg.scrollJump);
            buf.writeDouble(msg.airAccel);
            buf.writeDouble(msg.wishCap);
            buf.writeDouble(msg.maxSpeed);
            buf.writeDouble(msg.landPreserve);
            buf.writeDouble(msg.jumpBoost);
        }

        public static SyncStatePacket decode(FriendlyByteBuf buf) {
            return new SyncStatePacket(
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble()
            );
        }

        public static void handle(SyncStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                CS2BhopConfig.noDashEnabled = msg.noDashEnabled;
                CS2BhopConfig.bhopEnabled = msg.bhopEnabled;
                CS2BhopConfig.autoBhopHoldSpace = msg.autoBhop;
                CS2BhopConfig.scrollWheelJump = msg.scrollJump;
                CS2BhopConfig.airAccelerate = msg.airAccel;
                CS2BhopConfig.wishSpeedCap = msg.wishCap;
                CS2BhopConfig.maxBhopSpeed = msg.maxSpeed;
                CS2BhopConfig.landingPreserve = msg.landPreserve;
                CS2BhopConfig.jumpBoost = msg.jumpBoost;
            });
            ctx.setPacketHandled(true);
        }
    }
}
