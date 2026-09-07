package dev.zeralius.clowder.common.geo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record LatLon(BigDecimal latitude, BigDecimal longitude) {

    private static final int SCALE = 6;
    private static final BigDecimal MAX_LAT = new BigDecimal("90");
    private static final BigDecimal MAX_LON = new BigDecimal("180");

    public LatLon {
        Objects.requireNonNull(latitude, "latitude");
        Objects.requireNonNull(longitude, "longitude");

        if(latitude.abs().compareTo(MAX_LAT) > 0) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if(longitude.abs().compareTo(MAX_LON) > 0) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }

        latitude = latitude.setScale(SCALE, RoundingMode.HALF_UP);
        longitude = longitude.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static LatLon of(String lat, String lon) {
        return new LatLon(new BigDecimal(lat), new BigDecimal(lon));
    }
    public static LatLon of(double latitude, double longitude) {
        return new LatLon(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
    }
}
