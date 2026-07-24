package com.boostermod.charge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChargeSessionTest {

    @Test
    void zeroTickReleaseUsesBaseMultiplierAndIsNotOverloaded() {
        ChargeSession session = new ChargeSession();
        assertTrue(session.tryStart(10));

        ChargeSession.ReleaseResult release = session.release(10, true);

        assertTrue(release.accepted());
        assertEquals(0, release.chargeTicks());
        assertEquals(1.0, release.multiplier(), 1.0e-9);
        assertFalse(release.overloaded());
        assertTrue(release.allowLaunchAttempt());
        assertTrue(release.settleResourcesOnSuccessfulLaunch());
        assertFalse(session.isActive());
    }

    @Test
    void threeSecondBoundaryIsMaxMultiplierAndOverloaded() {
        ChargeSession session = new ChargeSession();
        assertTrue(session.tryStart(0));

        ChargeSession.View view = session.view(60);

        assertTrue(view.active());
        assertEquals(60, view.chargeTicks());
        assertEquals(1.8, view.multiplier(), 1.0e-9);
        assertTrue(view.overloaded());
        assertFalse(view.shouldForceRelease());
    }

    @Test
    void overloadWindowCapsMultiplierAtMax() {
        ChargeSession session = new ChargeSession();
        assertTrue(session.tryStart(0));

        ChargeSession.View view = session.view(80);

        assertEquals(80, view.chargeTicks());
        assertEquals(1.8, view.multiplier(), 1.0e-9);
        assertTrue(view.overloaded());
        assertFalse(view.shouldForceRelease());
    }

    @Test
    void fiveSecondBoundaryRequestsForceRelease() {
        ChargeSession session = new ChargeSession();
        assertTrue(session.tryStart(0));

        ChargeSession.TickResult tick = session.tick(100, false);

        assertTrue(tick.active());
        assertEquals(100, tick.chargeTicks());
        assertEquals(1.8, tick.multiplier(), 1.0e-9);
        assertTrue(tick.overloaded());
        assertTrue(tick.shouldForceRelease());
        assertFalse(tick.cancelled());
        assertTrue(session.isActive());
    }

    @Test
    void cancelEndsSessionWithoutLaunchOrSettlement() {
        ChargeSession session = new ChargeSession();
        assertTrue(session.tryStart(0));

        ChargeSession.TickResult tick = session.tick(40, true);

        assertFalse(tick.active());
        assertTrue(tick.cancelled());
        assertFalse(tick.shouldForceRelease());
        assertFalse(session.isActive());
    }

    @Test
    void failedReleaseClearsSessionAndDoesNotSettle() {
        ChargeSession session = new ChargeSession();
        assertTrue(session.tryStart(0));

        ChargeSession.ReleaseResult release = session.release(60, false);

        assertTrue(release.accepted());
        assertFalse(release.allowLaunchAttempt());
        assertFalse(release.settleResourcesOnSuccessfulLaunch());
        assertEquals(60, release.chargeTicks());
        assertTrue(release.overloaded());
        assertFalse(session.isActive());
    }

    @Test
    void cancelBeatsForceReleaseOnSameTick() {
        ChargeSession session = new ChargeSession();
        assertTrue(session.tryStart(0));

        ChargeSession.TickResult tick = session.tick(100, true);

        assertTrue(tick.cancelled());
        assertFalse(tick.shouldForceRelease());
        assertFalse(session.isActive());
    }

    @Test
    void releaseAfterSessionClearedIsIgnored() {
        ChargeSession session = new ChargeSession();
        assertTrue(session.tryStart(0));
        session.cancel();

        ChargeSession.ReleaseResult release = session.release(50, true);

        assertFalse(release.accepted());
        assertFalse(release.allowLaunchAttempt());
        assertFalse(release.settleResourcesOnSuccessfulLaunch());
        assertFalse(session.isActive());
    }

    @Test
    void midChargeMultiplierInterpolatesLinearly() {
        ChargeSession session = new ChargeSession();
        assertTrue(session.tryStart(0));

        // 1.5s = 30 ticks → m = 1.0 + 0.8 * 0.5 = 1.4
        assertEquals(1.4, session.view(30).multiplier(), 1.0e-9);
    }

    @Test
    void duplicateStartWhileActiveIsRejected() {
        ChargeSession session = new ChargeSession();
        assertTrue(session.tryStart(0));
        assertFalse(session.tryStart(5));
        assertTrue(session.isActive());
        assertEquals(5, session.view(5).chargeTicks());
    }
}
