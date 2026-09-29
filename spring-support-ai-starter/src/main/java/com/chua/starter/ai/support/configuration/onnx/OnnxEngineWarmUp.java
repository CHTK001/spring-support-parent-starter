package com.chua.starter.ai.support.configuration.onnx;

import ai.djl.engine.Engine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ONNX Runtime 引擎预热。
 *
 * <p><b>为什么必须预热</b>：进程内存在两条互相独立的 ONNX Runtime 初始化路径，
 * 而 {@code ai.onnxruntime.OrtEnvironment} 是<b>全局单例</b>：</p>
 * <ul>
 *   <li>RAG 的 BGE 嵌入（{@code BgeEmbeddingTranslator}）走裸 ONNX Runtime，
 *       调用 {@code OrtEnvironment.getEnvironment()} <b>不指定线程池</b>；</li>
 *   <li>DJL 的 ONNX 引擎（{@code ai.djl.onnxruntime.engine.OrtEngine}，
 *       被图像特征、分类、检测等 Translator 使用）调用
 *       {@code OrtEnvironment.getEnvironment(threadPool)} <b>指定线程池</b>。</li>
 * </ul>
 *
 * <p>若「不指定线程池」的那条先执行，DJL 再初始化时会抛
 * {@code IllegalStateException: Tried to specify the thread pool when creating an
 * OrtEnvironment, but one already exists}，导致
 * {@code OrtEngineProvider$InstanceHolder} 类初始化失败。类初始化失败是<b>粘性的</b>：
 * 此后所有走 DJL 的模型调用都直接抛 {@code NoClassDefFoundError}，
 * 表现为「模型加载失败」，重启前无法恢复。</p>
 *
 * <p>反过来先初始化 DJL 则安全：{@code OrtEnvironment.getEnvironment()} 无参重载
 * 在实例已存在时<b>直接返回既有实例</b>，不会抛错。因此这里在容器启动阶段
 * 主动触发一次 DJL 引擎初始化，把顺序固定下来，消除该竞态。</p>
 *
 * <p>预热失败不阻断启动：引擎不可用时后续业务调用会给出各自的错误信息，
 * 由业务侧提示，避免把「ONNX 不可用」升级成「应用起不来」。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class OnnxEngineWarmUp {

    /** DJL 的 ONNX Runtime 引擎名 */
    private static final String ENGINE_NAME = "OnnxRuntime";

    /**
     * 启动阶段初始化 DJL 的 OnnxRuntime 引擎，固定 OrtEnvironment 的创建顺序。
     */
    @PostConstruct
    public void warmUp() {
        try {
            Engine engine = Engine.getEngine(ENGINE_NAME);
            log.info("[AiStarter] DJL ONNX 引擎预热完成: engine={}, version={}",
                    engine.getEngineName(), engine.getVersion());
        } catch (Throwable ex) {
            log.warn("[AiStarter] DJL ONNX 引擎预热失败（不阻断启动）: {}", ex.getMessage());
        }
    }
}