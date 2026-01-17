package com.chua.starter.common.support.watch;

import com.chua.common.support.span.trace.TrackContext;

import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * 追踪管控
 */
public class NewTrackManager {

    private static final ThreadLocal<Stack<Span>> TRACK_CACHE = new ThreadLocal<>();

    public static Object invoke(Callable<?> callable) throws Exception {
        return callable.call();
    }


    public static void before() {
        Span currentSpan = NewTrackManager.getCurrentSpan();
        if (null == currentSpan) {
            String linkId = UUID.randomUUID().toString();
            TrackContext.setLinkId(linkId);
        }
        NewTrackManager.createEntrySpan();
    }

    public static Span getCurrentSpan() {
        Stack<Span> stack = TRACK_CACHE.get();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.peek();
    }

    /**
     * 获取栈
     *
     * @return 栈
     */
    public static Stack<Span> currentSpans() {
        return TRACK_CACHE.get();
    }


    public static void clear() {
        TRACK_CACHE.remove();
    }

    public static Span createEntrySpan() {
        Span span = createSpan();
        Stack<Span> stack = TRACK_CACHE.get();
        span.setEnterTime(System.nanoTime());
        if (stack.isEmpty()) {
            span.setId(span.getLinkId());
        } else {
            span.setId(UUID.randomUUID().toString());
            span.setPid(stack.peek().getId());
        }
        stack.push(span);
        return span;
    }


    public static Span getLastSpan() {
        Stack<Span> stack = TRACK_CACHE.get();
        if (stack == null || stack.isEmpty()) {
            TrackContext.clear();
            return null;
        }
        return stack.peek();
    }

    public static void registerSpan(Span span) {
        Stack<Span> spans = TRACK_CACHE.get();
        if (spans == null) {
            spans = new Stack<>();
        }
        spans.push(span);
        TRACK_CACHE.remove();
        TRACK_CACHE.set(spans);
    }

    private static Span createSpan() {
        Stack<Span> stack = TRACK_CACHE.get();
        if (stack == null) {
            stack = new Stack<>();
            TRACK_CACHE.remove();
            TRACK_CACHE.set(stack);
        }

        String linkId;
        if (stack.isEmpty()) {
            linkId = TrackContext.getLinkId();
            if (linkId == null) {
                linkId = "nvl";
                TrackContext.setLinkId(linkId);
            }
        } else {
            Span span = stack.peek();
            linkId = span.getLinkId();
            TrackContext.setLinkId(linkId);
        }
        return new Span(linkId);
    }

    public static void refreshCost(Span span) {
        if(null == span) {
            return;
        }

        span.setCostTime(System.nanoTime() - span.getEnterTime());
    }
}
