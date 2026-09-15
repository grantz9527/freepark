package com.freepark.local.softwareplate;

import java.util.List;

/**
 * 软件车牌引擎。{@link #YOLO26_PLATE} 仅用于读取历史库值，运行时一律视为 {@link #HYPER_LPR3}。
 */
public enum SoftwarePlateProvider {
    YOLO26_PLATE,
    HYPER_LPR3;

    public static SoftwarePlateProvider effective(SoftwarePlateProvider value) {
        return HYPER_LPR3;
    }

    public static List<SoftwarePlateProvider> selectable() {
        return List.of(HYPER_LPR3);
    }
}
