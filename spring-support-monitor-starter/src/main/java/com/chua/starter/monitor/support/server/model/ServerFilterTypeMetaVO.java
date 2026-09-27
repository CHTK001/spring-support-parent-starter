package com.chua.starter.monitor.support.server.model;

import java.util.List;
import java.util.Objects;

/**
 * ServerFilter 插件元数据 —— 前端插件下拉与参数表单的数据源。
 *
 * @param type        插件类型：预设别名 file-storage / SPI 名称 / FQCN
 * @param desc        插件描述
 * @param params      参数 schema
 * @param require     使用前提说明，无则不下发
 * @param group       分组标识：storage / discovery / routing / custom / other
 * @param detailPage  深度查看页路由，仅 {@code detailKind=route} 时有值
 * @param detailKind  深度查看目标类型：{@code node} 直开节点地址，{@code route} 跳宿主路由，空表示无入口
 * @param detailLabel 深度查看按钮文案
 * @param spi         是否来自 ServerFilter SPI 动态发现
 * @author CH
 * @since 4.0.0.42
 */
public record ServerFilterTypeMetaVO(
        String type,
        String desc,
        List<ServerPluginParamVO> params,
        String require,
        String group,
        String detailPage,
        String detailKind,
        String detailLabel,
        boolean spi
) {

    /**
     * 规范构造器：参数 schema 做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。本记录是响应体而非请求体，
     * 全部由 {@code ServerManageServiceImpl#filterTypes()} 经
     * {@code fileStorageParams()}、{@code serviceDiscoveryParams()}、
     * {@code paramsOf(definition)} 与 {@link List#of()} 组装后传入，
     * 恒不为 null 且元素恒不为 null，故可使用拒绝 null 的 {@link List#copyOf}。</p>
     *
     * @param params 参数 schema
     */
    public ServerFilterTypeMetaVO {
        params = List.copyOf(Objects.requireNonNull(params, "params 不能为 null"));
    }
}
