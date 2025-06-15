package me.msicraft.mpibycte2.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import me.msicraft.mpibycte2.config.MpiConfig;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ConfigCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("mpi")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("config")
                                        .then(Commands.literal("reload")
                                             .executes(ctx -> {
                                                    MpiConfig.reloadConfig();
                                                    ctx.getSource().sendSystemMessage(
                                                            Component.literal("랜덤 장비 가중치 설정을 파일에서 다시 로드했습니다."));
                                                    return 1;
                                                })
                                        )
                                        .then(Commands.literal("settings")
                                                .then(Commands.literal("view")
                                                        .executes(ctx -> {
                                                            double dp = MpiConfig.getGearDamagePercent();
                                                            double wt = MpiConfig.getWarningThreshold();
                                                            ctx.getSource().sendSystemMessage(
                                                                    Component.literal(String.format(
                                                                            "Settings: damagePercent=%.3f, warningThreshold=%.3f", dp, wt))
                                                            );
                                                            return 1;
                                                        })
                                                )
                                                // set damagePercent <value>
                                                .then(Commands.literal("setDamagePercent")
                                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                                                                .executes(ctx -> {
                                                                    double v = DoubleArgumentType.getDouble(ctx, "value");
                                                                    if (MpiConfig.setGearDamagePercent(v)) {
                                                                        ctx.getSource().sendSystemMessage(
                                                                                Component.literal("damagePercent을 " + v + "로 설정했습니다."));
                                                                        return 1;
                                                                    } else {
                                                                        ctx.getSource().sendFailure(
                                                                                Component.literal("잘못된 값입니다: " + v));
                                                                        return 0;
                                                                    }
                                                                })
                                                        )
                                                )
                                                // set warningThreshold <value>
                                                .then(Commands.literal("setWarningThreshold")
                                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                                                                .executes(ctx -> {
                                                                    double v = DoubleArgumentType.getDouble(ctx, "value");
                                                                    if (MpiConfig.setWarningThreshold(v)) {
                                                                        ctx.getSource().sendSystemMessage(
                                                                                Component.literal("warningThreshold을 " + v + "로 설정했습니다."));
                                                                        return 1;
                                                                    } else {
                                                                        ctx.getSource().sendFailure(
                                                                                Component.literal("잘못된 값입니다: " + v));
                                                                        return 0;
                                                                    }
                                                                })
                                                        )
                                                )

                                        )
                        )
        );
    }

}
