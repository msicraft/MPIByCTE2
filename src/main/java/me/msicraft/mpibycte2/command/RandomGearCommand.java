package me.msicraft.mpibycte2.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.robertx22.mine_and_slash.uncommon.datasaving.Load;
import me.msicraft.mpibycte2.config.MpiConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Random;
import java.util.Set;

public class RandomGearCommand {
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("mpi")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("randomgear")
                                .executes(ctx -> executeRandomGear(ctx, MpiConfig.getDefaultTableId()))
                                .then(Commands.argument("table", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String tableId = StringArgumentType.getString(ctx, "table");
                                            if (!MpiConfig.getTableIds().contains(tableId)) {
                                                String available = String.join(", ", MpiConfig.getTableIds());
                                                ctx.getSource().sendFailure(
                                                        Component.literal("존재하지 않는 테이블 ID입니다. 사용 가능한 ID: " + available)
                                                );
                                                return 0;
                                            }
                                            return executeRandomGear(ctx, tableId);
                                        })
                                )
                                .then(Commands.literal("config")
                                        .then(Commands.literal("tables")
                                                .then(Commands.literal("list")
                                                        .executes(ctx -> {
                                                            Set<String> ids = MpiConfig.getTableIds();
                                                            String msg = "사용 가능한 테이블 ID: " + String.join(", ", ids);
                                                            ctx.getSource().sendSystemMessage(Component.literal(msg));
                                                            return 1;
                                                        })
                                                )
                                                .then(Commands.literal("add")
                                                        .then(Commands.argument("table", StringArgumentType.word())
                                                                .executes(ctx -> {
                                                                    String tableId = StringArgumentType.getString(ctx, "table");
                                                                    if (MpiConfig.addTable(tableId)) {
                                                                        ctx.getSource().sendSystemMessage(
                                                                                Component.literal("테이블 '" + tableId + "'을(를) 생성했습니다."));
                                                                        return 1;
                                                                    } else {
                                                                        ctx.getSource().sendFailure(
                                                                                Component.literal("이미 존재하는 테이블 ID이거나 생성할 수 없습니다: " + tableId));
                                                                        return 0;
                                                                    }
                                                                })
                                                        )
                                                )
                                                .then(Commands.literal("remove")
                                                        .then(Commands.argument("table", StringArgumentType.word())
                                                                .executes(ctx -> {
                                                                    String tableId = StringArgumentType.getString(ctx, "table");
                                                                    if (MpiConfig.removeTable(tableId)) {
                                                                        ctx.getSource().sendSystemMessage(
                                                                                Component.literal("테이블 '" + tableId + "'을(를) 삭제했습니다."));
                                                                        return 1;
                                                                    } else {
                                                                        ctx.getSource().sendFailure(
                                                                                Component.literal("삭제할 수 없거나 존재하지 않는 테이블 ID: " + tableId));
                                                                        return 0;
                                                                    }
                                                                })
                                                        )
                                                )
                                        )
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("table", StringArgumentType.word())
                                                        .then(Commands.argument("rarity", StringArgumentType.word())
                                                                .then(Commands.argument("weight", DoubleArgumentType.doubleArg(0.0))
                                                                        .executes(ctx -> {
                                                                            String tableId = StringArgumentType.getString(ctx, "table");
                                                                            String rarity = StringArgumentType.getString(ctx, "rarity");
                                                                            double weight = DoubleArgumentType.getDouble(ctx, "weight");
                                                                            if (!MpiConfig.getTableIds().contains(tableId)) {
                                                                                ctx.getSource().sendFailure(
                                                                                        Component.literal("존재하지 않는 테이블 ID: " + tableId));
                                                                                return 0;
                                                                            }
                                                                            if (MpiConfig.setRarityWeight(tableId, rarity, weight)) {
                                                                                ctx.getSource().sendSystemMessage(
                                                                                        Component.literal("테이블 '" + tableId + "'에서 희귀도 '" + rarity +
                                                                                                "'의 가중치를 " + weight + " 으로 설정했습니다."));
                                                                                return 1;
                                                                            } else {
                                                                                ctx.getSource().sendFailure(
                                                                                        Component.literal("잘못된 희귀도 이름이거나 가중치 값입니다: " + rarity));
                                                                                return 0;
                                                                            }
                                                                        })
                                                                )
                                                        )
                                                )
                                        )
                                        .then(Commands.literal("reload")
                                                .executes(ctx -> {
                                                    MpiConfig.reloadConfig();
                                                    ctx.getSource().sendSystemMessage(
                                                            Component.literal("랜덤 장비 가중치 설정을 파일에서 다시 로드했습니다."));
                                                    return 1;
                                                })
                                        )
                                )
                        )
        );
    }

    private static int executeRandomGear(CommandContext<CommandSourceStack> ctx, String tableId) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String playerName = player.getName().getString();
        int playerLevel = Load.Unit(player).getLevel();

        String gearType = pickRandomGearType();
        String rarity = MpiConfig.pickRarityWeighted(RANDOM, tableId);

        // mine_and_slash give gear <player> <level> <gearType> <rarity> 1
        String giveCmd = String.format("mine_and_slash give gear %s %d %s %s 1",
                playerName, playerLevel, gearType, rarity);
        ctx.getSource().getServer().getCommands().performPrefixedCommand(ctx.getSource(), giveCmd);
        return 1;
    }

    private static final List<String> GEAR_BASE_TYPES = List.of(
            "axe", "bow", "brigandine_", "chainmail_", "cloth_", "crossbow", "dagger",
            "gauntlet", "greatsword", "hammer", "leather_", "necklace", "plate_",
            "ring", "scythe", "shield", "spear", "staff", "sword", "tome", "totem", "trident", "vest_"
    );
    private static final List<String> GEAR_SUFFIXES = List.of("boots", "chest", "helmet", "pants");
    private static String pickRandomGearType() {
        String base = GEAR_BASE_TYPES.get(RANDOM.nextInt(GEAR_BASE_TYPES.size()));
        if (base.endsWith("_")) {
            String suffix = GEAR_SUFFIXES.get(RANDOM.nextInt(GEAR_SUFFIXES.size()));
            return base + suffix;
        } else {
            return base;
        }
    }
}
