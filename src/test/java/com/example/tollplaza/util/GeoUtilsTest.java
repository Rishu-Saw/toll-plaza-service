package com.example.tollplaza.util;

import com.example.tollplaza.model.Coordinates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoUtilsTest {

    @Test
    @DisplayName("Haversine distance for 1 degree latitude should be approximately 111.19 km")
    void testHaversineDistanceOneDegree() {
        double dist = GeoUtils.haversineDistanceMeters(0, 0, 1, 0);
        assertTrue(dist > 111000 && dist < 111300, "Distance should be around 111.19 km, was " + dist);
    }

    @Test
    @DisplayName("Haversine distance between identical points should be 0")
    void testHaversineDistanceZero() {
        double dist = GeoUtils.haversineDistanceMeters(28.6333, 77.2167, 28.6333, 77.2167);
        assertEquals(0.0, dist, 0.001);
    }

    @Test
    @DisplayName("Projection of a point directly on the segment")
    void testPointDirectlyOnSegment() {
        Coordinates start = new Coordinates(0.0, 0.0);
        Coordinates end = new Coordinates(0.0, 1.0);
        Coordinates point = new Coordinates(0.0, 0.5);

        GeoUtils.SegmentProjection proj = GeoUtils.projectPointOnSegment(point, start, end);
        assertEquals(0.0, proj.getPerpendicularDistanceMeters(), 1.0, "Perpendicular distance should be 0");
        assertTrue(proj.getDistanceAlongSegmentMeters() > 55000 && proj.getDistanceAlongSegmentMeters() < 56000);
    }

    @Test
    @DisplayName("Projection of a point off to the side of the segment")
    void testPointPerpendicularToSegment() {
        Coordinates start = new Coordinates(0.0, 0.0);
        Coordinates end = new Coordinates(0.0, 1.0);
        Coordinates point = new Coordinates(0.001, 0.5); // ~111 meters North of midpoint

        GeoUtils.SegmentProjection proj = GeoUtils.projectPointOnSegment(point, start, end);
        assertTrue(proj.getPerpendicularDistanceMeters() > 100 && proj.getPerpendicularDistanceMeters() < 120,
                "Perpendicular distance should be ~111m, was " + proj.getPerpendicularDistanceMeters());
    }

    @Test
    @DisplayName("Projection of a point beyond segment end clamps to end")
    void testPointClampedToEnd() {
        Coordinates start = new Coordinates(0.0, 0.0);
        Coordinates end = new Coordinates(0.0, 1.0);
        Coordinates point = new Coordinates(0.0, 1.5);

        GeoUtils.SegmentProjection proj = GeoUtils.projectPointOnSegment(point, start, end);
        double totalSegLength = GeoUtils.haversineDistanceMeters(start, end);
        assertEquals(totalSegLength, proj.getDistanceAlongSegmentMeters(), 1.0);
    }

    @Test
    @DisplayName("Projection of a point before segment start clamps to start")
    void testPointClampedToStart() {
        Coordinates start = new Coordinates(0.0, 0.0);
        Coordinates end = new Coordinates(0.0, 1.0);
        Coordinates point = new Coordinates(0.0, -0.5);

        GeoUtils.SegmentProjection proj = GeoUtils.projectPointOnSegment(point, start, end);
        assertEquals(0.0, proj.getDistanceAlongSegmentMeters(), 0.01);
    }
}
