package com.boostermod.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChargeAppearAnimationTest {

    @Test
    void fadeInReachesFullAfterFadeTicks() {
        float appear = 0.0f;
        for (int i = 0; i < ChargeAppearAnimation.FADE_TICKS; i++) {
            appear = ChargeAppearAnimation.step(appear, true);
        }
        assertEquals(1.0f, appear, 1.0e-6f);
    }

    @Test
    void fadeOutReachesZeroAfterFadeTicks() {
        float appear = 1.0f;
        for (int i = 0; i < ChargeAppearAnimation.FADE_TICKS; i++) {
            appear = ChargeAppearAnimation.step(appear, false);
        }
        assertEquals(0.0f, appear, 1.0e-6f);
    }

    @Test
    void midFadeIsPartialAndNotHardCut() {
        float appear = ChargeAppearAnimation.step(0.0f, true);
        assertTrue(appear > 0.0f && appear < 1.0f);
        assertEquals(1.0f / ChargeAppearAnimation.FADE_TICKS, appear, 1.0e-6f);
    }

    @Test
    void sameGameTickDoesNotDoubleAdvance() {
        float once = ChargeAppearAnimation.stepOnTick(0.0f, true, 10, 9);
        float sameTick = ChargeAppearAnimation.stepOnTick(once, true, 10, 10);
        assertEquals(once, sameTick, 1.0e-6f);
    }

    @Test
    void newGameTickAdvancesOnce() {
        float from = 0.0f;
        float advanced = ChargeAppearAnimation.stepOnTick(from, true, 11, 10);
        assertEquals(ChargeAppearAnimation.step(from, true), advanced, 1.0e-6f);
    }

    @Test
    void alphaScalesWithAppear() {
        assertEquals(0, ChargeAppearAnimation.alpha(0.0f));
        assertEquals(0xFF, ChargeAppearAnimation.alpha(1.0f));
        assertEquals(Math.round(0xFF * 0.5f), ChargeAppearAnimation.alpha(0.5f));
    }

    @Test
    void heightScaleGrowsFromMinToFull() {
        assertEquals(ChargeAppearAnimation.SCALE_MIN, ChargeAppearAnimation.heightScale(0.0f), 1.0e-6f);
        assertEquals(1.0f, ChargeAppearAnimation.heightScale(1.0f), 1.0e-6f);
        float mid = ChargeAppearAnimation.heightScale(0.5f);
        assertTrue(mid > ChargeAppearAnimation.SCALE_MIN && mid < 1.0f);
    }

    @Test
    void shouldRenderOnlyWhenVisible() {
        assertFalse(ChargeAppearAnimation.shouldRender(0.0f));
        assertFalse(ChargeAppearAnimation.shouldRender(0.005f));
        assertTrue(ChargeAppearAnimation.shouldRender(0.02f));
        assertTrue(ChargeAppearAnimation.shouldRender(1.0f));
    }

    @Test
    void fadeTicksIsLongEnoughToPerceive() {
        // Spec: 可感知；约 ≥0.3s（6 tick）才不算硬切闪现
        assertTrue(ChargeAppearAnimation.FADE_TICKS >= 6);
    }
}
