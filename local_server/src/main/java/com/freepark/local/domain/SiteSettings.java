package com.freepark.local.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "site_settings")
@EntityListeners(AuditingEntityListener.class)
public class SiteSettings {

    public static final String SINGLETON_ID = "default";

    @Id
    @Column(length = 32, nullable = false, updatable = false)
    private String id = SINGLETON_ID;

    @Column(nullable = false, length = 16)
    private String defaultLocale;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_plate_color", nullable = false, length = 32)
    private PlateColor defaultPlateColor = PlateColor.BLUE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "site_settings_allowed_plate_color",
            joinColumns = @JoinColumn(name = "settings_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "plate_color", nullable = false, length = 32)
    @OrderColumn(name = "sort_order")
    private List<PlateColor> allowedPlateColors = new ArrayList<>();

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected SiteSettings() {
    }

    public SiteSettings(String defaultLocale, String timezone) {
        this.defaultLocale = defaultLocale;
        this.timezone = timezone;
        this.defaultPlateColor = PlateColor.BLUE;
        this.allowedPlateColors = new ArrayList<>(PlateColorSupport.defaultChinaAllowed());
    }

    public String getId() {
        return id;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public PlateColor getDefaultPlateColor() {
        return defaultPlateColor;
    }

    public void setDefaultPlateColor(PlateColor defaultPlateColor) {
        this.defaultPlateColor = defaultPlateColor;
    }

    public List<PlateColor> getAllowedPlateColors() {
        return allowedPlateColors;
    }

    public void setAllowedPlateColors(List<PlateColor> allowedPlateColors) {
        this.allowedPlateColors = new ArrayList<>(allowedPlateColors);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
