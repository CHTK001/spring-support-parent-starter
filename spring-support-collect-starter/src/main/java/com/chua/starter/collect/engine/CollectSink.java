package com.chua.starter.collect.engine;

import com.chua.starter.collect.engine.model.CollectNode;

/**
 * 执行过程落库通道。
 *
 * <p>引擎不直接依赖 Mapper，由服务层实现本接口把节点日志与采集结果写入数据库，
 * 便于单节点调试时换成内存实现。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectSink {

    /**
     * 记录一次节点执行。
     *
     * @param node      节点
     * @param iteration 循环轮次
     * @param status    结果 SUCCESS/FAILURE/SKIPPED
     * @param costMs    耗时毫秒
     * @param message   摘要或异常信息
     * @param preview   出参预览
     */
    void nodeLog(CollectNode node, int iteration, String status, long costMs, String message, String preview);

    /**
     * 产出一条采集记录。
     *
     * @param entry 结果条目
     * @throws RuntimeException 结果未写入；引擎按节点异常边与容错策略处理，不能静默丢弃
     */
    void record(CollectRecordEntry entry);

    /**
     * 任务级信息输出，用于调试会话回传执行过程。
     *
     * @param message 信息文本
     */
    default void trace(String message) {
        // 默认丢弃，只有调试通道需要接收
    }

    /**
     * 绑定执行上下文，用于读取计量计数等运行期信息。
     *
     * <p>引擎在启动执行前调用一次；不使用上下文的实现可以不覆写。</p>
     *
     * @param context 执行上下文
     */
    default void attach(CollectContext context) {
        // 默认不接收上下文
    }

    /**
     * 任务收尾：回写计量、登记字段定义等收尾工作。
     *
     * <p>任务终态落定后无论成败都会调用；实现方应内部消化异常，不能把收尾失败
     * 反抛给执行线程。</p>
     */
    default void close() {
        // 默认无收尾动作
    }

    /**
     * 换一个流程归属来记节点日志。
     *
     * <p>子流程与宿主流程的节点ID 可以完全重名（界面新建的节点统一编号 n1、n2），日志只按
     * 节点ID 回给画布着色就会互相串台。实现方可以在返回的视图里给节点日志标注真正的归属流程，
     * 采集结果与任务计量仍按宿主流程记账。默认原样返回，第三方实现无需改动。</p>
     *
     * @param flowId 产生这些节点日志的流程ID
     * @return 带流程归属的落库通道
     */
    default CollectSink forFlow(Integer flowId) {
        return this;
    }
}
