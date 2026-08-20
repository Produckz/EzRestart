package com.producks.ezrestart;

import com.producks.ezrestart.command.EzRestartCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public class EzRestart implements ModInitializer {
    public static final String MOD_ID = "ezrestart";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final int CHECK_INTERVAL_TICKS = 20;

    private static Config config = new Config();
    private static volatile boolean restartFlagged = false;
    private static LocalDate lastAutoFlagDate = null;
    private static int tickCounter = 0;

    @Override
    public void onInitialize() {
        config = Config.load();
        LOGGER.info("[EzRestart] Loaded. autoFlagEnabled={}, restartTime={}, timezone={}",
                config.autoFlagEnabled, config.restartTime, config.describeZone());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                EzRestartCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ZoneId zone = config.getZone();
            if (!LocalTime.now(zone).isBefore(config.getRestartTimeParsed())) {
                lastAutoFlagDate = LocalDate.now(zone);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < CHECK_INTERVAL_TICKS) {
                return;
            }
            tickCounter = 0;

            if (config.autoFlagEnabled) {
                ZoneId zone = config.getZone();
                LocalDate today = LocalDate.now(zone);
                if (!today.equals(lastAutoFlagDate)
                        && !LocalTime.now(zone).isBefore(config.getRestartTimeParsed())) {
                    restartFlagged = true;
                    lastAutoFlagDate = today;
                    LOGGER.info("[EzRestart] Daily auto-flag fired. Server will restart next time it is empty.");
                }
            }

            if (restartFlagged && server.getPlayerCount() == 0) {
                LOGGER.info("[EzRestart] Server is empty and flagged for restart. Stopping now.");
                restartFlagged = false;
                armShutdownWatchdog();
                server.halt(false);
            }
        });
    }

    public static boolean isFlagged() {
        return restartFlagged;
    }

    public static void setFlagged(boolean flagged) {
        restartFlagged = flagged;
    }

    public static Config getConfig() {
        return config;
    }

    public static void reloadConfig() {
        config = Config.load();
        LOGGER.info("[EzRestart] Config reloaded. autoFlagEnabled={}, restartTime={}, timezone={}, shutdownTimeoutSeconds={}",
                config.autoFlagEnabled, config.restartTime, config.describeZone(), config.shutdownTimeoutSeconds);
    }

    private static volatile boolean watchdogArmed = false;

    public static void armShutdownWatchdog() {
        if (watchdogArmed) {
            return;
        }
        int timeoutSeconds = config.shutdownTimeoutSeconds;
        if (timeoutSeconds <= 0) {
            return;
        }
        watchdogArmed = true;
        long timeoutMs = timeoutSeconds * 1000L;
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(timeoutMs);
            } catch (InterruptedException ignored) {
                return;
            }
            LOGGER.warn("[EzRestart] Shutdown did not complete within {}s, forcing JVM exit so the wrapper can relaunch.",
                    timeoutSeconds);
            Runtime.getRuntime().halt(0);
        }, "EzRestart-Shutdown-Watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }
}
