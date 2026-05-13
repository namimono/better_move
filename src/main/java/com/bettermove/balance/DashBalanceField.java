package com.bettermove.balance;

import com.bettermove.tier.DashTier;

public enum DashBalanceField {
    DISTANCE("distance") {
        @Override
        public double readDefault(DashTier tier) {
            return tier.getDefaultDistance();
        }

        @Override
        public DashBalanceProfile update(DashBalanceProfile profile, double value) {
            return new DashBalanceProfile(value, profile.speed(), profile.boostStrength(), profile.endSpeedMultiplier());
        }
    },
    SPEED("speed") {
        @Override
        public double readDefault(DashTier tier) {
            return tier.getDefaultSpeed();
        }

        @Override
        public DashBalanceProfile update(DashBalanceProfile profile, double value) {
            return new DashBalanceProfile(profile.distance(), value, profile.boostStrength(), profile.endSpeedMultiplier());
        }
    },
    BOOST_STRENGTH("boostStrength") {
        @Override
        public double readDefault(DashTier tier) {
            return tier.getDefaultBoostStrength();
        }

        @Override
        public DashBalanceProfile update(DashBalanceProfile profile, double value) {
            return new DashBalanceProfile(profile.distance(), profile.speed(), value, profile.endSpeedMultiplier());
        }
    },
    END_SPEED_MULTIPLIER("endSpeedMultiplier") {
        @Override
        public double readDefault(DashTier tier) {
            return tier.getDefaultEndSpeedMultiplier();
        }

        @Override
        public DashBalanceProfile update(DashBalanceProfile profile, double value) {
            return new DashBalanceProfile(profile.distance(), profile.speed(), profile.boostStrength(), value);
        }
    };

    private final String id;

    DashBalanceField(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public abstract double readDefault(DashTier tier);

    public abstract DashBalanceProfile update(DashBalanceProfile profile, double value);

    public static DashBalanceField byId(String id) {
        for (DashBalanceField field : values()) {
            if (field.id.equals(id)) {
                return field;
            }
        }
        return null;
    }
}
