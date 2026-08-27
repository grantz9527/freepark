package com.freepark.local.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "lot_access_judgment_order", joinColumns = @JoinColumn(name = "lot_id"))
    @OrderColumn(name = "sort_order")
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 32)
    private List<AccessJudgmentRuleType> accessJudgmentOrder = new ArrayList<>(AccessJudgmentRuleType.defaultOrder());

    @Column(name = "map_data", columnDefinition = "TEXT")
    private String mapData;

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
        this.accessJudgmentOrder = new ArrayList<>(AccessJudgmentRuleType.defaultOrder());
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

    public List<AccessJudgmentRuleType> getAccessJudgmentOrder() {
        return accessJudgmentOrder;
    }

    public List<AccessJudgmentRuleType> effectiveAccessJudgmentOrder() {
        if (accessJudgmentOrder == null || accessJudgmentOrder.isEmpty()) {
            return AccessJudgmentRuleType.defaultOrder();
        }
        if (accessJudgmentOrder.size() != AccessJudgmentRuleType.values().length) {
            return AccessJudgmentRuleType.defaultOrder();
        }
        if (!java.util.EnumSet.copyOf(accessJudgmentOrder).equals(java.util.EnumSet.allOf(AccessJudgmentRuleType.class))) {
            return AccessJudgmentRuleType.defaultOrder();
        }
        return List.copyOf(accessJudgmentOrder);
    }

    public void updateAccessJudgmentOrder(List<AccessJudgmentRuleType> ruleOrder) {
        this.accessJudgmentOrder = new ArrayList<>(ruleOrder);
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

    public String getMapData() {
        return mapData;
    }

    public void updateMapData(String mapData) {
        this.mapData = mapData;
    }

    public void updateInterceptRules(java.util.List<InterceptRuleType> entryRules, java.util.List<InterceptRuleType> exitRules) {
        this.entryInterceptArrears = entryRules.contains(InterceptRuleType.ARREARS);
        this.entryInterceptBlacklist = entryRules.contains(InterceptRuleType.BLACKLIST);
        this.exitInterceptArrears = exitRules.contains(InterceptRuleType.ARREARS);
        this.exitInterceptBlacklist = exitRules.contains(InterceptRuleType.BLACKLIST);
    }
}
