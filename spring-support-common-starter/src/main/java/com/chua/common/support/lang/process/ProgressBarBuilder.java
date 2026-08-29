package com.chua.common.support.lang.process;

/**
 * 进度条构建器
 *
 * @author CH
 */
public class ProgressBarBuilder {

    private String taskName;
    private int initialMax = 0;
    private ProgressBarStyle style;

    /**
     * 设置任务名称
     *
     * @param taskName 任务名称
     * @return 当前构建器实例
     */
    public ProgressBarBuilder setTaskName(String taskName) {
        this.taskName = taskName;
        return this;
    }

    /**
     * 设置初始最大进度值
     *
     * @param initialMax 初始最大进度值
     * @return 当前构建器实例
     */
    public ProgressBarBuilder setInitialMax(int initialMax) {
        this.initialMax = initialMax;
        return this;
    }

    /**
     * 设置进度条样式
     *
     * @param style 样式
     * @return 当前构建器实例
     */
    public ProgressBarBuilder setStyle(ProgressBarStyle style) {
        this.style = style;
        return this;
    }

    /**
     * 构建进度条实例
     *
     * @return 进度条实例
     */
    public ProgressBar build() {
        return new ProgressBar(initialMax);
    }
}
