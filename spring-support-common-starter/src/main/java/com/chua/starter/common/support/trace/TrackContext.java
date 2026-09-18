package com.chua.starter.common.support.trace;

/**
* 链路追踪上下文。
* @author CH
* @since 4.0.0
 */
public final class TrackContext {

    /** 链接_标识 */
    private static final ThreadLocal<String> LINK_ID = new ThreadLocal<>();

    /** 创建 track上下文 实例 */
    private TrackContext() {
    }

    /**
    * 设置链接标识
    *
    * @param linkId 链接标识
    */
    public static void setLinkId(String linkId) {
        LINK_ID.set(linkId);
    }

    /**
    * 获取链接标识
    *
    * @return 获取链接id的结果
    */
    public static String getLinkId() {
        return LINK_ID.get();
    }

    /** Clear */
    public static void clear() {
        LINK_ID.remove();
    }
}

