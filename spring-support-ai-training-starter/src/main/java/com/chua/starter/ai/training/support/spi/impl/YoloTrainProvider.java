package com.chua.starter.ai.training.support.spi.impl;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import com.chua.deeplearning.support.dl4j.TrainListener;
import com.chua.deeplearning.support.dl4j.TrainProgress;
import com.chua.deeplearning.support.dl4j.TrainResult;
import com.chua.deeplearning.support.dl4j.TrainStatus;
import com.chua.starter.ai.training.support.spi.TrainContext;
import com.chua.starter.ai.training.support.spi.TrainProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * YOLO 训练引擎（类型=yolo, engine=Python-http）。
 *
 * <p>通过 Java 拉起本地 python 子进程，运行随模块打包的
 * {@code python/yolo_train_service.py}（Ultralytics YOLO 一次性 HTTP 训练服务），
 * Java 侧经 HTTP 下发超参并轮询进度，训练完成/失败后回收子进程。</p>
 *
 * <p>HTTP 协议（见 python 脚本）：</p>
 * <ul>
 *   <li>{@code POST /start}：{data, model, epochs, imgsz, device, project, name} → 起训练线程，返回 task=1；
 * 数据 为 Java 侧定位到的 <b>数据.yaml 文件路径</b>（自动容忍 数据集/ 等子目录布局）</li>
 *   <li>{@code GET  /status}：{status: starting|running|done|failed, current_epoch, total_epochs, best_map, message, best_model}</li>
 * </ul>
 *
 * <p>python 可执行默认 {@code python}（win）/ {@code python3}，可经系统属性
 * {@code plugin.ai.training.yolo.python} 覆盖；脚本默认取自 classpath，可经
 * {@code plugin.ai.training.yolo.script} 指定外部路径。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class YoloTrainProvider implements TrainProvider {

    private static final String CLASS_PATH_SCRIPT = "python/yolo_train_service.py"; // 类路径script
    private static final long TRAIN_TIMEOUT_MIN = 6 * 60; // train超时最小
    private static final long START_TIMEOUT_MS = 60_000; // 启动超时ms

    /**
     * 数据.yaml 递归定位的最大目录深度（容忍 数据集/ 等一层子目录打包，
     * 也允许再深一层；Ultralytics 官方导出一般不深于此）。
     */
    static final int MAX_DATA_YAML_DEPTH = 3;

    /**
     * JSON 编解码器（协议体 /启动 与 /状态 的字段值可能含逗号、冒号等特殊字符，
     * 统一走标准 JSON 解析，避免手工 分割 解析错位）。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String type() {
        return "yolo";
    }

    @Override
    public String engine() {
        return "python-http";
    }

    @Override
    public TrainResult train(TrainArgument argument, TrainContext ctx, TrainListener listener) throws Exception {
        long startMs = System.currentTimeMillis();

 // 0) 前置（引擎回调前）：定位 数据.yaml。Ultralytics 数据集布局允许 数据.yaml
 // 嵌套于 数据集/ 等子目录，故递归向下定位；找不到给出含实际内容的可操作错误。
        Path dataYaml = locateDataYaml(Path.of(ctx.dataRootPath()));
        if (dataYaml == null) {
            /**
             * IllegalArgumentException。
             * @param buildMissingDataYamlMessage(Path.of(ctx.dataRootPath( 构建missing数据yamlmessage(路径.的(ctx.数据根路径(
             * @return IllegalArgumentException的结果
             */
            throw new IllegalArgumentException(buildMissingDataYamlMessage(Path.of(ctx.dataRootPath())));
        }
        Path datasetDir = dataYaml.getParent();

        int total = argument.getEpoch() != null ? argument.getEpoch() : 10;
        TrainProgress progress = new TrainProgress()
                .setStatus(TrainStatus.RUNNING)
                .setTotalEpochs(total);
        if (listener != null) {
            listener.onStarted(progress);
        }

        // 1) 解析 python 与脚本
        String pythonCmd = resolvePython();
        Path script = resolveScript();

        // 2) 拉起 python 子进程（进程内起 HTTP 训练服务）
        Process process = spawnServer(pythonCmd, script);
        int port;
        try {
            port = waitForReady(process);
        } catch (Exception e) {
            /**
             * 销毁。
             * @param process 处理
             */
            destroy(process);
            throw e;
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            /**
             * 启动培训假。
             * @param client 客户端
             * @param port 端口
             * @param argument 参数
             * @param dataYaml 数据yaml
             * @param datasetDir 数据集dir
             * @param ctx ctx
             */
            startTraining(client, port, argument, dataYaml, datasetDir, ctx);
            return pollUntilDone(client, port, process, total, ctx, progress, listener, startMs);
        } finally {
            /**
             * 销毁。
             * @param process 处理
             */
            destroy(process);
        }
    }

    /**
     * 递归定位数据根目录下的 数据.yaml（Ultralytics 布局）。
     *
     * <p>容忍常见打包差异：data.yaml 可位于根目录，也可嵌套在 dataset/ 等子目录
     * （深度上限 {@link #MAX_DATA_YAML_DEPTH}）。多个候选时取目录层级最浅的一个
     * （通常即数据集真正入口）；未找到返回 {@code null}。</p>
     *
     * @param dataRoot 解压后的数据根目录
     * @return data.yaml 绝对路径；未找到返回 空
     * @throws IOException 目录不可读时抛出
     */
    static Path locateDataYaml(Path dataRoot) throws IOException {
        List<Path> found;
        /**
         * 尝试。
         * @param MAX_DATA_YAML_DEPTH) 最大数据yaml深度
         */
        try (Stream<Path> stream = Files.walk(dataRoot, MAX_DATA_YAML_DEPTH)) {
            found = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("data.yaml"))
                    .map(Path::toAbsolutePath)
                    .sorted(Comparator.comparingInt(Path::getNameCount))
                    .collect(Collectors.toList());
        }
        return found.isEmpty() ? null : found.getFirst();
    }

    /**
     * 构造"未找到 数据.yaml"的可操作错误信息：附数据目录顶层实际内容，便于使用者定位打包问题。
     * @param dataRoot 数据根
     * @return 构建missing数据yaml消息的结果
     */
    private static String buildMissingDataYamlMessage(Path dataRoot) {
        String listing;
        try {
            listing = listTopLevel(dataRoot);
        } catch (IOException e) {
            listing = "（无法列出目录内容: " + e.getMessage() + "）";
        }
        return "YOLO 训练需要 Ultralytics 布局数据集（images/ + labels/ + data.yaml）。数据目录 "
                + dataRoot.toAbsolutePath() + " 下（含子目录，深度 ≤" + MAX_DATA_YAML_DEPTH + "）未找到 data.yaml；"
                + "实际顶层内容: [" + listing + "]。请确认上传的 ZIP 内含 data.yaml（可位于 dataset/ 等子目录）。";
    }

    /**
     * 列出数据目录顶层条目（目录带 / 后缀），供错误提示使用。
     * @param dataRoot 数据根
     * @return 列表top水平仪的结果
     */
    private static String listTopLevel(Path dataRoot) throws IOException {
        /**
         * 尝试。
         * @param Files.list(dataRoot 文件.列表(数据根
         */
        try (Stream<Path> stream = Files.list(dataRoot)) {
            return stream
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(p -> p.getFileName().toString() + (Files.isDirectory(p) ? "/" : ""))
                    .collect(Collectors.joining(", "));
        }
    }

    /**
     * 解析 Python 可执行（系统属性优先，默认 win=Python / 其它=Python3）。
     * @return resolvePython的结果
     */
    private String resolvePython() {
        String cfg = System.getProperty("plugin.ai.training.yolo.python");
        if (cfg != null && !cfg.isBlank()) {
            return cfg;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? "python" : "python3";
    }

    /**
     * 解析训练脚本：优先外部路径，否则从 类路径 资源落盘到临时目录。
     * @return resolveScript的结果
     */
    private Path resolveScript() throws IOException {
        String external = System.getProperty("plugin.ai.training.yolo.script");
        if (external != null && !external.isBlank() && Files.isRegularFile(Path.of(external))) {
            return Path.of(external);
        }
        Path tmp = Files.createTempFile("yolo_train_service_", ".py");
        /**
         * 尝试。
         * @param YoloTrainProvider.class.getClassLoader( yolotrainprovider.类.获取类加载(
         */
        try (InputStream in = YoloTrainProvider.class.getClassLoader().getResourceAsStream(CLASS_PATH_SCRIPT)) {
            if (in == null) {
                /**
                 * io异常。
                 * @param CLASS_PATH_SCRIPT 类路径script
                 * @return io异常的结果
                 */
                throw new IOException("classpath 中未找到脚本资源: " + CLASS_PATH_SCRIPT);
            }
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        tmp.toFile().deleteOnExit();
        return tmp;
    }

    /**
     * spawn服务端。
     * @param pythonCmd pythoncmd
     * @param script script
     * @return spawn服务端的结果
     */
    private Process spawnServer(String pythonCmd, Path script) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(pythonCmd, script.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        log.info("[YOLO] 启动 python 训练服务: {} {} (type=yolo, engine=python-http)",
                pythonCmd, script);
        return pb.start();
    }

    /**
     * 读取 Python stdout 首行 端口=xxx 完成端口协商。
     * @param process 处理
     * @return waitfor就绪的结果
     */
    private int waitForReady(Process process) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            long deadline = System.currentTimeMillis() + START_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline && process.isAlive()) {
                if (!reader.ready()) {
                    Thread.sleep(100);
                    continue;
                }
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.startsWith("PORT=")) {
                    return Integer.parseInt(line.substring(5).trim());
                }
                if (line.startsWith("Traceback") || line.startsWith("ERROR") || line.startsWith("ModuleNotFoundError")) {
                    /**
                     * illegal状态异常。
                     * @param line 线
                     * @return illegal状态异常的结果
                     */
                    throw new IllegalStateException("python 服务启动失败: " + line);
                }
            }
        }
        /**
         * illegal状态异常。
         * @param 内就绪" 内就绪"
         * @return illegal状态异常的结果
         */
        throw new IllegalStateException("python 训练服务未在 " + START_TIMEOUT_MS + "ms 内就绪");
    }

    private void startTraining(HttpClient client, int port, TrainArgument arg,
                               Path dataYaml, Path datasetDir, TrainContext ctx) throws Exception {
        Map<String, Object> body = new HashMap<>();
 // 数据 传 数据.yaml 文件路径（ultralytics 原生支持，yaml 内相对路径基于其所在目录解析）
        body.put("data", dataYaml.toString());
        body.put("model", "yolo11n.pt");
        body.put("epochs", arg.getEpoch() != null ? arg.getEpoch() : 10);
        body.put("imgsz", 640);
        body.put("device", "cpu");
        body.put("project", ctx.savePath());
        body.put("name", "train");
        String json = OBJECT_MAPPER.writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/start"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            /**
             * illegal状态异常。
             * @param resp.statusCode( resp.状态编码(
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("python 训练启动失败: HTTP " + resp.statusCode() + " " + resp.body());
        }
        log.info("[YOLO] 训练请求已下发: dataYaml={}, dataset={}", dataYaml, datasetDir);
    }

    private TrainResult pollUntilDone(HttpClient client, int port, Process process, int total, TrainContext ctx,
                                      TrainProgress progress, TrainListener listener, long startMs)
            throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(TRAIN_TIMEOUT_MIN);

        while (System.currentTimeMillis() < deadline) {
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/status"))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> resp;
            try {
                resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
 // 子进程已退出（崩溃/被杀）时再轮询只会空转到超时——立即判定失败，不必空等 TRAIN_超时_最小
                if (!process.isAlive()) {
                    String msg = "python 训练服务已退出（进程结束），训练中断";
                    log.error("[YOLO] {}", msg);
                    progress.setStatus(TrainStatus.FAILED).setStage("训练失败");
                    if (listener != null) {
                        listener.onFailed(progress, new IllegalStateException(msg));
                    }
                    /**
                     * train结果。
                     * @return train结果的结果
                     */
                    return new TrainResult().setSuccess(false).setErrorMessage(msg);
                }
                Thread.sleep(1500);
                continue;
            }
            if (resp.statusCode() != 200) {
                Thread.sleep(1500);
                continue;
            }

            JsonNode statusNode = parseStatus(resp.body());
            String status = statusNode.path("status").asText("running");
            progress.setCurrentEpoch(statusNode.path("current_epoch").asInt(0));
            double map = statusNode.path("best_map").asDouble(0.0);
            if (map > progress.getBestScore()) {
                progress.setBestScore(map);
            }
            progress.setStage("YOLO 训练中 - epoch " + progress.getCurrentEpoch() + "/" + total);
            if (total > 0) {
                progress.setProgress(100.0 * Math.min(progress.getCurrentEpoch(), total) / total);
            }

            if ("done".equals(status)) {
                String bestModel = statusNode.path("best_model").asText(ctx.savePath());
                progress.setStatus(TrainStatus.SUCCESS).setStage("训练完成").setProgress(100);
                if (listener != null) {
                    listener.onFinished(progress);
                }
                long elapsed = System.currentTimeMillis() - startMs;
                log.info("[YOLO] 训练完成: model={}, map={}, 耗时={}ms", bestModel, progress.getBestScore(), elapsed);
                /**
                 * train结果。
                 * @return train结果的结果
                 */
                return new TrainResult()
                        .setSuccess(true)
                        .setModelPath(bestModel)
                        .setBestScore(progress.getBestScore())
                        .setElapsedMs(elapsed);
            }
            if ("failed".equals(status)) {
                String msg = statusNode.path("message").asText("python 训练失败");
                progress.setStatus(TrainStatus.FAILED);
                if (listener != null) {
                    listener.onFailed(progress, new RuntimeException(msg));
                }
                /**
                 * train结果。
                 * @return train结果的结果
                 */
                return new TrainResult().setSuccess(false).setErrorMessage(msg);
            }
 // 启动/running → 轮询（当前脚本训练中不逐 轮次 回调，进度=best_映射 方向）
            if (listener != null) {
                listener.onEpochCompleted(progress);
            }
            Thread.sleep(2000);
        }
        /**
         * train结果。
         * @return train结果的结果
         */
        return new TrainResult().setSuccess(false).setErrorMessage("训练超时(>=" + TRAIN_TIMEOUT_MIN + "min)");
    }

    /**
     * 解析 /状态 响应体为 JSON 树（标准解析，字段值含逗号/冒号/引号均不串位）。
     * @param body 主体
     * @return 解析状态的结果
     */
    private JsonNode parseStatus(String body) throws Exception {
        return OBJECT_MAPPER.readTree(body);
    }

    /**
     * 销毁。
     * @param process 处理
     */
    private void destroy(Process process) {
        if (process != null && process.isAlive()) {
            process.destroy();
            log.info("[YOLO] python 训练服务已回收");
        }
    }
}
