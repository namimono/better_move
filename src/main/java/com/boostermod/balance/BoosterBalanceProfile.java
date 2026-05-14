package com.boostermod.balance;

/**
 * 推进器各等级的平衡参数（物理冲量驱动）。
 *
 * <ul>
 *   <li>{@code impulse}：触发推进时一次性赋予玩家的水平初速度（blocks/tick）。</li>
 *   <li>{@code thrustPerTick}：在持续推进阶段，每 tick 沿推进方向追加的速度增量（第 0 tick 为满值，按线性方式衰减到 0）。</li>
 *   <li>{@code thrustTicks}：持续推进阶段的总 tick 数；超过后不再追加推力，玩家完全依赖空气阻力自然减速。</li>
 * </ul>
 */
public record BoosterBalanceProfile(
        double impulse,
        double thrustPerTick,
        int thrustTicks) {
}
