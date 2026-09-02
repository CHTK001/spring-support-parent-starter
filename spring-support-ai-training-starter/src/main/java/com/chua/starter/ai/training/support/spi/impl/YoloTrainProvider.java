package com.chua.starter.ai.training.support.spi.impl;

import com.chua.deeplearning.support.dl4j.TrainArgument;
import com.chua.deeplearning.support.dl4j.TrainListener;
import com.chua.deeplearning.support.dl4j.TrainProgress;
import com.chua.deeplearning.support.dl4j.TrainResult;
import com.chua.deeplearning.support.dl4j.TrainStatus;
import com.chua.starter.ai.training.support.spi.TrainContext;
import com.chua.starter.ai.training.support.spi.TrainProvider;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * YOLO 训练引擎（type=yolo, engine=python-http）。
 *
 * <p>通过 Java 拉起本地 python 子进程，运行随模块打包的
 * {@code python/yolo_train_service.py}（Ultralytics YOLO 一次性 HTTP 训练服务），
 * Java 侧经 HTTP 下发超参并轮询进度，训练完成/失败后回收子进程。</p>
 *
 * <p>HTTP 协议（见 python 脚本）：</p>
 * <ul>
 *   <li>{@code POST /start}：{data, model, epochs, imgsz, device, project, name} → 起训练线程，返回 task=1</li>
 *   <li>{@code GET  /status}：{status: starting|running|done|failed, current_epoch, total_epochs, best_map, message, best_model}</li>
 * </ul>
 *
 * <p>python 可执行默认 {@code python}（win）/ {@code python3}，可经系统属性
 * {@code ai.training.yolo.python} 覆盖；脚本默认取自 classpath，可经
 * {@code ai.training.yolo.script} 指定外部路径。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class YoloTrainProvider implements TrainProvider {

    private static final String CLASS_PATH_SCRIPT = "python/yolo_train_service.py";
    private static final long TRAIN_TIMEOUT_MIN = 6 * 60;
    private static final long START_TIMEOUT_MS = 60_000;

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
        int total = argument.getEpoch() != null ? argument.getEpoch() : 10;
        TrainProgress progress = new TrainProgress()
                .setStatus(TrainStatus.RUNNING)
                .setTotalEpochs(total);
        if (listener != null) {
            listener.onStarted(progress);
        }

        // 0) 前置：YOLO 数据目录需含 data.yaml
        Path dataYaml = Path.of(ctx.dataRootPath()).resolve("data.yaml");
        if (!Files.isRegularFile(dataYaml)) {
            throw new IllegalArgumentException("YOLO 训练需要数据目录含 data.yaml: " + dataYaml);
        }
        // 数据集目录（含 data.yaml）的父目录作为 --data 指向 data.yaml 所在目录本身
        Path datasetDir = Path.of(ctx.dataRootPath()).toAbsolutePath();

        // 1) 解析 python 与脚本
        String pythonCmd = resolvePython();
        Path script = resolveScript();

        // 2) 拉起 python 子进程（进程内起 HTTP 训练服务）
        Process process = spawnServer(pythonCmd, script);
        int port;
        try {
            port = waitForReady(process);
        } catch (Exception e) {
            destroy(process);
            throw e;
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            startTraining(client, port, argument, datasetDir, ctx);
            return pollUntilDone(client, port, total, ctx, progress, listener, startMs);
        } finally {
            destroy(process);
        }
    }

    /**
     * 解析 python 可执行（系统属性优先，默认 win=python / 其它=python3）。
     */
    private String resolvePython() {
        String cfg = System.getProperty("ai.training.yolo.python");
        if (cfg != null && !cfg.isBlank()) {
            return cfg;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? "python" : "python3";
    }

    /**
     * 解析训练脚本：优先外部路径，否则从 classpath 资源落盘到临时目录。
     */
    private Path resolveScript() throws IOException {
        String external = System.getProperty("ai.training.yolo.script");
        if (external != null && !external.isBlank() && Files.isRegularFile(Path.of(external))) {
            return Path.of(external);
        }
        Path tmp = Files.createTempFile("yolo_train_service_", ".py");
        try (InputStream in = YoloTrainProvider.class.getClassLoader().getResourceAsStream(CLASS_PATH_SCRIPT)) {
            if (in == null) {
                throw new IOException("classpath 中未找到脚本资源: " + CLASS_PATH_SCRIPT);
            }
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        tmp.toFile().deleteOnExit();
        return tmp;
    }

    private Process spawnServer(String pythonCmd, Path script) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(pythonCmd, script.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        log.info("[YOLO] 启动 python 训练服务: {} {} (type=yolo, engine=python-http)",
                pythonCmd, script);
        return pb.start();
    }

    /**
     * 读取 python stdout 首行 PORT=xxx 完成端口协商。
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
                    throw new IllegalStateException("python 服务启动失败: " + line);
                }
            }
        }
        throw new IllegalStateException("python 训练服务未在 " + START_TIMEOUT_MS + "ms 内就绪");
    }

    private void startTraining(HttpClient client, int port, TrainArgument arg,
                               Path datasetDir, TrainContext ctx) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("data", datasetDir.toString());
        body.put("model", "yolo11n.pt");
        body.put("epochs", arg.getEpoch() != null ? arg.getEpoch() : 10);
        body.put("imgsz", 640);
        body.put("device", "cpu");
        body.put("project", ctx.savePath());
        body.put("name", "train");
        String json = toJson(body);

        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/start"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("python 训练启动失败: HTTP " + resp.statusCode() + " " + resp.body());
        }
        log.info("[YOLO] 训练请求已下发: {}", json);
    }

    private TrainResult pollUntilDone(HttpClient client, int port, int total, TrainContext ctx,
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
                Thread.sleep(1500);
                continue;
            }
            if (resp.statusCode() != 200) {
                Thread.sleep(1500);
                continue;
            }

            Map<String, String> s = parseStatus(resp.body());
            String status = s.getOrDefault("status", "running");
            progress.setCurrentEpoch(parseInt(s.get("current_epoch"), 0));
            double map = parseDouble(s.get("best_map"), 0.0);
            if (map > progress.getBestScore()) {
                progress.setBestScore(map);
            }
            progress.setStage("YOLO 训练中 - epoch " + progress.getCurrentEpoch() + "/" + total);
            if (total > 0) {
                progress.setProgress(100.0 * Math.min(progress.getCurrentEpoch(), total) / total);
            }

            if ("done".equals(status)) {
                String bestModel = s.getOrDefault("best_model", ctx.savePath());
                progress.setStatus(TrainStatus.SUCCESS).setStage("训练完成").setProgress(100);
                if (listener != null) {
                    listener.onFinished(progress);
                }
                long elapsed = System.currentTimeMillis() - startMs;
                log.info("[YOLO] 训练完成: model={}, map={}, 耗时={}ms", bestModel, progress.getBestScore(), elapsed);
                return new TrainResult()
                        .setSuccess(true)
                        .setModelPath(bestModel)
                        .setBestScore(progress.getBestScore())
                        .setElapsedMs(elapsed);
            }
            if ("failed".equals(status)) {
                String msg = s.getOrDefault("message", "python 训练失败");
                progress.setStatus(TrainStatus.FAILED);
                if (listener != null) {
                    listener.onFailed(progress, new RuntimeException(msg));
                }
                return new TrainResult().setSuccess(false).setErrorMessage(msg);
            }
            // starting/running → 轮询（当前脚本训练中不逐 epoch 回调，进度=best_map 方向）
            if (listener != null) {
                listener.onEpochCompleted(progress);
            }
            Thread.sleep(2000);
        }
        return new TrainResult().setSuccess(false).setErrorMessage("训练超时(>=" + TRAIN_TIMEOUT_MIN + "min)");
    }

    private Map<String, String> parseStatus(String body) {
        Map<String, String> m = new HashMap<>();
        String inner = body;
        int s = body.indexOf('{');
        int e = body.lastIndexOf('}');
        if (s >= 0 && e > s) {
            inner = body.substring(s + 1, e);
        }
        for (String pair : inner.split(",")) {
            int colon = pair.indexOf(':');
            if (colon > 0) {
                String k = pair.substring(0, colon).replace("\"", "").trim();
                String v = pair.substring(colon + 1).replace("\"", "").trim();
                m.put(k, v);
            }
        }
        return m;
    }

    private String toJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> en : m.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(en.getKey()).append("\":");
            Object v = en.getValue();
            if (v instanceof Number) {
                sb.append(v);
            } else {
                sb.append('"').append(String.valueOf(v).replace("\\", "\\\\")
                        .replace("\"", "\\\"")).append('"');
            }
        }
        return sb.append('}').toString();
    }

    private int parseInt(String v, int def) {
        try {
            return (int) Double.parseDouble(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private double parseDouble(String v, double def) {
        try {
            return Double.parseDouble(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private void destroy(Process process) {
        if (process != null && process.isAlive()) {
            process.destroy();
            log.info("[YOLO] python 训练服务已回收");
        }
    }
}
