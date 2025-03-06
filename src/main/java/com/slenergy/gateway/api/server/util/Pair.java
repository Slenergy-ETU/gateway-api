package com.slenergy.gateway.api.server.util;

import lombok.Getter;

/**
 * class Pair description
 *
 * @author Eric Li
 * @version 1.0
 *
 * @since 2024-04-22
 * @since 1.0
 */
@Getter
public final class Pair<L, R> {

    private static final Pair<Object, Object> EMPTY = new Pair<>(null, null);

    private final L left;
    private final R right;

    private static <L, R> Pair<L, R> empty() {
        return (Pair<L, R>) EMPTY;
    }

    public static <L, R> Pair<L, R> create(L left, R right) {
        if (left == null && right == null)
            return empty();

        return new Pair<>(left, right);
    }

    private Pair(final L left, final R right) {
        this.left = left;
        this.right = right;
    }

}
