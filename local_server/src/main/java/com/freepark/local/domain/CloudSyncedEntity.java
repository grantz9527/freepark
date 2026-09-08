package com.freepark.local.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * 与云端「边缘配置同步」绑定的本地领域实体基类：额外携带云端主键 {@code cloud_id}
 * （云端自增 Long），用于增量帧按云端 id 做 upsert/delete 定位，以及全量帧按
 * cloud_id 对齐既有本地行（保留本地 UUID 主键、不破坏外部引用）。
 *
 * <p>{@code cloudId == null} 表示本地自建、尚未与任何云端条目关联；该行会在下一次
 * 所属车场的全量快照整批替换时被清除（云端配置为权威源）。</p>
 */
@MappedSuperclass
public abstract class CloudSyncedEntity extends BaseEntity {

    @Column(name = "cloud_id")
    private Long cloudId;

    public Long getCloudId() {
        return cloudId;
    }

    public void setCloudId(Long cloudId) {
        this.cloudId = cloudId;
    }
}
