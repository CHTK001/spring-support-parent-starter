package com.chua.starter.ai.support.manage;

/**
* Nomifun 前后端 webSocket 主题常量。
*
* <p>前后端必须保持同名，前端在 <code>src/common/adapter/ipcBridge.ts</code> / 业务组件中
* 以 {@code wsEmitter('model.metrics.push')} 订阅对应的 名称。</p>
*
* @since 4.0.0.42
* @author CH
 */
public final class SysAiWsTopics {

    /**
    * 模型价格/指标定时推送：服务端定时聚合「输入价格/输出价格/缓存价格/速度/延迟/智能」后，
    * 以 {@code { name: 'model.metrics.push', data: ModelMetricsSnapshot }} 广播。
    * 前端 {@code GET /api/model-metrics} 获取同一份快照。
    */
    public static final String MODEL_METRICS_PUSH = "model.metrics.push";

    /**
    * sysaiwstopics。
    */
    private SysAiWsTopics() {
    }
}
