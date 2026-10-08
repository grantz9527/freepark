package com.freepark.local.barrier.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 更新一台识别一体机的默认屏显：两行、停留秒数和每行播放方式保存后下发到控制板。
 * 行数不在此修改，由驱动按型号上报。屏幕方向硬件不支持，不再由此接口写入。
 */
public record UpdateScreenOrientationRequest(
        String line1,
        String line2,
        @Min(0) @Max(255) Integer staySeconds,
        Integer playMode1,
        Integer playMode2) {
}
