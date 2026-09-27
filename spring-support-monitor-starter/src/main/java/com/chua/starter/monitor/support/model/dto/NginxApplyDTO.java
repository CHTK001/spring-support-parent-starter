package com.chua.starter.monitor.support.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 保存 Nginx 配置请求（文件模式与 UI 模式共用）。
 *
 * <p>两种模式走同一个端点，但语义不同：</p>
 * <ul>
 *   <li>{@code mode=FILE}：{@link #content()} 是编辑器里的完整文本，直接落盘；</li>
 *   <li>{@code mode=MODEL}：{@link #patches()} 是改动列表，服务端在<strong>原始文本</strong>
 *       基础上重新解析并应用，只重渲染被改动的区块。</li>
 * </ul>
 *
 * <p>三个开关共同构成生产级安全网：{@code backup} 保存前自动备份、
 * {@code validate} 落盘后立即 {@code nginx -t}、{@code reload} 校验通过才重载。
 * 任一步失败都会在 {@code NginxApplyResultVO} 里如实报告，不会静默吞掉。</p>
 *
 * @param datasourceId 数据源 ID，可空
 * @param path         配置文件绝对路径
 * @param mode         保存模式：FILE / MODEL
 * @param content      完整文本，仅 FILE 模式使用
 * @param patches      改动列表，仅 MODEL 模式使用
 * @param backup       是否保存前自动备份
 * @param validate     是否落盘后自动语法检查
 * @param reload       是否校验通过后自动重载
 * @param dryRun       是否只预览不落盘。为 true 时渲染出结果并返回，不写文件、不备份、不重载，
 *                     用于 UI 模式在保存前确认「这份配置渲染出来长什么样」
 * @author CH
 * @since 4.0.0.42
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NginxApplyDTO(
        Long datasourceId,
        String path,
        String mode,
        String content,
        List<NginxPatchDTO> patches,
        Boolean backup,
        Boolean validate,
        Boolean reload,
        Boolean dryRun
) {

    /**
     * 规范构造器：对补丁列表做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。本记录由请求体反序列化而来，
     * FILE 模式下 {@code patches} 整体缺省为 null，且
     * {@code NginxConfigModelSupport#applyPatches} 显式跳过 null 元素
     * （{@code patches == null} 与 {@code p == null} 都有分支），
     * 因此保留 null 语义、不做非空断言，并采用可容纳 null 元素的不可变包装
     * 而非 {@link List#copyOf}。补丁元素自身不可变，无需深拷贝。</p>
     *
     * @param patches 改动列表，仅 MODEL 模式使用，可为 null
     */
    public NginxApplyDTO {
        patches = patches == null ? null : Collections.unmodifiableList(new ArrayList<>(patches));
    }
}
