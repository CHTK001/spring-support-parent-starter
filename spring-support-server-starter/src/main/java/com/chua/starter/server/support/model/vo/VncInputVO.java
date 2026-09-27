package com.chua.starter.server.support.model.vo;

/**
 * 直连桌面输入指令（浏览器 -> 平台）
 * <p>
 * 文本帧只承载输入语义，字段缺失即为未提供，由桥接层按零值处理；
 * 桌面像素与回执走二进制帧与 {@link VncControlVO}，不用本对象回带。
 * </p>
 *
 * @param c      指令类型：key 按键 / ptr 指针 / refresh 请求刷新
 * @param x      指针横坐标
 * @param y      指针纵坐标
 * @param keysym 键标（X11 keysym）
 * @param down   是否按下，空视为按下
 * @param mask   指针按键掩码（位 0 左、位 1 中、位 2 右、位 3 滚轮上、位 4 滚轮下）
 * @author CH
 * @since 4.0.0.43
 */
public record VncInputVO(
        String c,
        Integer x,
        Integer y,
        Integer keysym,
        Boolean down,
        Integer mask
) {
}
