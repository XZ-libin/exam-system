package com.exam.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页参数收口：page 至少 1，size 限定在 1..200。
 * 不夹的话 size=-1 会让 MyBatis-Plus 直接放弃分页、把整张表拉回来。
 */
public final class PageQuery {

    public static final long MAX_SIZE = 200;

    private PageQuery() {
    }

    public static <T> Page<T> of(long page, long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        return new Page<>(safePage, safeSize);
    }
}
