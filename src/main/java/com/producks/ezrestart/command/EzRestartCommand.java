package com.producks.ezrestart.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.producks.ezrestart.Config;
import com.producks.ezrestart.EzRestart;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionLevel;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

public final class EzRestartCommand {
    private EzRestartCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ezrestart")
                .requires(Permissions.require("ezrestart.command", PermissionLevel.GAMEMASTERS))
                .then(Commands.literal("flag")
                    .requires(Permissions.require("ezrestart.command.flag", PermissionLevel.OWNERS))
                    .executes(ctx -> {
                        EzRestart.setFlagged(true);
                        CommandSourceStack src = ctx.getSource();
                        src.sendSuccess(
                            () -> Component.literal("EzRestart: server flagged. It will restart the next time it is empty."),
                            true);
                        EzRestart.LOGGER.info("[EzRestart] Flagged for restart by {}.", src.getTextName());
                        return 1;
                    }))
                .then(Commands.literal("cancel")
                    .requires(Permissions.require("ezrestart.command.cancel", PermissionLevel.OWNERS))
                    .executes(ctx -> {
                        boolean wasFlagged = EzRestart.isFlagged();
                        EzRestart.setFlagged(false);
                        CommandSourceStack src = ctx.getSource();
                        String msg = wasFlagged
                            ? "EzRestart: restart flag cleared."
                            : "EzRestart: no restart was flagged.";
                        src.sendSuccess(() -> Component.literal(msg), true);
                        if (wasFlagged) {
                            EzRestart.LOGGER.info("[EzRestart] Restart flag cleared by {}.", src.getTextName());
                        }
                        return 1;
                    }))
                .then(Commands.literal("status")
                    .requires(Permissions.require("ezrestart.command.status", PermissionLevel.GAMEMASTERS))
                    .executes(ctx -> {
                        Config config = EzRestart.getConfig();
                        String msg = String.format(
                            "EzRestart: flagged=%s, autoFlagEnabled=%s, restartTime=%s, timezone=%s, shutdownTimeoutSeconds=%d",
                            EzRestart.isFlagged(),
                            config.autoFlagEnabled,
                            config.restartTime,
                            config.describeZone(),
                            config.shutdownTimeoutSeconds);
                        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                        return 1;
                    }))
                .then(Commands.literal("reload")
                    .requires(Permissions.require("ezrestart.command.reload", PermissionLevel.OWNERS))
                    .executes(ctx -> {
                        EzRestart.reloadConfig();
                        Config config = EzRestart.getConfig();
                        String msg = String.format(
                            "EzRestart: config reloaded. autoFlagEnabled=%s, restartTime=%s, timezone=%s, shutdownTimeoutSeconds=%d",
                            config.autoFlagEnabled,
                            config.restartTime,
                            config.describeZone(),
                            config.shutdownTimeoutSeconds);
                        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
                        EzRestart.LOGGER.info("[EzRestart] Config reloaded by {}.", ctx.getSource().getTextName());
                        return 1;
                    }))
                .then(Commands.literal("set")
                    .requires(Permissions.require("ezrestart.command.set", PermissionLevel.OWNERS))
                    .then(Commands.literal("time")
                        .then(Commands.argument("time", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String value = StringArgumentType.getString(ctx, "time").trim();
                                try {
                                    LocalTime.parse(value, Config.TIME_FORMAT);
                                } catch (DateTimeParseException e) {
                                    ctx.getSource().sendFailure(Component.literal(
                                        "EzRestart: invalid time '" + value + "', expected HH:mm, e.g. 04:00."));
                                    return 0;
                                }
                                Config config = EzRestart.getConfig();
                                config.restartTime = value;
                                config.revalidate();
                                config.save();
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                    "EzRestart: daily restart time set to " + value + " (" + config.describeZone() + ")."), true);
                                EzRestart.LOGGER.info("[EzRestart] restartTime set to {} by {}.",
                                    value, ctx.getSource().getTextName());
                                return 1;
                            })))
                    .then(Commands.literal("auto")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                            .executes(ctx -> {
                                boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                Config config = EzRestart.getConfig();
                                config.autoFlagEnabled = enabled;
                                config.save();
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                    "EzRestart: daily auto-flag " + (enabled ? "enabled." : "disabled.")), true);
                                EzRestart.LOGGER.info("[EzRestart] autoFlagEnabled set to {} by {}.",
                                    enabled, ctx.getSource().getTextName());
                                return 1;
                            })))
                    .then(Commands.literal("timezone")
                        .then(Commands.argument("zone", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String zone = StringArgumentType.getString(ctx, "zone").trim();
                                if (zone.equalsIgnoreCase("system") || zone.equalsIgnoreCase("default")) {
                                    zone = "";
                                }
                                if (!zone.isBlank()) {
                                    try {
                                        ZoneId.of(zone);
                                    } catch (DateTimeException e) {
                                        ctx.getSource().sendFailure(Component.literal(
                                            "EzRestart: invalid timezone '" + zone
                                                + "', expected an IANA id like America/New_York, or 'system'."));
                                        return 0;
                                    }
                                }
                                Config config = EzRestart.getConfig();
                                config.timezone = zone;
                                config.revalidate();
                                config.save();
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                    "EzRestart: timezone set to " + config.describeZone() + "."), true);
                                EzRestart.LOGGER.info("[EzRestart] timezone set to '{}' by {}.",
                                    config.timezone, ctx.getSource().getTextName());
                                return 1;
                            })))
                    .then(Commands.literal("timeout")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                            .executes(ctx -> {
                                int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                Config config = EzRestart.getConfig();
                                config.shutdownTimeoutSeconds = seconds;
                                config.save();
                                String msg = seconds == 0
                                    ? "EzRestart: shutdown watchdog disabled."
                                    : "EzRestart: shutdown watchdog timeout set to " + seconds + "s.";
                                ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
                                EzRestart.LOGGER.info("[EzRestart] shutdownTimeoutSeconds set to {} by {}.",
                                    seconds, ctx.getSource().getTextName());
                                return 1;
                            }))))
        );
    }
}
