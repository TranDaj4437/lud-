package com.custom.nodashbhop;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Grants a configurable chance (default 35%) to roll an alternate Stand skin (1..3)
 * whenever a player obtains a new Stand via a JCraft Stand Arrow.
 */
public class StandArrowSkinHandler {
    private static volatile int altSkinChancePercent = 35;

    private final Map<UUID, String> lastStandId = new HashMap<>();
    private final Map<UUID, Long> arrowUseWindowEndMs = new HashMap<>();
    private final Random random = new Random();

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return;

        String full = itemId.toString().toLowerCase(Locale.ROOT);
        if (full.contains("jcraft") && full.contains("arrow")) {
            // Arm a 12-second window for Stand Arrow grant/evolution
            arrowUseWindowEndMs.put(player.getUUID(), System.currentTimeMillis() + 12000L);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if ((player.tickCount % 5) != 0) return;

        UUID uuid = player.getUUID();
        String currentStand = JCraftStandCheck.getStandId(player);
        String previousStand = lastStandId.put(uuid, currentStand);

        if (previousStand == null) return; // First tick on login
        if (currentStand.equals(previousStand)) return;
        if (!JCraftStandCheck.hasStand(player)) return;

        Long windowEnd = arrowUseWindowEndMs.get(uuid);
        if (windowEnd == null || System.currentTimeMillis() > windowEnd) return;
        arrowUseWindowEndMs.remove(uuid);

        if (altSkinChancePercent <= 0) return;
        if (random.nextInt(100) < altSkinChancePercent) {
            int skinIndex = 1 + random.nextInt(3); // Alternate skins 1, 2, or 3
            if (applyStandSkin(player, skinIndex)) {
                String standName = JCraftStandCheck.getStandDisplayName(player);
                player.displayClientMessage(
                        Component.literal("§6✦ Chúc mừng! Bạn đã nhận được §eSkin #" + skinIndex + " §6cho §b" + standName + "§6!"),
                        false
                );
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        lastStandId.remove(uuid);
        arrowUseWindowEndMs.remove(uuid);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("jstandskin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> {
                                    altSkinChancePercent = IntegerArgumentType.getInteger(ctx, "percent");
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("§a[Server] Tỉ lệ ra Skin Stand khi dùng Mũi Tên: §e" + altSkinChancePercent + "%"),
                                            true
                                    );
                                    return 1;
                                })
                        )
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("§b[Server] Tỉ lệ ra Skin Stand hiện tại: §e" + altSkinChancePercent + "% §7(/jstandskin <0-100>)"),
                                    false
                            );
                            return 1;
                        })
        );
    }

    private boolean applyStandSkin(ServerPlayer player, int skin) {
        Object standComp = JCraftStandCheck.getStandComponent(player);
        if (standComp == null) return false;
        for (String mName : new String[]{"setSkin", "setStandSkin", "setSkinIndex"}) {
            try {
                Method m = standComp.getClass().getMethod(mName, int.class);
                m.invoke(standComp, skin);
                // Try syncing if component exposes sync()
                try {
                    Method sync = standComp.getClass().getMethod("sync");
                    sync.invoke(standComp);
                } catch (Exception ignored) {
                }
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
