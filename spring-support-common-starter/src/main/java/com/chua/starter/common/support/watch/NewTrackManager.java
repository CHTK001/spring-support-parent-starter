package com.chua.starter.common.support.watch;

import com.chua.starter.common.support.trace.TrackContext;

import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
* 追踪管控
* @author CH
* @since 4.0.0
 */
public class NewTrackManager {

    /**
    * 新track管理器。
     */
    private NewTrackManager() {
    }

    /** Track_缓存 */
    private static final ThreadLocal<Stack<Span>> TRACK_CACHE = new ThreadLocal<>();

    /**
    * 调用
    *
    * @param callable callable
    * @return invoke的结果
     */
    public static Object invoke(Callable<?> callable) throws Exception {
        return callable.call();
    }


    /** 之前 */
    public static void before() {
        Span currentSpan = NewTrackManager.getCurrentSpan();
        if (null == currentSpan) {
            String linkId = UUID.randomUUID().toString();
            TrackContext.setLinkId(linkId);
        }
        NewTrackManager.createEntrySpan();
    }

    /**
    * 获取当前span
    *
    * @return 获取当前span的结果
     */
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


    /** Clear */
    public static void clear() {
        TRACK_CACHE.remove();
    }

    /**
    * 创建entryspan
    *
    * @return 创建entryspan的结果
     */
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


    /**
    * 获取最后一个span
    *
    * @return 获取最后一个span的结果
     */
    public static Span getLastSpan() {
        Stack<Span> stack = TRACK_CACHE.get();
        if (stack == null || stack.isEmpty()) {
            TrackContext.clear();
            return null;
        }
        return stack.peek();
    }

    /**
    * 注册Span
    *
    * @param span span
     */
    public static void registerSpan(Span span) {
        Stack<Span> spans = TRACK_CACHE.get();
        if (spans == null) {
            spans = new Stack<>();
        }
        spans.push(span);
        TRACK_CACHE.remove();
        TRACK_CACHE.set(spans);
    }

    /**
    * 创建Span
    *
    * @return 创建span的结果
     */
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
        /**
        * Span。
        * @param linkId 链接标识
        * @return Span的结果
         */
        return new Span(linkId);
    }

    /**
    * refreshcost
    *
    * @param span span
     */
    public static void refreshCost(Span span) {
        if(null == span) {
            return;
        }

        span.setCostTime(System.nanoTime() - span.getEnterTime());
    }
}

