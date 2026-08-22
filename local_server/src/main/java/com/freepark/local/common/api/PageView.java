package com.freepark.local.common.api;

import java.util.List;

public record PageView<T>(List<T> items, long total, int page, int size) {
}
