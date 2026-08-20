package com.producks.ezrestart;

import com.producks.ezrestart.command.EzRestartCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public class EzRestart implements ModInitializer {
    public static final String MOD_ID = "ezrestart";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final long WATCHER_INTERVAL_MS = 2000L;

    private static volatile Config config = new Config();
    private static volatile boolean restartFlagged = false;
    private static volatile LocalDate lastAutoFlagDate = null;
    private static int tickCounter = 0;
    private static volatile Thread watcherThread = null;

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
            startWatcher(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stopWatcher());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < CHECK_INTERVAL_TICKS) {
                return;
            }
            tickCounter = 0;
            runChecks(server);
        });
    }

    public static void runChecks(MinecraftServer server) {
        maybeAutoFlag();
        maybeRestart(server);
    }

    private static synchronized void maybeAutoFlag() {
        if (!config.autoFlagEnabled) {
            return;
        }
        ZoneId zone = config.getZone();
        LocalDate today = LocalDate.now(zone);
        if (!today.equals(lastAutoFlagDate)
                && !LocalTime.now(zone).isBefore(config.getRestartTimeParsed())) {
            restartFlagged = true;
            lastAutoFlagDate = today;
            LOGGER.info("[EzRestart] Daily auto-flag fired. Server will restart next time it is empty.");
        }
    }

    public static synchronized void maybeRestart(MinecraftServer server) {
        if (!restartFlagged || server.getPlayerCount() > 0) {
            return;
        }
        LOGGER.info("[EzRestart] Server is empty and flagged for restart. Stopping now.");
        restartFlagged = false;
        armShutdownWatchdog();
        server.halt(false);
    }

    private static void startWatcher(MinecraftServer server) {
        Thread watcher = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(WATCHER_INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }
                runChecks(server);
            }
        }, "EzRestart-Watcher");
        watcher.setDaemon(true);
        watcherThread = watcher;
        watcher.start();
        LOGGER.info("[EzRestart] Watcher started. Restart checks keep running while the server is paused.");
    }

    private static void stopWatcher() {
        Thread watcher = watcherThread;
        watcherThread = null;
        if (watcher != null) {
            watcher.interrupt();
        }
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
