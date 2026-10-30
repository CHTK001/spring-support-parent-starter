package com.chua.starter.ai.support.rpc;

import com.chua.common.support.network.rpc.RpcService;
import com.chua.deeplearning.support.model.DetectionInfo;
import com.chua.starter.ai.support.configuration.image.ImageRecognitionClientManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 图片 AI 能力（RPC 提供者）。
 *
 * <p>把 {@link com.chua.starter.ai.support.configuration.image.ImageRecognitionClientManager}
 * 封装成 RPC 可消费的 {@link AiImageRecognitionService}；模型与设置走 {@code image} 分组
 * 全局设置（配置变更经指纹比对自动重建），**不依赖登录态与账号上下文**，
 * 所以它可以在任意的服务端（Dubbo 消费方、定时任务）里直接调用。</p>
 *
 * <p>注册与发现规约：<br>
 * 1. 仅提供开启 {@code plugin.rpc.enable=true} 后才生效（默认关闭）；
 * 2. 扫描包路径 {@code plugin.rpc.scan}（默认扫描应用入口类所在包，包含 {@code com.chua.starter.ai.support.rpc}）；
 * 3. 消费者侧用 {@code @RpcResource(version = "1.0.0")} 注入，RPC 层（Dubbo）不可用时自动
 *    降級到本应用的本地 Bean（{@code degrade = "springbean"}）。</p>
 *
 * @see com.chua.common.support.network.rpc.RpcService
 * @see com.chua.common.support.network.rpc.RpcResource
 * @since 4.0.0.51
 * @Revision 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
@RpcService(interfaceClass = AiImageRecognitionService.class, version = "1.0.0")
public class AiImageRecognitionServiceImpl implements AiImageRecognitionService {

    /**
     * 图片识别客户端管理器（懒加载 + 热重建）
     */
    private final ImageRecognitionClientManager recognitionClientManager;

    /**
     * 图像分类 Top-K。
     *
     * @param imageData 图片字节
     * @param topK      返回类别数
     * @return 分类结果列表（未配置模型时返回 空）
     */
    @Override
    public List<DetectionInfo> classify(byte[] imageData, int topK) {
        return recognitionClientManager.classifyTopK(imageData, topK);
    }

    /**
     * 目标检测。
     *
     * @param imageData 图片字节
     * @return 检测结果列表（未配置模型时返回 空）
     */
    @Override
    public List<DetectionInfo> detect(byte[] imageData) {
        return recognitionClientManager.detect(imageData);
    }

    /**
     * 图像描述。
     *
     * @param imageData 图片字节
     * @return 描述文本（未配置模型时返回 空）
     */
    @Override
    public String caption(byte[] imageData) {
        return recognitionClientManager.caption(imageData);
    }

    /**
     * 图像超分修复。
     *
     * @param imageData 图片字节
     * @return 超分后图片字节（未配置模型时返回 空）
     */
    @Override
    public byte[] enhance(byte[] imageData) {
        return recognitionClientManager.superResolution(imageData);
    }

    /**
     * 模型清单。
     *
     * @return 能力分组 → 模型标识列表
     */
    @Override
    public Map<String, List<String>> listModels() {
        return recognitionClientManager.listModels();
    }
}
