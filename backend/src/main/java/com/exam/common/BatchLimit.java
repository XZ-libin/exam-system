package com.exam.common;

import java.util.List;

/**
 * 客户端提交的列表长度封顶。MyBatis 会把长列表摊成一条条 insert，不限长度时
 * 单个请求就能把题库/答题明细写爆，所以每个收列表的入口都要过一遍。
 */
public final class BatchLimit {

    public static final int MAX_ITEMS = 200;

    private BatchLimit() {
    }

    public static void check(List<?> items, String what) {
        if (items != null && items.size() > MAX_ITEMS) {
            throw BizException.param(what + "一次最多 " + MAX_ITEMS + " 条，本次提交了 " + items.size() + " 条");
        }
    }
}
