package com.chua.starter.collect.engine.spi;

/**
 * 子流程来源扩展点。
 *
 * <p>process 节点不查库，由实现方按流程ID交出流程图；默认实现是流程服务。第三方 jar 可以用
 * {@code @Spi} 注册自己的来源（例如从 XML 或资源文件加载子流程），实现零侵入接入。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectFlowProvider {

    /**
     * 按流程ID取子流程定义。
     *
     * @param flowId 流程ID
     * @return 子流程定义，取不到返回 null，由调用方给出可读错误
     */
    CollectFlowRef load(Integer flowId);
}
