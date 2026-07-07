package com.boostermod.feedback;

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

public final class BoosterShakeSettings {
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "boostermod-shake.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicReference<BoosterShakeSettings> INSTANCE = new AtomicReference<>();

    private final MinecraftServer server;
    private boolean enabled;

    private BoosterShakeSettings(MinecraftServer server, boolean enabled) {
        this.server = server;
        this.enabled = enabled;
    }

    public static BoosterShakeSettings get(MinecraftServer server) {
        BoosterShakeSettings existing = INSTANCE.get();
        if (existing != null && existing.server == server) {
            return existing;
        }

        BoosterShakeSettings loaded = load(server);
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
                GSON.toJson(new ShakeConfig(enabled), writer);
            }
        } catch (IOException e) {
            BoosterMod.LOGGER.error("Failed to save screen shake config to {}", path, e);
        }
    }

    private static BoosterShakeSettings load(MinecraftServer server) {
        Path path = configPath(server);
        if (!Files.exists(path)) {
            BoosterShakeSettings settings = new BoosterShakeSettings(server, true);
            settings.save();
            return settings;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            ShakeConfig config = GSON.fromJson(reader, ShakeConfig.class);
            return new BoosterShakeSettings(server, config == null || config.enabled());
        } catch (IOException e) {
            BoosterMod.LOGGER.warn("Failed to load screen shake config from {}, using defaults.", path, e);
            return new BoosterShakeSettings(server, true);
        }
    }

    private static Path configPath(MinecraftServer server) {
        return server.getServerDirectory().resolve(CONFIG_DIR).resolve(CONFIG_FILE);
    }

    private record ShakeConfig(boolean enabled) {}
}
