package com.bettermove.balance;

import com.bettermove.BetterMoveMod;
import com.bettermove.tier.DashTier;
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

/**
 * 服务端级冲刺平衡配置。默认值来自 {@link DashTier}，管理员可在游戏中改动并持久化。
 */
public final class DashBalanceManager {
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "bettermove-dash-balance.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type FILE_TYPE = new TypeToken<Map<String, Map<String, Double>>>() {}.getType();
    private static final AtomicReference<DashBalanceManager> INSTANCE = new AtomicReference<>();

    private final MinecraftServer server;
    private final EnumMap<DashTier, DashBalanceProfile> profiles;

    private DashBalanceManager(MinecraftServer server, EnumMap<DashTier, DashBalanceProfile> profiles) {
        this.server = server;
        this.profiles = profiles;
    }

    public static DashBalanceManager get(MinecraftServer server) {
        DashBalanceManager existing = INSTANCE.get();
        if (existing != null && existing.server == server) {
            return existing;
        }

        DashBalanceManager loaded = load(server);
        INSTANCE.set(loaded);
        return loaded;
    }

    public static DashBalanceManager reload(MinecraftServer server) {
        DashBalanceManager loaded = load(server);
        INSTANCE.set(loaded);
        return loaded;
    }

    public DashBalanceProfile getProfile(DashTier tier) {
        return profiles.get(tier);
    }

    public DashBalanceProfile setField(DashTier tier, DashBalanceField field, double value) {
        DashBalanceProfile updated = field.update(getProfile(tier), value);
        profiles.put(tier, updated);
        save();
        return updated;
    }

    public void resetTier(DashTier tier) {
        profiles.put(tier, defaultsFor(tier));
        save();
    }

    public void resetAll() {
        for (DashTier tier : DashTier.values()) {
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
            BetterMoveMod.LOGGER.error("Failed to save dash balance config to {}", path, e);
        }
    }

    private static DashBalanceManager load(MinecraftServer server) {
        EnumMap<DashTier, DashBalanceProfile> profiles = new EnumMap<>(DashTier.class);
        for (DashTier tier : DashTier.values()) {
            profiles.put(tier, defaultsFor(tier));
        }

        Path path = configPath(server);
        if (!Files.exists(path)) {
            DashBalanceManager manager = new DashBalanceManager(server, profiles);
            manager.save();
            return manager;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, Map<String, Double>> raw = GSON.fromJson(reader, FILE_TYPE);
            if (raw != null) {
                mergeRawConfig(profiles, raw);
            }
        } catch (IOException | JsonParseException e) {
            BetterMoveMod.LOGGER.warn("Failed to load dash balance config from {}, using defaults.", path, e);
        }

        return new DashBalanceManager(server, profiles);
    }

    private static void mergeRawConfig(
            EnumMap<DashTier, DashBalanceProfile> profiles,
            Map<String, Map<String, Double>> raw) {
        for (Map.Entry<String, Map<String, Double>> entry : raw.entrySet()) {
            DashTier tier = parseTier(entry.getKey());
            if (tier == null) {
                continue;
            }
            DashBalanceProfile profile = profiles.get(tier);
            for (Map.Entry<String, Double> fieldEntry : entry.getValue().entrySet()) {
                DashBalanceField field = DashBalanceField.byId(fieldEntry.getKey());
                Double value = fieldEntry.getValue();
                if (field == null || value == null) {
                    continue;
                }
                profile = field.update(profile, value);
            }
            profiles.put(tier, profile);
        }
    }

    private static DashTier parseTier(String rawTier) {
        if (rawTier == null) {
            return null;
        }
        try {
            return DashTier.valueOf(rawTier.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static DashBalanceProfile defaultsFor(DashTier tier) {
        return new DashBalanceProfile(
                tier.getDefaultDistance(),
                tier.getDefaultSpeed(),
                tier.getDefaultBoostStrength(),
                tier.getDefaultEndSpeedMultiplier());
    }

    private Map<String, Map<String, Double>> toSerializableMap() {
        Map<String, Map<String, Double>> data = new HashMap<>();
        for (DashTier tier : DashTier.values()) {
            DashBalanceProfile profile = profiles.get(tier);
            Map<String, Double> fields = new HashMap<>();
            fields.put(DashBalanceField.DISTANCE.getId(), profile.distance());
            fields.put(DashBalanceField.SPEED.getId(), profile.speed());
            fields.put(DashBalanceField.BOOST_STRENGTH.getId(), profile.boostStrength());
            fields.put(DashBalanceField.END_SPEED_MULTIPLIER.getId(), profile.endSpeedMultiplier());
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
