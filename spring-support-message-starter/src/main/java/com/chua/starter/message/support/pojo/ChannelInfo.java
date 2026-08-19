package com.chua.starter.message.support.pojo;

/**
 * 消息渠道信息
 * <p>
 * 描述一个已注册的消息推送渠道（MessagePush SPI 实现）的标识、名称、类型与支持能力，
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
}
