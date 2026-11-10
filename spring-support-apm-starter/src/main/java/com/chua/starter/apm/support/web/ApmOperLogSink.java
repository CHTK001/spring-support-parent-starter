package com.chua.starter.apm.support.web;

/**
 * APM 接口的操作日志落点（SPI）。
 *
 * <p>APM 接口不走 Spring MVC（嵌入模式是独立 Servlet、独立模式自起 HTTP 服务），
 * 因此 {@code @SysLog} 切面拦不到它。本接口由 {@link ApmDispatcher} 在<b>写操作</b>
 * （{@code POST}）执行完后调用，宿主模块提供实现即可把 APM 的设置变更留痕。</p>
 *
 * <p>只在写操作上回调：GET 是查询，每次打开页面都记一条会把时间线淹掉，
 * 也失去了「谁改了什么」这个日志唯一有价值的语义。</p>
 *
 * <p>实现方负责落库与异常兜底：{@link ApmDispatcher} 已经吞掉实现抛出的运行时异常，
 * 但实现自己也应在写失败时只打日志，不要让「记不下来」变成「改不了设置」。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
@FunctionalInterface
public interface ApmOperLogSink {

    /**
     * 记录一次 APM 写操作。
     *
     * @param request 原始请求（方法、路径、参数）
     * @param result  处理结果（成功/失败与原因）
     * @param costMs  处理耗时（毫秒）
     */
    void record(ApmRequest request, ApmResult result, long costMs);
}
