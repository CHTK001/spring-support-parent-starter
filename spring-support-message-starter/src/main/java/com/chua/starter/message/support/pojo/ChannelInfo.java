package com.chua.starter.message.support.pojo;

/**
 * 消息渠道信息
 * <p>
 * 描述一个已注册的消息推送渠道（消息push SPI 实现）的标识、名称、类型与支持能力，
 * 数据来自实现类上的 {@code @SpiDescribe}/{@code @SpiSupport} 注解，前端据此渲染渠道下拉，
 * 新增渠道无需改动前端代码。
 * </p>
 *
 * @param channel      渠道标识（SPI 名称，如 EMAIL、ALIBABA-SMS、STATION）
 * @param label        渠道中文名称
 * @param type         渠道类型（如 EMAIL/SMS/STATION/DINGDING/FEISHU/DESKTOP）
 * @param desc         渠道功能描述
 * @param capabilities 渠道支持的能力/类型列表
 * @since 2026/08/03
 * @author CH
 */
public record ChannelInfo(
        String channel,
        String label,
        String type,
        String desc,
        String[] capabilities
) {
    /**
     * 规范构造器：能力数组做防御性拷贝。
     *
     * <p>value class 前置条件——数组组件必须深不可变。SPI 定义未声明支持类型时
     * {@code getSupportedTypes()} 返回 null，故此处保留 null 语义。</p>
     *
     * @param channel      渠道标识（SPI 名称，如 EMAIL、ALIBABA-SMS、STATION）
     * @param label        渠道中文名称
     * @param type         渠道类型（如 EMAIL/SMS/STATION/DINGDING/FEISHU/DESKTOP）
     * @param desc         渠道功能描述
     * @param capabilities 渠道支持的能力/类型列表，可为 null
     */
    public ChannelInfo {
        capabilities = capabilities == null ? null : capabilities.clone();
    }

    /**
     * 访问器覆写：返回内部数组的副本，避免外部改写内部状态。
     *
     * @return 能力数组副本，源为 null 时返回 null
     */
    @Override
    public String[] capabilities() {
        return capabilities == null ? null : capabilities.clone();
    }
}
