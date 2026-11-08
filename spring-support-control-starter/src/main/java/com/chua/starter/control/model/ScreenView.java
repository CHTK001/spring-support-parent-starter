package com.chua.starter.control.model;

/**
 * 远端屏幕视图。
 *
 * @param id     屏幕 ID
 * @param name   屏幕名称
 * @param width  宽度
 * @param height 高度
 * @author CH
 * @since 4.0.0.51
 */
public record ScreenView(String id, String name, int width, int height) {
}
