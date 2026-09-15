package com.freepark.local.softwareplate;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** 国内车牌结构校验，供软件识别与 Frigate 补全共用。 */
public final class PlateShape {

    private static final Set<String> CN_PROVINCE_PREFIX = new HashSet<>(Arrays.asList(
            "京", "津", "沪", "渝", "冀", "豫", "云", "辽", "黑", "湘", "皖", "鲁",
            "新", "苏", "浙", "赣", "鄂", "桂", "甘", "晋", "蒙", "陕", "吉", "闽",
            "贵", "粤", "青", "藏", "川", "宁", "琼", "使", "领", "学", "警", "港", "澳"));

    private static final Pattern SECOND_CHAR_LETTER = Pattern.compile("^[A-Z]$");
    private static final Pattern PLATE_SHAPE = Pattern.compile("^[\\u4e00-\\u9fa5A-Z][A-Z][A-Z0-9]{5,6}$");

    private PlateShape() {
    }

    public static boolean isValid(String plate) {
        if (plate == null || plate.isBlank()) {
            return false;
        }
        String t = plate.trim().toUpperCase();
        int len = t.codePointCount(0, t.length());
        if (len < 7 || len > 8) {
            return false;
        }
        String first = new String(Character.toChars(t.codePointAt(0)));
        if (!CN_PROVINCE_PREFIX.contains(first)) {
            return false;
        }
        int secondIdx = Character.offsetByCodePoints(t, 0, 1);
        String second = t.substring(secondIdx, Character.offsetByCodePoints(t, secondIdx, 1));
        if (!SECOND_CHAR_LETTER.matcher(second).matches()) {
            return false;
        }
        return PLATE_SHAPE.matcher(t).matches();
    }
}
