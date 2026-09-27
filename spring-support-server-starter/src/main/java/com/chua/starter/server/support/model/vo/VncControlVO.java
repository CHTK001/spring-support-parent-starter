package com.chua.starter.server.support.model.vo;

/**
 * 直连桌面对端事件（平台 -> 浏览器）
 * <p>
 * 桌面像素走二进制帧，不经本对象；这里只描述会话生命周期：
 * {@code init} 携带首帧尺寸与桌面名、{@code size} 表示桌面尺寸变化、
 * {@code error} 表示失败原因。未使用的字段固定为 null。
 * </p>
 *
 * @param c      事件类型：init/size/error
 * @param width  桌面宽度
 * @param height 桌面高度
 * @param name   桌面名（VNC 服务端自报）
 * @param msg    错误原因
 * @author CH
 * @since 4.0.0.43
 */
public record VncControlVO(
        String c,
        Integer width,
        Integer height,
        String name,
        String msg
) {

    /**
     * 会话建立事件
     *
     * @param width  桌面宽度
     * @param height 桌面高度
     * @param name   桌面名
     * @return 事件
     */
    public static VncControlVO init(int width, int height, String name) {
        return new VncControlVO("init", width, height, name, null);
    }

    /**
     * 桌面尺寸变化事件
     *
     * @param width  桌面宽度
     * @param height 桌面高度
     * @return 事件
     */
    public static VncControlVO size(int width, int height) {
        return new VncControlVO("size", width, height, null, null);
    }

    /**
     * 错误事件
     *
     * @param msg 原因
     * @return 事件
     */
    public static VncControlVO error(String msg) {
        return new VncControlVO("error", null, null, null, msg);
    }
}
