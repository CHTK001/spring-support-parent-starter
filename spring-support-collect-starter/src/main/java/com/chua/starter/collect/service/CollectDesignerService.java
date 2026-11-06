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
