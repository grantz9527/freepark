package com.freepark.local.domain;

/**
 * 臻识机内白名单同步状态（本地字段，不参与云端协同）。
 *
 * <ul>
 *   <li>{@link #DELIVERED} 已下发 — 当前生效且已下发到相机</li>
 *   <li>{@link #PENDING_DELIVER} 待下发 — 当前生效，等待/正在下发</li>
 *   <li>{@link #EXPIRED_REMOVED} 过期已移除 — 已过期且已从相机删除</li>
 *   <li>{@link #EXPIRED_PENDING_REMOVE} 过期待移除 — 已过期，等待从相机删除</li>
 *   <li>{@link #NOT_EFFECTIVE} 未生效 — 未到开始时间，或非业主/月租，或已停用</li>
 * </ul>
 */
public enum CameraWhitelistSyncStatus {
    DELIVERED,
    PENDING_DELIVER,
    EXPIRED_REMOVED,
    EXPIRED_PENDING_REMOVE,
    NOT_EFFECTIVE;

    /** 该卡当前应支撑机内名单（已下发或待下发）。 */
    public boolean desiresCameraPresence() {
        return this == DELIVERED || this == PENDING_DELIVER;
    }

    public boolean needsDeliver() {
        return this == PENDING_DELIVER;
    }

    public boolean needsExpireRemove() {
        return this == EXPIRED_PENDING_REMOVE;
    }
}
