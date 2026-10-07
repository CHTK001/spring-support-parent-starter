package com.chua.starter.ai.support.configuration.client;

/**
 * 模型标识归一工具：把「设置页模型选择器值」收敛成裸模型名。
 *
 * <p>设置页保存的值形如 {@code onnx:macbert4csc}（{@code providerId:model}，
 * 用于同名模型消歧与前端回显），而 {@code ModelRegistry} 与底层推理实现
 * 只按裸名精确匹配——注册表标识来自文件系统路径（Windows 文件名不含冒号），
 * 带前缀直接下传会静默查空（懒加载返回 null，能力「配置了却不生效」）。
 *
 * <p>与 {@code RagClientManager#bareModelId}（embedding 路径既有先例）同语义；
 * OCR 路径（OcrClientManager / OcrTextExtractor）统一走本工具。
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class ModelIds {

    /**
     * 私有构造：工具类不可实例化。
     */
    private ModelIds() {
    }

    /**
     * 剥掉提供商前缀取裸模型名。
     *
     * @param modelRef 模型引用，允许为 {@code null} 或空串
     * @return 裸模型名；入参为 {@code null}/空串时原样返回（保留调用方的留空判断语义）
     */
    public static String bare(String modelRef) {
        if (modelRef == null || modelRef.isEmpty()) {
            return modelRef;
        }
        String ref = modelRef.trim();
        int colon = ref.indexOf(':');
        if (colon > 0 && colon < ref.length() - 1) {
            return ref.substring(colon + 1).trim();
        }
        return ref;
    }
}
