package com.custom.nodashbhop;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
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
 * Uses JCraft 0.18.2's real CommonStandComponent.setSkin(int) + sync(Entity),
 * displays ONLY on the hotbar actionbar ("Skin #N - <Skin Name>"), and plays firework sounds!
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
            arrowUseWindowEndMs.put(player.getUUID(), System.currentTimeMillis() + 15000L);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if ((player.tickCount % 4) != 0) return;

        UUID uuid = player.getUUID();
        String currentStand = JCraftStandCheck.getStandFullId(player);
        String previousStand = lastStandId.put(uuid, currentStand);

        if (previousStand == null) return;
        if (currentStand.equals(previousStand)) return;
        if (!JCraftStandCheck.hasStand(player)) return;

        Long windowEnd = arrowUseWindowEndMs.get(uuid);
        if (windowEnd == null || System.currentTimeMillis() > windowEnd) return;
        arrowUseWindowEndMs.remove(uuid);

        if (altSkinChancePercent <= 0) return;
        if (random.nextInt(100) < altSkinChancePercent) {
            int skinIndex = 1 + random.nextInt(3); // Skins 1, 2, or 3
            if (applyStandSkin(player, skinIndex)) {
                // Build exact JCraft 0.18.2 skin translation key: entity.<ns>.<stand>.info.skin<N>
                String ns = currentStand.contains(":") ? currentStand.substring(0, currentStand.indexOf(':')) : "jcraft";
                String slug = currentStand.contains(":") ? currentStand.substring(currentStand.indexOf(':') + 1) : currentStand;
                slug = slug.toLowerCase(Locale.ROOT).replace("_entity", "").replace("entity", "");
                String langKey = "entity." + ns + "." + slug + ".info.skin" + skinIndex;

                // Show ONLY on hotbar actionbar (actionBar = true, NO chat message!)
                Component hotbarMsg = Component.literal("§6Skin #" + skinIndex + " §f- §e")
                        .append(Component.translatable(langKey));
                player.displayClientMessage(hotbarMsg, true);

                // Play celebratory firework sounds!
                player.level().playSound(
                        null,
                        player.blockPosition(),
                        SoundEvents.FIREWORK_ROCKET_LAUNCH,
                        SoundSource.PLAYERS,
                        1.0F,
                        1.05F
                );
                player.level().playSound(
                        null,
                        player.blockPosition(),
                        SoundEvents.FIREWORK_ROCKET_TWINKLE,
                        SoundSource.PLAYERS,
                        1.0F,
                        1.15F
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
        try {
            // Exact JCraft 0.18.2 CommonStandComponent.setSkin(int)
            Method setSkin = standComp.getClass().getMethod("setSkin", int.class);
            setSkin.invoke(standComp, skin);

            // Sync component to client via CommonStandComponentImpl.sync(Entity)
            try {
                Method sync = standComp.getClass().getMethod("sync", Entity.class);
                sync.invoke(standComp, player);
            } catch (Exception ignored) {
            }

            // Also update currently summoned StandEntity if present
            Object standEntity = JCraftStandCheck.getStandEntity(player);
            if (standEntity != null) {
                try {
                    Method entitySetSkin = standEntity.getClass().getMethod("setSkin", int.class);
                    entitySetSkin.invoke(standEntity, skin);
                } catch (Exception ignored) {
                }
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
