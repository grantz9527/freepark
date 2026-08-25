package com.freepark.local.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "pattern_allowlist",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = {"lot_id", "name"}),
            @UniqueConstraint(columnNames = {"lot_id", "pattern"})
        })
public class PatternAllowlist extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private ParkingLot lot;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 255)
    private String pattern;

    @Column(length = 255)
    private String remark;

    @Column(nullable = false)
    private boolean enabled = true;

    protected PatternAllowlist() {
    }

    public PatternAllowlist(ParkingLot lot, String name, String pattern, String remark, boolean enabled) {
        this.lot = lot;
        this.name = name.trim();
        this.pattern = pattern.trim();
        this.remark = remark;
        this.enabled = enabled;
    }

    public ParkingLot getLot() {
        return lot;
    }

    public String getName() {
        return name;
    }

    public String getPattern() {
        return pattern;
    }

    public String getRemark() {
        return remark;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void updateDetails(String name, String pattern, String remark, boolean enabled) {
        this.name = name.trim();
        this.pattern = pattern.trim();
        this.remark = remark;
        this.enabled = enabled;
    }
}
