package com.chua.starter.ai.support.model;

/**
 * 输入优化请求模型。
 *
 * @since 4.0.0.42
 * @author CH
 */
public record InputOptimizeRequest(
        /**
         * 待优化的原始输入
         */
        String input,
        /**
         * 指定优化模型（可选，空=使用 输入 分组 优化器_模型，仍为空则用全局默认对话模型）
         */
        String model,
        /**
         * 指定优化系统提示词（可选，空=使用 输入 分组 优化器_提示符，仍为空则用内置默认提示词）
         */
        String systemPrompt,
        /**
         * 使用场景（可选，如 image / video）。
         *
         * <p>只影响**内置默认提示词**的选取：图像、视频这类场景需要各自的提示词结构，
         * 用通用「改写得更清晰」的指令会把提示词写成散文，抽不出画面要素。
         * 显式传入 {@link #systemPrompt} 或全局配置了 {@code optimizer_prompt} 时，本字段被忽略。</p>
         */
        String scene
) {
}
