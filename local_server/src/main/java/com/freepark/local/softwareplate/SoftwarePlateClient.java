package com.freepark.local.softwareplate;

import java.io.IOException;

import com.freepark.local.yolo26plate.Yolo26PlateClient.RecognitionResult;

public interface SoftwarePlateClient {

    SoftwarePlateProvider provider();

    /**
     * 使用当前 provider 的配置识别一张图片。
     *
     * @param imageBytes   图片二进制（jpg/png 均可，交给上游解析）
     * @param originalName 原始文件名（用于 debug 日志，可为 null）
     * @param imageId      用户/前端给的自定义 image id（可为 null）
     * @param minConfidenceOverride 覆盖配置里的最小置信度，null 表示使用配置默认
     * @return 统一识别结果（与 yolo26 保持一致的结构，便于前后端复用）
     */
    RecognitionResult recognize(byte[] imageBytes, String originalName, String imageId, Double minConfidenceOverride)
            throws IOException;
}
