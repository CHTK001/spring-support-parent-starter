package com.chua.starter.ai.support.rpc;

import com.chua.deeplearning.support.model.DetectionInfo;

import java.util.List;
import java.util.Map;

/**
 * 图片 AI 能力（过 RPC 暴露的“无状态 AI 计算”子集）。
 *
 * <p>模型与设置全部来自 {@code image} 分组的全局设置（{@code sys_ai_global_setting}），
 * 不依赖账号上下文，不在任何用户的画像/底库/会话里落盘，所以这类操作能脱离
 * 登录态（HTTP Session）运行——也因此是唯一适合走出本应用、经 RPC输送到其它服务端的
 * AI 能力。</p>
 *
 * <p>对应本地 HTTP 出口：{
 * @code {@code /v2/image-recognition/analyze}
 *     （分类 + 检测 + 描述）与 {
 * @code {@code /v2/image-recognition/enhance}
 *     （超分修复）}。</p>
 *
 * <h2>用法</h2>
 * <pre>{@code
 * // 消费者侧
 * public class OrderService {
 *
 *     @RpcResource(version = "1.0.0")
 *     private AiImageRecognitionService imageService;   // RPC 代理；Dubbo 不可用时降级到本地 Bean
 *
 *     public String caption(byte[] image) {
 *         return imageService.caption(image);
 *     }
 * }
 * }</pre>
 *
 * @author CH
 * @see com.chua.common.support.network.rpc.RpcService
 * @see com.chua.common.support.network.rpc.RpcResource
 * @since 4.0.0.51
 */
public interface AiImageRecognitionService {

    /**
     * 图像分类 Top-K。
     *
     * @param imageData 图片字节
     * @param topK      返回类别数
     * @return 分类结果列表（未配置模型时返回 空）
     */
    List<DetectionInfo> classify(byte[] imageData, int topK);

    /**
     * 目标检测。
     *
     * @param imageData 图片字节
     * @return 检测结果列表（未配置模型时返回 空）
     */
    List<DetectionInfo> detect(byte[] imageData);

    /**
     * 图像描述。
     *
     * @param imageData 图片字节
     * @return 描述文本（未配置模型时返回 空）
     */
    String caption(byte[] imageData);

    /**
     * 图像超分修复。
     *
     * @param imageData 图片字节
     * @return 超分后图片字节（未配置模型时返回 空）
     */
    byte[] enhance(byte[] imageData);

    /**
     * 模型清单（按能力分组）。
     *
     * @return 能力分组 → 模型标识列表
     */
    Map<String, List<String>> listModels();
}
