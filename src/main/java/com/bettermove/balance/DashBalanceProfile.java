package com.bettermove.balance;

/**
 * 单个品质当前生效的冲刺参数快照。
 */
public record DashBalanceProfile(
        double distance,
        double speed,
        double boostStrength,
        double endSpeedMultiplier) {
}
