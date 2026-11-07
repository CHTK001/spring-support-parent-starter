package com.chua.starter.collect.service;

import com.chua.starter.collect.dto.CollectDslDTO;
import com.chua.starter.collect.engine.spi.CollectShapeMeta;
import com.chua.starter.collect.vo.CollectPreviewVO;
import com.chua.starter.collect.vo.CollectRuntimeVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设计器与运行时概览服务。
 *
 * <p>设计器的工具箱、属性面板全部由 {@link CollectShapeMeta} 渲染，第三方以
 * {@code @Spi} 追加节点类型后无需改前端。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectDesignerService {

    /**
     * 全部节点类型元数据。
     *
     * @return 元数据列表
     */
    List<CollectShapeMeta> metas();

    /**
     * 运行时概览：开关、并发规模、计量与节点类型。
     *
     * @return 概览出参
     */
    CollectRuntimeVO runtime();

    /**
     * 预览定时表达式接下来几次的触发时间。
     *
     * @param cron  定时表达式，六位 Quartz 风格
     * @param times 预览次数，最多 10 次
     * @return 触发时间列表
     */
    List<LocalDateTime> cronPreview(String cron, int times);

    /**
     * 设计期预览抓取：把 URL 抓回来供元素拾取器渲染与点选。
     *
     * @param url       目标地址，仅允许 http/https，且拒绝内网地址
     * @param timeoutMs 超时毫秒；空或非正数时用采集配置的默认请求超时
     * @return 抓取结果（状态码、Content-Type、正文原文）
     */
    CollectPreviewVO previewFetch(String url, Integer timeoutMs);

    /**
     * 设计期预览抓取（可指定是否用真实浏览器渲染）。
     *
     * <p>{@code render=false} 走 HTTP 直取，快、省资源，但拿到的是**未经 JS 执行**的原始
     * 响应——SPA 页面里通常一个业务元素都没有。{@code render=true} 用内置 CDP 引擎开一个
     * 真实 Chromium，等文档加载完成后再取 DOM，于是点选器能看到用户真正看到的页面。</p>
     *
     * <p>渲染模式要启动浏览器进程，单次开销在秒级，因此默认关闭；由前端按需勾选。</p>
     *
     * @param url       目标地址，仅允许 http/https，且拒绝内网地址
     * @param timeoutMs 超时毫秒；空或非正数时用采集配置的默认请求超时
     * @param render    是否用真实浏览器渲染后取 DOM
     * @return 抓取结果（状态码、Content-Type、正文、是否渲染）
     */
    CollectPreviewVO previewFetch(String url, Integer timeoutMs, boolean render);

    /**
     * 用自然语言描述生成流程 DSL（AI 生成）。
     *
     * <p>调用同机 ai-starter 生成，并走与保存相同的图校验（{@code normalizeDsl}）后才返回；
     * 校验不过直接抛 {@link IllegalArgumentException}（原因给用户看）。</p>
     *
     * @param input 用户需求描述
     * @return 规范化后的流程 DSL
     */
    CollectDslDTO aiGenerate(String input);
}
