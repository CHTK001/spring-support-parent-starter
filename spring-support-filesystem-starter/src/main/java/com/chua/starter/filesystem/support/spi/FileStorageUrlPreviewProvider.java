package com.chua.starter.filesystem.support.spi;

import com.chua.common.support.spi.annotations.Spi;

/**
 * 三方 URL 预览能力 SPI 接口。
 *
 * <p>用于把「能否预览外部地址」从一个布尔开关收敛成<b>可枚举的能力实现</b>：
 * 管理员在系统配置里选择某个实现，命中后 {@code ?url=<base64(http(s)://…)>}
 * 才会被放行；实现名单由 {@code ?format=capabilities} 下发给前端，
 * 前端据此渲染可选项，不再硬编码「开/关」。</p>
 *
 * <p><b>为什么不是一个开关</b>：开关把「谁能处理外部地址」和「处理发生在哪一侧」
 * 压成了同一个布尔值。扩展方接入自己的外部地址处理器（转发、鉴权、白名单、
 * 走内网网关等）时，前端无从表达，服务端也没有校验入口——只能把开关打开，
 * 等于对所有外部地址全量放行。拆成 SPI 实现后，每个实现自己声明是否支持
 * {@code ?url=}，未选择或选择到不支持处理的实现时一律 403。</p>
 *
 * <p>实现类通过 {@code META-INF/extensions/} 注册，扩展方仅需新增实现并登记即可。
 * {@link #getDescription()} 与 {@link #supportsUrlPreview()} 有默认值，老实现无需改动。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Spi
public interface FileStorageUrlPreviewProvider {

    /**
     * 获取实现唯一名称。
     *
     * <p>写入 {@code FileStorageSetting#thirdUrlPreview} 持久化，需全局唯一。</p>
     *
     * @return 实现名（如 {@code SERVER}）
     */
    String getName();

    /**
     * 获取实现展示名。
     *
     * @return 展示名（如 {@code 后台}）
     */
    String getLabel();

    /**
     * 获取实现说明（下发给前端作为选项描述）。
     *
     * @return 说明文本；无说明返回空串
     */
    default String getDescription() {
        return "";
    }

    /**
     * 是否支持 {@code ?url=} 外部地址预览。
     *
     * <p>只有返回 {@code true} 的实现被选中时，{@code ?url=} 请求才会被放行；
     * 其余实现（含未选择的空值）一律 403。服务端拉取型实现返回 {@code true}，
     * 纯前端直连型实现返回 {@code false}——它拿不到本服务的票据与鉴权，
     * 走不通预览分发链路。</p>
     *
     * @return true 表示该实现能处理 {@code ?url=}
     */
    default boolean supportsUrlPreview() {
        return false;
    }
}
