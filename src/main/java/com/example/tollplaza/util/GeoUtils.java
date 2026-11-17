package com.example.tollplaza.util;

import com.example.tollplaza.model.Coordinates;

public final class GeoUtils {

    public static final double EARTH_RADIUS_METERS = 6371000.0;

    private GeoUtils() {
    }

    public static double haversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dPhi / 2.0) * Math.sin(dPhi / 2.0) +
                Math.cos(phi1) * Math.cos(phi2) *
                        Math.sin(dLambda / 2.0) * Math.sin(dLambda / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return EARTH_RADIUS_METERS * c;
    }

    public static double haversineDistanceMeters(Coordinates c1, Coordinates c2) {
        return haversineDistanceMeters(c1.getLatitude(), c1.getLongitude(), c2.getLatitude(), c2.getLongitude());
    }

    public static SegmentProjection projectPointOnSegment(Coordinates point, Coordinates segStart, Coordinates segEnd) {
        double lat1 = segStart.getLatitude();
        double lon1 = segStart.getLongitude();
        double lat2 = segEnd.getLatitude();
        double lon2 = segEnd.getLongitude();
        double plat = point.getLatitude();
        double plon = point.getLongitude();

        double midLat = Math.toRadians((lat1 + lat2) / 2.0);
        double radConversion = Math.PI / 180.0;

        double x2 = (lon2 - lon1) * radConversion * Math.cos(midLat) * EARTH_RADIUS_METERS;
        double y2 = (lat2 - lat1) * radConversion * EARTH_RADIUS_METERS;

        double xp = (plon - lon1) * radConversion * Math.cos(midLat) * EARTH_RADIUS_METERS;
        double yp = (plat - lat1) * radConversion * EARTH_RADIUS_METERS;

        double dx = x2;
        double dy = y2;
        double l2 = dx * dx + dy * dy;

        if (l2 < 1e-9) {
            double dist = Math.hypot(xp, yp);
            return new SegmentProjection(dist, 0.0, new Coordinates(lat1, lon1));
        }

        double t = (xp * dx + yp * dy) / l2;
        if (t <= 0.0) {
            double dist = Math.hypot(xp, yp);
            return new SegmentProjection(dist, 0.0, new Coordinates(lat1, lon1));
        } else if (t >= 1.0) {
            double dist = Math.hypot(xp - dx, yp - dy);
            double segLen = Math.sqrt(l2);
            return new SegmentProjection(dist, segLen, new Coordinates(lat2, lon2));
        } else {
            double projX = t * dx;
            double projY = t * dy;
            double dist = Math.hypot(xp - projX, yp - projY);
            double segLen = Math.sqrt(l2);
            double distAlongSeg = t * segLen;

            double projLat = lat1 + (t * (lat2 - lat1));
            double projLon = lon1 + (t * (lon2 - lon1));
            return new SegmentProjection(dist, distAlongSeg, new Coordinates(projLat, projLon));
        }
    }

    public static class SegmentProjection {
        private final double perpendicularDistanceMeters;
        private final double distanceAlongSegmentMeters;
        private final Coordinates projectedPoint;

        public SegmentProjection(double perpendicularDistanceMeters, double distanceAlongSegmentMeters, Coordinates projectedPoint) {
            this.perpendicularDistanceMeters = perpendicularDistanceMeters;
            this.distanceAlongSegmentMeters = distanceAlongSegmentMeters;
            this.projectedPoint = projectedPoint;
        }

        public double getPerpendicularDistanceMeters() {
            return perpendicularDistanceMeters;
        }

        public double getDistanceAlongSegmentMeters() {
            return distanceAlongSegmentMeters;
        }

        public Coordinates getProjectedPoint() {
            return projectedPoint;
        }
    }
}
