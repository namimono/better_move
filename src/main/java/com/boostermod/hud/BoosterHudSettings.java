package com.boostermod.hud;

import com.boostermod.BoosterMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;

public final class BoosterHudSettings {
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "boostermod-hud.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicReference<BoosterHudSettings> INSTANCE = new AtomicReference<>();

    private final MinecraftServer server;
    private boolean enabled;

    private BoosterHudSettings(MinecraftServer server, boolean enabled) {
        this.server = server;
        this.enabled = enabled;
    }

    public static BoosterHudSettings get(MinecraftServer server) {
        BoosterHudSettings existing = INSTANCE.get();
        if (existing != null && existing.server == server) {
            return existing;
        }

        BoosterHudSettings loaded = load(server);
        INSTANCE.set(loaded);
        return loaded;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return false;
        }

        this.enabled = enabled;
        save();
        return true;
    }

    private void save() {
        Path path = configPath(server);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(new HudConfig(enabled), writer);
            }
        } catch (IOException e) {
            BoosterMod.LOGGER.error("Failed to save HUD config to {}", path, e);
        }
    }

    private static BoosterHudSettings load(MinecraftServer server) {
        Path path = configPath(server);
        if (!Files.exists(path)) {
            BoosterHudSettings settings = new BoosterHudSettings(server, true);
            settings.save();
            return settings;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            HudConfig config = GSON.fromJson(reader, HudConfig.class);
            return new BoosterHudSettings(server, config == null || config.enabled());
        } catch (IOException e) {
            BoosterMod.LOGGER.warn("Failed to load HUD config from {}, using defaults.", path, e);
            return new BoosterHudSettings(server, true);
        }
    }

    private static Path configPath(MinecraftServer server) {
        return server.getServerDirectory().resolve(CONFIG_DIR).resolve(CONFIG_FILE);
    }

    private record HudConfig(boolean enabled) {}
}
