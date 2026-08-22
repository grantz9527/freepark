package com.freepark.local.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "parking_lot")
public class ParkingLot extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LotType lotType = LotType.INTERNAL;

    @Column(length = 255)
    private String address;

    @Column(nullable = false)
    private int totalSpaces = 0;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean entryInterceptArrears = false;

    @Column(nullable = false)
    private boolean entryInterceptBlacklist = false;

    @Column(nullable = false)
    private boolean exitInterceptArrears = false;

    @Column(nullable = false)
    private boolean exitInterceptBlacklist = false;

    protected ParkingLot() {
    }

    public ParkingLot(
            String name,
            String code,
            LotType lotType,
            String address,
            int totalSpaces,
            boolean enabled) {
        this.name = name;
        this.code = code;
        this.lotType = lotType;
        this.address = address;
        this.totalSpaces = totalSpaces;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public LotType getLotType() {
        return lotType;
    }

    public String getAddress() {
        return address;
    }

    public int getTotalSpaces() {
        return totalSpaces;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEntryInterceptArrears() {
        return entryInterceptArrears;
    }

    public boolean isEntryInterceptBlacklist() {
        return entryInterceptBlacklist;
    }

    public boolean isExitInterceptArrears() {
        return exitInterceptArrears;
    }

    public boolean isExitInterceptBlacklist() {
        return exitInterceptBlacklist;
    }

    public void updateDetails(
            String name,
            LotType lotType,
            String address,
            int totalSpaces,
            boolean enabled) {
        this.name = name;
        this.lotType = lotType;
        this.address = address;
        this.totalSpaces = totalSpaces;
        this.enabled = enabled;
    }

    public void updateInterceptRules(java.util.List<InterceptRuleType> entryRules, java.util.List<InterceptRuleType> exitRules) {
        this.entryInterceptArrears = entryRules.contains(InterceptRuleType.ARREARS);
        this.entryInterceptBlacklist = entryRules.contains(InterceptRuleType.BLACKLIST);
        this.exitInterceptArrears = exitRules.contains(InterceptRuleType.ARREARS);
        this.exitInterceptBlacklist = exitRules.contains(InterceptRuleType.BLACKLIST);
    }
}
