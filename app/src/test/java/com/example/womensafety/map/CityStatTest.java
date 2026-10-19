package com.example.womensafety.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CityStatTest {

    @Test
    public void levelsAreRelativeToTheAverage() {
        assertEquals(CityStat.Level.UNKNOWN, CityStat.levelFor(-1));
        assertEquals(CityStat.Level.LOW, CityStat.levelFor(0.3));
        assertEquals(CityStat.Level.MODERATE, CityStat.levelFor(1.0));
        assertEquals(CityStat.Level.HIGH, CityStat.levelFor(1.5));
        assertEquals(CityStat.Level.VERY_HIGH, CityStat.levelFor(2.7));
    }

    @Test
    public void haversineDistanceIsSensible() {
        // Bengaluru to Chennai is roughly 290 km in a straight line.
        double km = CrimeStatsRepository.distanceKm(12.9716, 77.5946, 13.0827, 80.2707);
        assertTrue(km > 280 && km < 300);
        assertEquals(0, CrimeStatsRepository.distanceKm(10, 10, 10, 10), 1e-9);
    }
}
