package com.boostermod.balance;

import com.boostermod.BoosterMod;
import com.boostermod.tier.BoosterTier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;

public final class BoosterBalanceManager {
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "boostermod-booster-balance.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type FILE_TYPE = new TypeToken<Map<String, Map<String, Double>>>() {}.getType();
    private static final AtomicReference<BoosterBalanceManager> INSTANCE = new AtomicReference<>();

    private final MinecraftServer server;
    private final EnumMap<BoosterTier, BoosterBalanceProfile> profiles;

    private BoosterBalanceManager(MinecraftServer server, EnumMap<BoosterTier, BoosterBalanceProfile> profiles) {
        this.server = server;
        this.profiles = profiles;
    }

    public static BoosterBalanceManager get(MinecraftServer server) {
        BoosterBalanceManager existing = INSTANCE.get();
        if (existing != null && existing.server == server) {
            return existing;
        }

        BoosterBalanceManager loaded = load(server);
        INSTANCE.set(loaded);
        return loaded;
    }

    public static BoosterBalanceManager reload(MinecraftServer server) {
        BoosterBalanceManager loaded = load(server);
        INSTANCE.set(loaded);
        return loaded;
    }

    public BoosterBalanceProfile getProfile(BoosterTier tier) {
        return profiles.get(tier);
    }

    public BoosterBalanceProfile setField(BoosterTier tier, BoosterBalanceField field, double value) {
        BoosterBalanceProfile updated = field.update(getProfile(tier), value);
        profiles.put(tier, updated);
        save();
        return updated;
    }

    public void resetTier(BoosterTier tier) {
        profiles.put(tier, defaultsFor(tier));
        save();
    }

    public void resetAll() {
        for (BoosterTier tier : BoosterTier.values()) {
            profiles.put(tier, defaultsFor(tier));
        }
        save();
    }

    public void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(toSerializableMap(), writer);
            }
        } catch (IOException e) {
            BoosterMod.LOGGER.error("Failed to save booster balance config to {}", path, e);
        }
    }

    private static BoosterBalanceManager load(MinecraftServer server) {
        EnumMap<BoosterTier, BoosterBalanceProfile> profiles = new EnumMap<>(BoosterTier.class);
        for (BoosterTier tier : BoosterTier.values()) {
            profiles.put(tier, defaultsFor(tier));
        }

        Path path = configPath(server);
        if (!Files.exists(path)) {
            BoosterBalanceManager manager = new BoosterBalanceManager(server, profiles);
            manager.save();
            return manager;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, Map<String, Double>> raw = GSON.fromJson(reader, FILE_TYPE);
            if (raw != null) {
                mergeRawConfig(profiles, raw);
            }
        } catch (IOException | JsonParseException e) {
            BoosterMod.LOGGER.warn("Failed to load booster balance config from {}, using defaults.", path, e);
        }

        return new BoosterBalanceManager(server, profiles);
    }

    private static void mergeRawConfig(
            EnumMap<BoosterTier, BoosterBalanceProfile> profiles,
            Map<String, Map<String, Double>> raw) {
        for (Map.Entry<String, Map<String, Double>> entry : raw.entrySet()) {
            BoosterTier tier = parseTier(entry.getKey());
            if (tier == null) {
                continue;
            }
            BoosterBalanceProfile profile = profiles.get(tier);
            for (Map.Entry<String, Double> fieldEntry : entry.getValue().entrySet()) {
                BoosterBalanceField field = BoosterBalanceField.byId(fieldEntry.getKey());
                Double value = fieldEntry.getValue();
                if (field == null || value == null) {
                    continue;
                }
                profile = field.update(profile, value);
            }
            profiles.put(tier, profile);
        }
    }

    private static BoosterTier parseTier(String rawTier) {
        if (rawTier == null) {
            return null;
        }
        try {
            return BoosterTier.valueOf(rawTier.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static BoosterBalanceProfile defaultsFor(BoosterTier tier) {
        return new BoosterBalanceProfile(
                tier.getDefaultImpulse(),
                tier.getDefaultThrustPerTick(),
                tier.getDefaultThrustTicks());
    }

    private Map<String, Map<String, Double>> toSerializableMap() {
        Map<String, Map<String, Double>> data = new HashMap<>();
        for (BoosterTier tier : BoosterTier.values()) {
            BoosterBalanceProfile profile = profiles.get(tier);
            Map<String, Double> fields = new HashMap<>();
            fields.put(BoosterBalanceField.IMPULSE.getId(), profile.impulse());
            fields.put(BoosterBalanceField.THRUST_PER_TICK.getId(), profile.thrustPerTick());
            fields.put(BoosterBalanceField.THRUST_TICKS.getId(), (double) profile.thrustTicks());
            data.put(tier.getId(), fields);
        }
        return data;
    }

    private Path configPath() {
        return configPath(server);
    }

    private static Path configPath(MinecraftServer server) {
        return server.getServerDirectory().resolve(CONFIG_DIR).resolve(CONFIG_FILE);
    }
}
