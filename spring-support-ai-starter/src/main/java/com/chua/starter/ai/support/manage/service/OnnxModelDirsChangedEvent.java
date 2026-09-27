package com.chua.starter.ai.support.manage.service;

/**
 * ONNX 外部模型目录集合变更事件。
 *
 * <p>外部目录注册/注销会改变 {@code ModelRegistry} 的条目，而本地模型目录清单带 TTL 缓存：
 * 若缓存在目录注册之前被填充，ONNX 提供商的「刷新模型」就会在一段时间内看不到这些目录。
 * 用事件通知缓存持有方失效，避免两个服务互相注入形成循环依赖。</p>
 *
 * @param modelRoot 变更后的模型根目录
 * @since 4.0.0.43
 * @author CH
 */
public record OnnxModelDirsChangedEvent(String modelRoot) {
}
