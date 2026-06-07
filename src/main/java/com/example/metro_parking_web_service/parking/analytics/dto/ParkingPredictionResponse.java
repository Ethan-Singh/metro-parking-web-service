/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.dto;

import java.util.List;

public record ParkingPredictionResponse(String slug, List<PredictionPoint> predictions) {}
