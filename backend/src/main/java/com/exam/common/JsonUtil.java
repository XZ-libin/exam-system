package com.exam.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON 列（qz_question.options / answer、an_answer_item.user_answer 等）与 Java 的互转。
 * 实体里这些字段都是 String，避免引入 MyBatis typeHandler。
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private JsonUtil() {
    }

    /** 写成 JSON 字符串存库；空集合写成 []，null 保持 null */
    public static String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw BizException.param("JSON 字段格式不正确");
        }
    }

    /** 读成字符串数组；库里是原始字符串（如 "A"）时也兼容 */
    public static List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        String text = json.trim();
        try {
            if (text.startsWith("[")) {
                List<String> list = MAPPER.readValue(text, STRING_LIST);
                return list == null ? new ArrayList<>() : list;
            }
            if (text.startsWith("\"")) {
                return new ArrayList<>(List.of(MAPPER.readValue(text, String.class)));
            }
            return new ArrayList<>(List.of(text));
        } catch (Exception e) {
            throw BizException.param("JSON 字段解析失败：" + text);
        }
    }

    /** 判分用的归一化：去首尾空格 */
    public static List<String> readAnswerList(String json) {
        List<String> list = readStringList(json);
        return list.stream().map(s -> s == null ? "" : s.trim()).toList();
    }

    public static boolean isJsonArray(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            MAPPER.readTree(text);
            return text.trim().startsWith("[");
        } catch (Exception e) {
            return false;
        }
    }
}
