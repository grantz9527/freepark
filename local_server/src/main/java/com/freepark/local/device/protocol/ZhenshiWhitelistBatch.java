package com.freepark.local.device.protocol;

import java.util.List;

/**
 * 臻识 HTTP 推送协议 3.7.5 一次白名单下发（同一 operate_type，最多 5 条）。
 *
 * <p>{@code operateType}：0 增加，1 删除。删除条目只需车牌；空车牌表示清空机内名单。
 * {@code replyUrl} 已废弃：多数机型不回调，协议字段本身可选，下发时不再附带。
 */
public record ZhenshiWhitelistBatch(int operateType, long msgId, String replyUrl, List<Item> items) {

    public static final int ADD = 0;
    public static final int DELETE = 1;
    public static final int MAX_ITEMS = 5;

    public record Item(String plate, String enableTime, String overdueTime) {
    }

    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }

    /** @deprecated 回调核对已取消；保留仅兼容旧设置校验。 */
    @Deprecated
    public static String normalizeCallbackBase(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.trim().replaceAll("\\s+", "");
        try {
            java.net.URI uri = java.net.URI.create(value.contains("://") ? value : "http://" + value);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return "";
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(scheme.toLowerCase(java.util.Locale.ROOT)).append("://").append(uri.getHost());
            if (uri.getPort() > 0) {
                sb.append(':').append(uri.getPort());
            }
            String path = uri.getPath();
            if (path != null && !path.isBlank()) {
                path = path.replaceAll("/+$", "");
                if (!path.isEmpty() && !"/".equals(path)) {
                    sb.append(path);
                }
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }
}
