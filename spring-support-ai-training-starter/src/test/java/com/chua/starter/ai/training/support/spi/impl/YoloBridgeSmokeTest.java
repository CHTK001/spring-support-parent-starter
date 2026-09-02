package com.chua.starter.ai.training.support.spi.impl;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import com.chua.deeplearning.support.dl4j.TrainListener;
import com.chua.deeplearning.support.dl4j.TrainProgress;
import com.chua.deeplearning.support.dl4j.TrainResult;
import com.chua.deeplearning.support.dl4j.TrainStatus;
import com.chua.starter.ai.training.support.spi.TrainContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * YOLO python 子进程桥冒烟测试（C 级）。
 *
 * <p>用 {@code python/yolo_mock_service.py}（纯标准库 mock，模拟 yolo_train_service.py
 * 的线协议：PORT= 协商 → /start → /status 轮询 → done）驱动真实 {@link YoloTrainProvider}，
 * 验证 Java↔python 全链路：子进程拉起、端口协商、HTTP 下发、轮询回收。</p>
 *
 * <p>不依赖 ultralytics / 数据集 / 联网，仅需本机有 python（无 python 时整类跳过）。
 * python 可执行经系统属性 {@code ai.training.yolo.python} 显式注入，避免 PATH 歧义；
 * mock 脚本自 classpath 落盘临时目录并经 {@code ai.training.yolo.script} 注入。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@EnabledIf(value = "pythonAvailable", disabledReason = "本机无 python，跳过进程桥冒烟测试")
class YoloBridgeSmokeTest {

    /** provider 读取的系统属性键（与实现内常量一致） */
    private static final String PROP_PYTHON = "ai.training.yolo.python";
    private static final String PROP_SCRIPT = "ai.training.yolo.script";
    private static final String CLASS_PATH_MOCK = "python/yolo_mock_service.py";

    private String savedPythonProp;
    private String savedScriptProp;

    /** 探测本机 python 可执行（供 @EnabledIf 条件判断）。 */
    static boolean pythonAvailable() {
        return resolvePython() != null;
    }

    @BeforeEach
    void setUp() throws IOException {
        savedPythonProp = System.getProperty(PROP_PYTHON);
        savedScriptProp = System.getProperty(PROP_SCRIPT);
    }

    @AfterEach
    void tearDown() {
        restore(PROP_PYTHON, savedPythonProp);
        restore(PROP_SCRIPT, savedScriptProp);
    }

    @Test
    @DisplayName("Java 拉起 python mock 服务并完成训练全链路")
    void trainAgainstMockPythonService() throws Exception {
        // 数据目录需含 data.yaml（provider 前置校验）
        Path dataDir = Files.createTempDirectory("yolo-smoke-data");
        Files.writeString(dataDir.resolve("data.yaml"),
                "path: " + dataDir + "\nnames:\n  0: cat\n  1: dog\n", StandardCharsets.UTF_8);

        // mock 脚本落盘临时文件
        Path script = extractMockScript();
        // python 显式注入（避免 PATH 解析差异）
        System.setProperty(PROP_PYTHON, resolvePython());
        System.setProperty(PROP_SCRIPT, script.toString());

        TrainArgument argument = TrainArgument.defaults(); // epoch=10, batchSize=2 ...
        TrainContext ctx = new TrainContext(
                "mock-pretrain.zip",      // modelPath
                dataDir.resolve("out").toString(), // savePath
                dataDir.toString());      // dataRootPath

        AtomicInteger started = new AtomicInteger(0);
        AtomicInteger epochCallbacks = new AtomicInteger(0);
        AtomicInteger finished = new AtomicInteger(0);
        AtomicReference<TrainProgress> lastProgress = new AtomicReference<>();

        YoloTrainProvider provider = new YoloTrainProvider();
        TrainResult result = provider.train(argument, ctx, new TrainListener() {
            @Override
            public void onStarted(TrainProgress p) {
                started.incrementAndGet();
                lastProgress.set(p);
            }

            @Override
            public void onEpochCompleted(TrainProgress p) {
                epochCallbacks.incrementAndGet();
                lastProgress.set(p);
            }

            @Override
            public void onFinished(TrainProgress p) {
                finished.incrementAndGet();
                lastProgress.set(p);
            }

            @Override
            public void onFailed(TrainProgress p, Throwable cause) {
                lastProgress.set(p);
            }
        });

        assertThat(result.isSuccess())
                .as("mock 服务应正常完成训练")
                .isTrue();
        assertThat(started.get()).as("应收到 onStarted").isEqualTo(1);
        assertThat(finished.get()).as("应收到 onFinished").isEqualTo(1);
        assertThat(epochCallbacks.get()).as("训练期间应收到进度回调").isGreaterThanOrEqualTo(1);
        assertThat(result.getModelPath())
                .as("结果应带回 best_model 路径")
                .isEqualTo("C:/mock/best.pt");
        assertThat(result.getBestScore()).isGreaterThan(0);
        // 终态进度应为 SUCCESS
        TrainProgress end = lastProgress.get();
        assertThat(end).isNotNull();
        assertThat(end.getStatus()).isEqualTo(TrainStatus.SUCCESS);
    }

    // ------------------------------------------------------------------ 辅助

    /** 从 classpath 提取 mock 脚本到临时文件。 */
    private Path extractMockScript() throws IOException {
        Path tmp = Files.createTempFile("yolo_mock_", ".py");
        try (InputStream in = YoloBridgeSmokeTest.class.getClassLoader().getResourceAsStream(CLASS_PATH_MOCK)) {
            if (in == null) {
                throw new IOException("classpath 中未找到 mock 脚本: " + CLASS_PATH_MOCK);
            }
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        tmp.toFile().deleteOnExit();
        return tmp;
    }

    /** 探测 python：依次尝试 python/python3，取首个可用的绝对路径。 */
    private static String resolvePython() {
        for (String candidate : new String[]{"python", "python3"}) {
            try {
                Process p = new ProcessBuilder(candidate, "-c",
                        "import sys; print(sys.executable)").start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0
                        && !out.isEmpty()) {
                    return out;
                }
            } catch (Exception ignored) {
                // 尝试下一个候选
            }
        }
        return null;
    }

    private void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
