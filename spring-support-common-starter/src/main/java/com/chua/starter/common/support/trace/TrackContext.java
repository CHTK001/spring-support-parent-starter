package com.chua.starter.common.support.trace;

/**
 * 链路追踪上下文。
 */
public final class TrackContext {

    /** Link_id */
    private static final ThreadLocal<String> LINK_ID = new ThreadLocal<>();

    private TrackContext() {
    }

    public static void setLinkId(String linkId) {
        LINK_ID.set(linkId);
    }

    public static String getLinkId() {
        return LINK_ID.get();
    }

    public static void clear() {
        LINK_ID.remove();
    }
}

