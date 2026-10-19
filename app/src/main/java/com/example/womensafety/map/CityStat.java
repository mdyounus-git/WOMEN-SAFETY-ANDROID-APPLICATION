package com.example.womensafety.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Crimes against women in one city, from NCRB. */
public final class CityStat {

    public enum Level { LOW, MODERATE, HIGH, VERY_HIGH, UNKNOWN }

    public final String name;
    public final String state;
    public final double lat;
    public final double lng;
    @Nullable
    public final Double rate;
    @Nullable
    public final Integer cases;
    public final int year;
    public final String unit;
    public final String sourceTitle;
    public final double baseline;
    public final String baselineLabel;

    CityStat(String name, String state, double lat, double lng, @Nullable Double rate,
             @Nullable Integer cases, int year, String unit, String sourceTitle, double baseline,
             String baselineLabel) {
        this.name = name;
        this.state = state;
        this.lat = lat;
        this.lng = lng;
        this.rate = rate;
        this.cases = cases;
        this.year = year;
        this.unit = unit;
        this.sourceTitle = sourceTitle;
        this.baseline = baseline;
        this.baselineLabel = baselineLabel;
    }

    /** Rate divided by its source's average (1.0 = average). -1 if unknown. */
    public double ratio() {
        if (rate == null || baseline <= 0) return -1;
        return rate / baseline;
    }

    @NonNull
    public Level level() {
        return levelFor(ratio());
    }

    /** Thresholds relative to the average; public for unit tests. */
    @NonNull
    public static Level levelFor(double ratio) {
        if (ratio < 0) return Level.UNKNOWN;
        if (ratio < 0.6) return Level.LOW;
        if (ratio < 1.15) return Level.MODERATE;
        if (ratio < 1.75) return Level.HIGH;
        return Level.VERY_HIGH;
    }
}
