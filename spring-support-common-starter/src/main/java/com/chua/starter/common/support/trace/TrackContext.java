package com.chua.starter.common.support.trace;

/**
 * 链路追踪上下文。
 */
public final class TrackContext {

    /** Link_id */
    private static final ThreadLocal<String> LINK_ID = new ThreadLocal<>();

    /** 创建 TrackContext 实例 */
    private TrackContext() {
    }

    /** 设置LinkId */
    public static void setLinkId(String linkId) {
        LINK_ID.set(linkId);
    }

    /** 获取LinkId */
    public static String getLinkId() {
        return LINK_ID.get();
    }

    /** Clear */
    public static void clear() {
        LINK_ID.remove();
    }
}

