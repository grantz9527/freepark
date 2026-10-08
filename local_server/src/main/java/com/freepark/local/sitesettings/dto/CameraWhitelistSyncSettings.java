package com.freepark.local.sitesettings.dto;

public record CameraWhitelistSyncSettings(boolean owner, boolean monthly, String callbackBaseUrl) {
}
