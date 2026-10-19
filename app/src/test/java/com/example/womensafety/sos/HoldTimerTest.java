package com.example.womensafety.sos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HoldTimerTest {

    @Test
    public void firesOnceAfterSixSeconds() {
        HoldTimer t = new HoldTimer();
        assertTrue(t.onDown(1_000));
        assertFalse(t.shouldFire(6_999));
        assertTrue(t.shouldFire(7_000));
        assertFalse("must fire only once per hold", t.shouldFire(9_000));
    }

    @Test
    public void releasingEarlyNeverFires() {
        HoldTimer t = new HoldTimer();
        t.onDown(0);
        assertEquals(5_500, t.onUp(5_500));
        assertFalse(t.shouldFire(10_000));
        assertFalse(t.isHolding());
    }

    @Test
    public void keyRepeatEventsDoNotRestartTheTimer() {
        HoldTimer t = new HoldTimer();
        assertTrue(t.onDown(0));
        assertFalse(t.onDown(500));   // Android sends repeated DOWN events while held
        assertFalse(t.onDown(3_000));
        assertTrue(t.shouldFire(6_000));
    }

    @Test
    public void newHoldCanFireAgain() {
        HoldTimer t = new HoldTimer();
        t.onDown(0);
        assertTrue(t.shouldFire(6_000));
        t.onUp(6_100);
        t.onDown(10_000);
        assertTrue(t.shouldFire(16_000));
    }

    @Test
    public void secondsHeldCountsWholeSeconds() {
        HoldTimer t = new HoldTimer();
        assertEquals(0, t.secondsHeld(100));
        t.onDown(0);
        assertEquals(2, t.secondsHeld(2_999));
        assertEquals(-1, new HoldTimer().onUp(5));
    }
}
