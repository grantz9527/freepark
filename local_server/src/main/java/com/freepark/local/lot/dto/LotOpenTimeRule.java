package com.freepark.local.lot.dto;

/** 车场对外开放时段的一个时段；day 为 ISO 周几（1=周一 … 7=周日），start/end 为 HH:mm。 */
public record LotOpenTimeRule(Integer day, String start, String end) {
}
