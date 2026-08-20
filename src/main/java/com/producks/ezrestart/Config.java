package com.producks.ezrestart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class Config {
    public boolean autoFlagEnabled = true;
    public String restartTime = "04:00";
    public String timezone = "";
    public int shutdownTimeoutSeconds = 60;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final LocalTime DEFAULT_RESTART_TIME = LocalTime.of(4, 0);

    private transient LocalTime parsedRestartTime = DEFAULT_RESTART_TIME;
    private transient ZoneId parsedZone = ZoneId.systemDefault();

    public LocalTime getRestartTimeParsed() {
        return parsedRestartTime;
    }

    public ZoneId getZone() {
        return parsedZone;
    }

    public String describeZone() {
        if (timezone == null || timezone.isBlank()) {
            return "system default (" + parsedZone + ")";
        }
        return parsedZone.toString();
    }

    public void revalidate() {
        try {
            parsedRestartTime = LocalTime.parse(restartTime, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            EzRestart.LOGGER.warn("[EzRestart] Invalid restartTime '{}' (expected HH:mm), falling back to 04:00",
                    restartTime);
            parsedRestartTime = DEFAULT_RESTART_TIME;
        }
        if (timezone == null || timezone.isBlank()) {
            parsedZone = ZoneId.systemDefault();
        } else {
            try {
                parsedZone = ZoneId.of(timezone);
            } catch (DateTimeException e) {
                EzRestart.LOGGER.warn("[EzRestart] Invalid timezone '{}' (expected an IANA id like America/New_York), "
                        + "falling back to system default ({})", timezone, ZoneId.systemDefault());
                parsedZone = ZoneId.systemDefault();
            }
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("ezrestart.json");
    }

    public static Config load() {
        Path path = configPath();
        Config result;
        try {
            if (Files.exists(path)) {
                String json = Files.readString(path);
                Config loaded = GSON.fromJson(json, Config.class);
                result = loaded != null ? loaded : new Config();
            } else {
                result = new Config();
                result.save();
            }
        } catch (IOException | JsonSyntaxException e) {
            EzRestart.LOGGER.error("[EzRestart] Failed to load config; using defaults.", e);
            result = new Config();
        }
        result.revalidate();
        return result;
    }

    public void save() {
        Path path = configPath();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            EzRestart.LOGGER.error("[EzRestart] Failed to save config.", e);
        }
    }
}
