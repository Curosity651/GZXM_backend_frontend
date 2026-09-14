package com.gzxm.server.common.api;

import java.util.List;

public record PageResult<T>(List<T> items, long page, long size, long total) {
    public static <T> PageResult<T> of(List<T> items, long page, long size, long total) {
        return new PageResult<>(items, page, size, total);
    }
}
