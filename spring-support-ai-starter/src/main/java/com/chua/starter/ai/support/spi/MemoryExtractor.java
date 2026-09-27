package com.chua.starter.ai.support.spi;

import com.chua.starter.ai.support.model.MemoryCandidateDTO;
import com.chua.starter.ai.support.model.MemoryExtractionContext;

import java.util.List;

/**
 * 记忆抽取 SPI —— 记忆体「怎么记录」的扩展点。
 *
 * <p>一轮对话正常结束后，后台按注册顺序调用全部实现，把用户输入与模型回复转成候选记忆，
 * 由 {@code SysAiMemoryStore} 去重落库。每个实现负责一种记录口径：</p>
 * <ul>
 *   <li>规则抽取：识别「记住…」「以后都…」等显式指令与自我介绍，零外部依赖、默认开启；</li>
 *   <li>模型抽取：让模型自己总结值得长期记住的内容，需可用模型，按设置开关；</li>
 *   <li>第三方实现：以 Spring Bean 形式注册即生效（构造注入收集），无需改动本模块代码。</li>
 * </ul>
 *
 * <p>实现必须自证安全：单个实现抛异常只影响该路候选，不得中断对话主流程；
 * 不得返回与本轮对话无关的内容，避免污染记忆库。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface MemoryExtractor {

    /**
     * 抽取器名称（用于设置页开关与来源标记）。
     *
     * @return 名称
     */
    String name();

    /**
     * 是否参与本轮抽取。
     *
     * <p>实现应在此读取自己的开关，关闭时不产生任何模型调用或磁盘扫描。</p>
     *
     * @param context 抽取上下文
     * @return true 表示执行抽取
     */
    default boolean supports(MemoryExtractionContext context) {
        return true;
    }

    /**
     * 从一轮对话中抽取候选记忆。
     *
     * @param context 抽取上下文（用户输入、模型回复、归属用户与会话）
     * @return 候选记忆列表，无可沉淀内容时返回空列表
     */
    List<MemoryCandidateDTO> extract(MemoryExtractionContext context);
}
