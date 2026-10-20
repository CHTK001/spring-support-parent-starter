package com.chua.starter.ai.support.configuration.audio;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.starter.ai.support.chat.ModelClientResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 音频管线的「文字模型」后处理服务。
 *
 * <p>音频管线（ASR 输入侧 / TTS 输出侧）都会经过一次大模型文本加工，本类把这段
 * 共用逻辑收在一处，避免两个控制器各写一份：</p>
 * <ul>
 *   <li><b>ASR</b>：转写结果矫正（同音字、术语、标点）—— 与 OCR 的
 *       {@code corrector_model} 完全同构，只是那边用的是本地 macbert4csc 小模型，
 *       这边用通用大模型；</li>
 *   <li><b>TTS</b>：合成前文本规范化（数字、符号、缩写改写成适合朗读的表述）。</li>
 * </ul>
 *
 * <p><b>三条硬约定</b>（与 OCR corrector 保持一致，也是配置项描述里写明的）：</p>
 * <ol>
 *   <li><b>留空即关闭</b>：模型名没配就原样返回，不报错、不做任何加工；</li>
 *   <li><b>失败保留原文</b>：模型解析不到、调用抛异常、返回空白 —— 一律返回原文。
 *       音频管线的主产物是转写/合成结果，后处理只是增益，绝不能因为它把主产物弄丢；</li>
 *   <li><b>不伪造</b>：模型没配或调用失败时不编造"看起来矫正过"的文本。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioTextRefineService {

    /**
     * 模型 → 对话客户端解析器（按 DB 供应商动态构建，支持多密钥 failover）。
     */
    private final ModelClientResolver modelClientResolver;

    /**
     * 用配置的大模型对音频管线文本做一次后处理。
     *
     * @param text             待处理文本（ASR 输出或 TTS 输入）
     * @param qualifiedModel   模型标识，支持 {@code <providerId>:<model>}；留空即关闭后处理
     * @param systemInstruction 该场景的系统指令（矫正 / 规范化）
     * @return 处理后的文本；未配置或任何一步失败时返回原文
     */
    public String refine(String text, String qualifiedModel, String systemInstruction) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (qualifiedModel == null || qualifiedModel.isBlank()) {
            // 留空关闭：这是配置项描述里承诺的语义，不是异常路径
            return text;
        }
        try {
            ChatClient client = modelClientResolver.resolve(qualifiedModel);
            if (client == null) {
                log.warn("[AudioRefine] 未解析到模型客户端，保留原文 model={}", qualifiedModel);
                return text;
            }
            String model = ModelClientResolver.normalize(qualifiedModel);
            // 温度取 0：矫正/规范化要的是确定性，同一段音频重复转写不应给出不同文本
            String refined = client.model(model)
                    .system(systemInstruction)
                    .temperature(0.0)
                    .chatSync(text);
            if (refined == null || refined.isBlank()) {
                log.warn("[AudioRefine] 模型返回空结果，保留原文 model={}", model);
                return text;
            }
            return refined.trim();
        } catch (Exception e) {
            log.warn("[AudioRefine] 文本后处理失败，保留原文 model={}: {}", qualifiedModel, e.getMessage());
            return text;
        }
    }
}
