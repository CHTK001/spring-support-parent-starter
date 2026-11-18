package com.chua.starter.account.support;

import com.chua.starter.datasearch.provider.model.ProviderDescriptor;
import com.chua.starter.datasearch.provider.support.ProviderRegistries;

import java.util.Map;

/**
 * 渠道官网解析：把渠道标识翻成登记表里的官网 / 官方文档。
 *
 * <p>渠道管理页右栏页头的「官网」入口读这两个字段。官网是「213 家供应商模板」这一层
 * 的公共数据，由 datasearch 的供应商登记表统一维护（显式 {@code website} 优先，
 * 其次取上游地址 / 官方文档站的可注册主域），<b>不是</b>渠道行上的列 ——
 * 账号模块不新增字段、也不在前端再维护一份 id → 网址的映射。</p>
 *
 * <p>登记表未收录（本机 ONNX、路由组、自建渠道、网关自有渠道）时回 {@code null}，
 * 前端据此隐藏入口：不拿上游 API 地址冒充官网，{@code https://api.xxx.com} 打开是 404。</p>
 *
 * <p>四个渠道类元数据接口（对话 / 文生图 / 文生视频 / 图生 3D）与 AI 账号渠道共用本类，
 * 口径只有一份，避免有的渠道卡片有官网、有的没有。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public final class ChannelSiteLinks {

    /**
     * 工具类，禁止实例化。
     */
    private ChannelSiteLinks() {
    }

    /**
     * 渠道官网。
     *
     * @param channel 渠道标识（与登记表 id / 别名同源）
     * @return 官网地址；登记表未收录返回 null
     */
    public static String websiteOf(String channel) {
        ProviderDescriptor descriptor = ProviderRegistries.find(channel);
        return descriptor == null ? null : descriptor.getWebsite();
    }

    /**
     * 渠道官方文档。
     *
     * @param channel 渠道标识
     * @return 官方文档入口；登记表未收录返回 null
     */
    public static String docOf(String channel) {
        ProviderDescriptor descriptor = ProviderRegistries.find(channel);
        return descriptor == null ? null : descriptor.getDoc();
    }

    /**
     * 把官网 / 文档写进接口返回项。
     *
     * <p>键名固定 {@code website} / {@code doc}；查不到时写 {@code null} 而不是省略，
     * 前端两种写法都能处理，但显式 null 让响应形状稳定（调试时能区分「没这个能力」
     * 与「后端漏了字段」）。</p>
     *
     * @param item    接口返回项
     * @param channel 渠道标识
     */
    public static void put(Map<String, Object> item, String channel) {
        ProviderDescriptor descriptor = ProviderRegistries.find(channel);
        item.put("website", descriptor == null ? null : descriptor.getWebsite());
        item.put("doc", descriptor == null ? null : descriptor.getDoc());
    }
}
