package me.msicraft.mpibycte2;

import me.msicraft.mpibycte2.config.MpiConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Mpibycte2.MODID)
public class ServerEvent {

    private static final long COOLDOWN_MILLIS = 10_000;
    private static final long CLEANUP_AGE_LIMIT = COOLDOWN_MILLIS * 3;
    private static final Map<UUID, Map<Integer, Long>> warnTimestampsByPlayer = new HashMap<>();
    private static long tickCounter = 0;
    private static final long CLEANUP_INTERVAL_TICKS = 20 * 60;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter >= CLEANUP_INTERVAL_TICKS) {
            tickCounter = 0;

            long now = System.currentTimeMillis();

            Iterator<Map.Entry<UUID, Map<Integer, Long>>> it = warnTimestampsByPlayer.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Map<Integer, Long>> entry = it.next();
                Map<Integer, Long> timestamps = entry.getValue();
                timestamps.entrySet().removeIf(e -> now - e.getValue() > CLEANUP_AGE_LIMIT);

                if (timestamps.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBoneMealUse(BonemealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.isCreative()) {
                return;
            }
            if (event.getStack().getItem() == Items.BONE_MEAL) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.isEndConquered()) {
            return;
        }

        double damagePercent = MpiConfig.getGearDamagePercent();
        for (ItemStack itemStack : player.getArmorSlots()) {
            if (itemStack.isEmpty() || !itemStack.isDamageableItem()) {
                continue;
            }
            applyDamagePercent(itemStack, damagePercent);
        }
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            inventory.getCurios().forEach((identifier, slotInventory) -> {
                int slotCount = slotInventory.getSlots();
                for (int i = 0; i < slotCount; i++) {
                    ItemStack stack = slotInventory.getStacks().getStackInSlot(i);
                    if (stack.isEmpty() || !stack.isDamageableItem()) {
                        continue;
                    }
                    applyDamagePercent(stack, damagePercent);
                }
            });
        });
    }

    @SubscribeEvent
    public static void vehicleRiding(EntityMountEvent e) {
        Entity entity = e.getEntityBeingMounted();
        if (entity != null) {
            Entity playerEntity = e.getEntityMounting();
            if (playerEntity instanceof ServerPlayer) {
                return;
            }
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }

        double warningThreshold = MpiConfig.getWarningThreshold();
        UUID uuid = player.getUUID();
        warnTimestampsByPlayer.putIfAbsent(uuid, new HashMap<>());
        Map<Integer, Long> playerTimestamps = warnTimestampsByPlayer.get(uuid);

        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.isEmpty() || !stack.isDamageableItem()) {
                continue;
            }

            checkAndWarn(player, stack, playerTimestamps, warningThreshold);
        }

        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            inventory.getCurios().forEach((identifier, slotInventory) -> {
                int slot = slotInventory.getSlots();
                for (int i = 0; i < slot; i++) {
                    ItemStack itemStack = slotInventory.getStacks().getStackInSlot(i);
                    if (itemStack.isEmpty() || !itemStack.isDamageableItem()) {
                        continue;
                    }

                    checkAndWarn(player, itemStack, playerTimestamps, warningThreshold);
                }
            });
        });
    }

    @SubscribeEvent
    public static void onItemDestroyed(PlayerDestroyItemEvent event) {
        Player player = event.getEntity();
        ItemStack destroyed = event.getOriginal();

        if (!destroyed.isDamageableItem()) return;

        if (destroyed.getOrCreateTag().getBoolean("Recovered") ||
                destroyed.getDamageValue() >= destroyed.getMaxDamage() - 1) return;

        ItemStack recovered = destroyed.copy();
        recovered.setDamageValue(recovered.getMaxDamage() - 1);
        recovered.getOrCreateTag().putBoolean("Recovered", true);

        boolean added = player.getInventory().add(recovered);
        Component itemName = recovered.getHoverName();

        if (added) {
            Component message = Component.literal("내구도 1이 된 [")
                    .append(itemName)
                    .append("] 이(가) 인벤토리로 회수되었습니다.")
                    .withStyle(style -> style.withHoverEvent(
                            new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(recovered))
                    ));
            player.sendSystemMessage(message);
        } else {
            player.spawnAtLocation(recovered);

            Component message = Component.literal("내구도 1이 된 [")
                    .append(itemName)
                    .append("] 이(가) 바닥에 떨어졌습니다.")
                    .withStyle(style -> style.withHoverEvent(
                            new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(recovered))
                    ));
            player.sendSystemMessage(message);
        }
    }

    private static void checkAndWarn(Player player, ItemStack stack, Map<Integer, Long> timestamps, double warningThreshold) {
        if (!stack.isDamageableItem()) {
            return;
        }

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.getBoolean("Recovered")) {
            if (stack.getDamageValue() < stack.getMaxDamage() - 1) {
                tag.remove("Recovered");
                if (tag.isEmpty()) {
                    stack.setTag(null);
                }
            }
        }

        int max = stack.getMaxDamage();
        int damage = stack.getDamageValue();
        float ratio = (float) (max - damage) / max;
        if (ratio > warningThreshold) return;

        int key = System.identityHashCode(stack);
        long now = System.currentTimeMillis();
        long lastWarn = timestamps.getOrDefault(key, 0L);
        if (now - lastWarn >= COOLDOWN_MILLIS) {
            Component itemName = stack.getHoverName();
            Component message = Component.literal("[내구도 경고] -> [")
                    .append(itemName)
                    .append("] 의 내구도가 " + (int)(warningThreshold*100) + "% 이하입니다.")
                    .withStyle(style -> style.withHoverEvent(
                            new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack))
                    ));
            player.sendSystemMessage(message);
            timestamps.put(key, now);
        }
    }


    private static void applyDamagePercent(ItemStack itemStack, double damagePercent) {
        int maxDamage = itemStack.getMaxDamage();
        int currentDamage = itemStack.getDamageValue();
        int additionalDamage = (int) (maxDamage * damagePercent);
        int newDamage = currentDamage + additionalDamage;
        if (newDamage >= maxDamage) {
            itemStack.setDamageValue(maxDamage - 1);
        } else {
            itemStack.setDamageValue(newDamage);
        }
    }

}
