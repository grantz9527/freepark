package com.freepark.local.softwareplate;

import java.util.List;

import com.freepark.local.domain.PlateColor;

public final class SoftwarePlateModels {

    private SoftwarePlateModels() {
    }

    public record BBox(double x1, double y1, double x2, double y2) {
    }

    public record DetectedPlate(
            BBox bbox,
            double detectConfidence,
            int cls,
            List<double[]> keypoints,
            String plate,
            double plateConfidence,
            String plateColorZh,
            PlateColor plateColor,
            double plateColorConfidence,
            String error,
            Double score,
            Boolean plateValid,
            Boolean suppressed) {

        public DetectedPlate(
                BBox bbox, double detectConfidence, int cls, List<double[]> keypoints,
                String plate, double plateConfidence, String plateColorZh, PlateColor plateColor,
                double plateColorConfidence, String error) {
            this(bbox, detectConfidence, cls, keypoints, plate, plateConfidence, plateColorZh, plateColor,
                    plateColorConfidence, error, null, null, null);
        }

        public DetectedPlate withRanking(double score, boolean plateValid, boolean suppressed) {
            return new DetectedPlate(bbox, detectConfidence, cls, keypoints, plate, plateConfidence, plateColorZh,
                    plateColor, plateColorConfidence, error, score, plateValid, suppressed);
        }
    }

    public record RecognitionResult(
            String imageId,
            int elapsedMs,
            int count,
            List<DetectedPlate> plates,
            DetectedPlate best,
            String device,
            String upstreamBaseUrl) {
    }
}
