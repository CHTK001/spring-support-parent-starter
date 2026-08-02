package com.chua.starter.device.spi.model;

/**
 * 云台控制指令枚举
 *
 * @author CH
 * @since 4.0.0.42
 */
public enum PtzCommand {

    /**
     * 向上
     */
    UP,

    /**
     * 向下
     */
    DOWN,

    /**
     * 向左
     */
    LEFT,

    /**
     * 向右
     */
    RIGHT,

    /**
     * 停止
     */
    STOP,

    /**
     * 变倍放大
     */
    ZOOM_IN,

    /**
     * 变倍缩小
     */
    ZOOM_OUT,

    /**
     * 聚焦近
     */
    FOCUS_IN,

    /**
     * 聚焦远
     */
    FOCUS_OUT,

    /**
     * 光圈大
     */
    IRIS_IN,

    /**
     * 光圈小
     */
    IRIS_OUT
}