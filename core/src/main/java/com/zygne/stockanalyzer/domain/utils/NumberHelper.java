package com.zygne.stockanalyzer.domain.utils;

import java.math.BigDecimal;

import static java.lang.Math.round;

public class NumberHelper {

    public static double round2Decimals(double original) {
        return round(original * 100d) / 100d;
    }

    public static double roundDecimals(int decimals, double original) {
        double scalar = Math.pow(10, decimals);

        return round(original * scalar) / scalar;
    }

    public static double getPercentChange(double original, double newValue) {
        double diff = newValue - original;

        double percentage = diff / original;

        return percentage * 100;
    }

    public static double roundUp(double original) {
        return round((original / 0.05)) * 0.05;
    }

    public static double roundUp(double original, double group) {
        return round((original / group)) * group;
    }
}
