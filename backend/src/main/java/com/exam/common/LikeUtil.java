package com.exam.common;

/**
 * 模糊查询转义。用户输入的 % 和 _ 是 SQL 的 LIKE 通配符，
 * 不转义的话搜「50%」会把全部记录都匹配出来。
 */
public final class LikeUtil {

    private LikeUtil() {
    }

    public static String escape(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return keyword;
        }
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
