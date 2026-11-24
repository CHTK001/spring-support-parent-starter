package com.chua.starter.server.support.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Docker Engine HTTP API 客户端（API 通道）
 *
 * <p>与 {@code WorkbenchServiceImpl} 的 SSH + docker CLI（cmd 通道）并列的第二条操作通道。
 * 通过主机配置的 {@code server_host_docker_api_url}（如 {@code http://172.16.9.194:2375}）
 * 直连目标机 Docker daemon 的 TCP API，免 SSH 依赖。</p>
 *
 * <p><b>字段映射口径</b>：所有列表方法的返回结构与其 docker CLI
 * {@code --format '{{json .}}'} 输出同构（id/name/image/state/status/ports/...），
 * 前端工作台无需感知通道差异。</p>
 *
 * <p><b>日志流解包</b>：Docker API 的 {@code /containers/{id}/logs} 在容器未启用 TTY 时
 * 返回多路复用帧流（每帧 8 字节头：1 字节流类型 + 3 字节填充 + 4 字节大端长度），
 * 本类用 {@link DemuxInputStream} 还原为纯文本流；TTY 容器自动回落原始流。</p>
 *
 * <p>不支持交互式 exec（hijack + tty），该能力仍由 cmd 通道提供。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
public class DockerEngineClient {

    /**
     * 建立连接超时（秒）
     */
    private static final int CONNECT_TIMEOUT_SECONDS = 5;

    /**
     * 单次 API 请求超时（秒，不含日志流读取）
     */
    private static final int REQUEST_TIMEOUT_SECONDS = 30;

    /**
     * 多路复用帧头长度
     */
    private static final int DEMUX_HEADER_LENGTH = 8;

    /**
     * 单帧长度上限（16MB，防御性：超出视为非帧流）
     */
    private static final int DEMUX_MAX_FRAME = 16 * 1024 * 1024;

    /**
     * JSON 解析器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * HTTP 客户端（进程内共享）
     */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();

    /**
     * Docker Engine API 基础地址（如 http://172.16.9.194:2375）
     */
    private final String baseUrl;

    /**
     * 构造 API 客户端
     *
     * @param baseUrl Docker Engine API 基础地址（http://host:port）
     */
    public DockerEngineClient(String baseUrl) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.baseUrl = url;
    }

    /* ==================== 能力探测 ==================== */

    /**
     * 探测 Docker daemon 版本
     *
     * @return 版本号，如 27.3.1
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public String version() throws IOException, InterruptedException {
        JsonNode node = getJson("/version");
        return node.path("Version").asText("");
    }

    /* ==================== 容器 ==================== */

    /**
     * 容器列表（映射为 docker ps --format json 同构字段）
     *
     * @param all 是否包含已停止容器
     * @return 容器列表
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public List<Map<String, Object>> listContainers(boolean all) throws IOException, InterruptedException {
        JsonNode root = getJson("/containers/json?all=" + (all ? 1 : 0));
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode item : root) {
            Map<String, Object> m = new LinkedHashMap<>();
            String id = item.path("Id").asText("");
            m.put("id", id.length() > 12 ? id.substring(0, 12) : id);
            String name = "";
            JsonNode names = item.path("Names");
            if (names.isArray() && names.size() > 0) {
                name = names.get(0).asText("");
                if (name.startsWith("/")) {
                    name = name.substring(1);
                }
            }
            m.put("name", name);
            m.put("image", item.path("Image").asText(""));
            m.put("state", item.path("State").asText(""));
            m.put("status", item.path("Status").asText(""));
            m.put("ports", formatPorts(item.path("Ports")));
            m.put("network", firstNetwork(item));
            m.put("createdAt", epochToCliDate(item.path("Created").asLong(0L)));
            list.add(m);
        }
        return list;
    }

    /**
     * 容器生命周期动作（start/stop/restart）
     *
     * @param containerId 容器 ID 或名称
     * @param action      start / stop / restart
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public void containerAction(String containerId, String action) throws IOException, InterruptedException {
        postEmpty("/containers/" + urlEncode(containerId) + "/" + action);
    }

    /**
     * 读取容器日志（一次性，等价 docker logs --tail n）
     *
     * @param containerId 容器 ID 或名称
     * @param tail        尾部行数
     * @return 日志文本
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public String containerLogs(String containerId, int tail) throws IOException, InterruptedException {
        HttpRequest request = request("/containers/" + urlEncode(containerId)
                + "/logs?stdout=1&stderr=1&timestamps=0&tail=" + tail).GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Docker API 返回 " + response.statusCode() + ": " + readError(response));
        }
        try (InputStream in = new DemuxInputStream(response.body())) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 打开容器日志跟随流（等价 docker logs -f）
     *
     * @param containerId 容器 ID 或名称
     * @param tail        起始尾部行数
     * @return 解包后的文本流（调用方逐行读取，用完关闭）
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public InputStream streamLogs(String containerId, int tail) throws IOException, InterruptedException {
        HttpRequest request = request("/containers/" + urlEncode(containerId)
                + "/logs?stdout=1&stderr=1&follow=1&timestamps=0&tail=" + tail).GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Docker API 返回 " + response.statusCode() + ": " + readError(response));
        }
        return new DemuxInputStream(response.body());
    }

    /* ==================== 镜像 / 网络 / 卷 / 清理 ==================== */

    /**
     * 镜像列表（映射为 docker images --format json 同构字段）
     *
     * @return 镜像列表
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public List<Map<String, Object>> listImages() throws IOException, InterruptedException {
        JsonNode root = getJson("/images/json");
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode item : root) {
            Map<String, Object> m = new LinkedHashMap<>();
            String id = item.path("Id").asText("").replaceFirst("^sha256:", "");
            m.put("id", id.length() > 12 ? id.substring(0, 12) : id);
            String repository = "";
            String tag = "";
            JsonNode repoTags = item.path("RepoTags");
            if (repoTags.isArray() && repoTags.size() > 0) {
                String repoTag = repoTags.get(0).asText("");
                int idx = repoTag.lastIndexOf(':');
                if (idx > 0) {
                    repository = repoTag.substring(0, idx);
                    tag = repoTag.substring(idx + 1);
                } else {
                    repository = repoTag;
                }
            }
            m.put("repository", repository);
            m.put("tag", tag);
            m.put("size", humanSize(item.path("Size").asLong(0L)));
            m.put("createdAt", epochToIso(item.path("Created").asLong(0L)));
            list.add(m);
        }
        return list;
    }

    /**
     * 网络列表（映射为 docker network ls --format json 同构字段）
     *
     * @return 网络列表
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public List<Map<String, Object>> listNetworks() throws IOException, InterruptedException {
        JsonNode root = getJson("/networks");
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode item : root) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", item.path("Id").asText(""));
            m.put("name", item.path("Name").asText(""));
            m.put("driver", item.path("Driver").asText(""));
            m.put("scope", item.path("Scope").asText(""));
            m.put("internal", item.path("Internal").asBoolean(false));
            m.put("createdAt", isoOrNull(item.path("Created").asText("")));
            list.add(m);
        }
        return list;
    }

    /**
     * 卷列表（映射为 docker volume ls --format json 同构字段）
     *
     * @return 卷列表
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public List<Map<String, Object>> listVolumes() throws IOException, InterruptedException {
        JsonNode root = getJson("/volumes");
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode item : root.path("Volumes")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", item.path("Name").asText(""));
            m.put("driver", item.path("Driver").asText(""));
            m.put("mountpoint", item.path("Mountpoint").asText(""));
            m.put("createdAt", isoOrNull(item.path("CreatedAt").asText("")));
            list.add(m);
        }
        return list;
    }

    /**
     * 一键清理（等价 docker system prune -af --volumes）
     *
     * @return 汇总输出（与 CLI stdout 文本同构）
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public Map<String, Object> prune() throws IOException, InterruptedException {
        HttpRequest request = request("/system/prune?containers=1&images=1&networks=1&volumes=1")
                .timeout(java.time.Duration.ofSeconds(120)).POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Docker API 返回 " + response.statusCode() + ": " + response.body());
        }
        JsonNode node = MAPPER.readTree(response.body());
        StringBuilder sb = new StringBuilder();
        appendDeleted(sb, "Deleted Containers", node.path("ContainersDeleted"));
        appendDeleted(sb, "Deleted Images", node.path("ImagesDeleted"));
        appendDeleted(sb, "Deleted Networks", node.path("NetworksDeleted"));
        appendDeleted(sb, "Deleted Volumes", node.path("VolumesDeleted"));
        long reclaimed = node.path("SpaceReclaimed").asLong(0L);
        sb.append("Total reclaimed space: ").append(humanSize(reclaimed)).append('\n');
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("output", sb.toString());
        return out;
    }

    /* ==================== Raw 原语（独立模块自行映射形状） ==================== */

    /**
     * 容器列表原始响应（{@code /containers/json}）
     *
     * @param all 是否包含已停止容器
     * @return Docker API 原始 JSON 数组
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public JsonNode listContainersRaw(boolean all) throws IOException, InterruptedException {
        return getJson("/containers/json?all=" + (all ? 1 : 0));
    }

    /**
     * 容器详情原始响应（{@code /containers/{id}/json}，等价 docker inspect 单元素）
     *
     * @param containerId 容器 ID 或名称
     * @return Docker API 原始 JSON 对象
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public JsonNode inspectContainerRaw(String containerId) throws IOException, InterruptedException {
        return getJson("/containers/" + urlEncode(containerId) + "/json");
    }

    /**
     * 删除容器（等价 docker rm）
     *
     * @param containerId 容器 ID 或名称
     * @param force       强制删除（运行中容器）
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public void removeContainer(String containerId, boolean force) throws IOException, InterruptedException {
        delete("/containers/" + urlEncode(containerId) + (force ? "?force=1" : ""));
    }

    /**
     * 创建容器（等价 docker create；请求体为 Docker API ContainerCreate 结构，
     * 由调用方负责拼装，本类不感知具体业务模型）
     *
     * @param name 容器名（可空）
     * @param spec 创建请求体
     * @return 容器 ID
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public String createContainer(String name, Map<String, Object> spec) throws IOException, InterruptedException {
        String path = "/containers/create" + (name == null || name.isBlank() ? "" : "?name=" + urlEncode(name));
        HttpResponse<String> response = postJsonForResponse(path, spec);
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new IOException("Docker API 返回 " + response.statusCode() + ": " + response.body());
        }
        return MAPPER.readTree(response.body()).path("Id").asText("");
    }

    /**
     * 启动容器（等价 docker start）
     *
     * @param containerId 容器 ID 或名称
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public void startContainer(String containerId) throws IOException, InterruptedException {
        postEmpty("/containers/" + urlEncode(containerId) + "/start");
    }

    /**
     * 拉取镜像进度流（{@code POST /images/create}，响应为 JSON 行进度流，非帧流）
     *
     * @param fromImage    镜像名（可含 tag / digest 前的仓库部分）
     * @param tag          标签（可空；digest 拉取时留空）
     * @param registryAuth X-Registry-Auth 头（base64 凭据 JSON，可空）
     * @return 进度 JSON 行流（调用方逐行读取，用完关闭）
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public InputStream pullImageStream(String fromImage, String tag, String registryAuth)
            throws IOException, InterruptedException {
        String path = "/images/create?fromImage=" + urlEncode(fromImage)
                + (tag == null || tag.isBlank() ? "" : "&tag=" + urlEncode(tag));
        HttpRequest.Builder builder = request(path).POST(HttpRequest.BodyPublishers.noBody());
        if (registryAuth != null && !registryAuth.isBlank()) {
            builder.header("X-Registry-Auth", registryAuth);
        }
        HttpResponse<InputStream> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Docker API 返回 " + response.statusCode() + ": " + readError(response));
        }
        return response.body();
    }

    /**
     * 删除镜像（等价 docker rmi）
     *
     * @param imageName 镜像名或 ID（可含 sha256: 前缀）
     * @param force     强制删除
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public void removeImage(String imageName, boolean force) throws IOException, InterruptedException {
        delete("/images/" + urlEncode(imageName) + (force ? "?force=1" : ""));
    }

    /**
     * 镜像列表原始响应（{@code /images/json}）
     *
     * @return Docker API 原始 JSON 数组
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public JsonNode listImagesRaw() throws IOException, InterruptedException {
        return getJson("/images/json");
    }

    /**
     * 网络列表原始响应（{@code /networks}）
     *
     * @return Docker API 原始 JSON 数组
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public JsonNode listNetworksRaw() throws IOException, InterruptedException {
        return getJson("/networks");
    }

    /**
     * 创建网络（{@code POST /networks/create}）
     *
     * @param spec 创建请求体（Name / Driver / Options）
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public void createNetwork(Map<String, Object> spec) throws IOException, InterruptedException {
        HttpResponse<String> response = postJsonForResponse("/networks/create", spec);
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new IOException("Docker API 返回 " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * 删除网络（等价 docker network rm）
     *
     * @param id 网络 ID 或名称
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public void removeNetwork(String id) throws IOException, InterruptedException {
        delete("/networks/" + urlEncode(id));
    }

    /**
     * 网络详情原始响应（{@code /networks/{name}}，等价 docker network inspect）
     *
     * @param name 网络名
     * @return Docker API 原始 JSON（单个对象或数组）
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public JsonNode inspectNetworkRaw(String name) throws IOException, InterruptedException {
        return getJson("/networks/" + urlEncode(name));
    }

    /**
     * 卷列表原始响应（{@code /volumes}）
     *
     * @return Docker API 原始 JSON（{@code {Volumes: [...]}}）
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public JsonNode listVolumesRaw() throws IOException, InterruptedException {
        return getJson("/volumes");
    }

    /**
     * 创建卷（{@code POST /volumes/create}）
     *
     * @param spec 创建请求体（Name / Driver / Options）
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public void createVolume(Map<String, Object> spec) throws IOException, InterruptedException {
        HttpResponse<String> response = postJsonForResponse("/volumes/create", spec);
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new IOException("Docker API 返回 " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * 删除卷（等价 docker volume rm）
     *
     * @param name 卷名
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public void removeVolume(String name) throws IOException, InterruptedException {
        delete("/volumes/" + urlEncode(name));
    }

    /**
     * 卷详情原始响应（{@code /volumes/{name}}，等价 docker volume inspect）
     *
     * @param name 卷名
     * @return Docker API 原始 JSON 对象
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    public JsonNode inspectVolumeRaw(String name) throws IOException, InterruptedException {
        return getJson("/volumes/" + urlEncode(name));
    }

    /* ==================== HTTP 基础 ==================== */

    /**
     * GET 并解析 JSON
     *
     * @param pathAndQuery 路径与查询串
     * @return JSON 节点
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    private JsonNode getJson(String pathAndQuery) throws IOException, InterruptedException {
        HttpRequest request = request(pathAndQuery).GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Docker API 返回 " + response.statusCode() + ": " + response.body());
        }
        return MAPPER.readTree(response.body());
    }

    /**
     * POST 空体（204/200 视为成功）
     *
     * @param pathAndQuery 路径与查询串
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    private void postEmpty(String pathAndQuery) throws IOException, InterruptedException {
        HttpRequest request = request(pathAndQuery).POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 204) {
            throw new IOException("Docker API 返回 " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * POST JSON 体并返回原始响应（状态码由调用方按端点判定）
     *
     * @param pathAndQuery 路径与查询串
     * @param body         JSON 请求体
     * @return 原始字符串响应
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    private HttpResponse<String> postJsonForResponse(String pathAndQuery, Map<String, Object> body)
            throws IOException, InterruptedException {
        HttpRequest request = request(pathAndQuery)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body == null ? Map.of() : body)))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * DELETE（204/200 视为成功）
     *
     * @param pathAndQuery 路径与查询串
     * @throws IOException 网络/协议异常
     * @throws InterruptedException 请求被中断
     */
    private void delete(String pathAndQuery) throws IOException, InterruptedException {
        HttpRequest request = request(pathAndQuery).DELETE().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 204) {
            throw new IOException("Docker API 返回 " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * 构造请求建造器
     *
     * @param pathAndQuery 路径与查询串
     * @return 请求建造器
     */
    private HttpRequest.Builder request(String pathAndQuery) {
        return HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                .timeout(java.time.Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS));
    }

    /**
     * 读取错误响应体（截断）
     *
     * @param response 响应
     * @return 错误消息
     */
    private static String readError(HttpResponse<InputStream> response) {
        try (InputStream in = response.body()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode node = MAPPER.readTree(body);
            String message = node.path("message").asText("");
            if (!message.isBlank()) {
                return message;
            }
            return body.length() > 200 ? body.substring(0, 200) : body;
        } catch (Exception e) {
            return "(无法读取错误详情)";
        }
    }

    /* ==================== 映射辅助 ==================== */

    /**
     * 端口数组映射为 CLI 风格文本（0.0.0.0:8080->80/tcp, ...）
     *
     * @param ports Docker API 端口数组
     * @return 端口描述文本
     */
    private static String formatPorts(JsonNode ports) {
        if (!ports.isArray() || ports.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : ports) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            String ip = p.path("IP").asText("");
            int publicPort = p.path("PublicPort").asInt(0);
            int privatePort = p.path("PrivatePort").asInt(0);
            String type = p.path("Type").asText("tcp");
            if (publicPort > 0) {
                sb.append(ip).append(':').append(publicPort).append("->")
                        .append(privatePort).append('/').append(type);
            } else {
                sb.append(privatePort).append('/').append(type);
            }
        }
        return sb.toString();
    }

    /**
     * 取容器第一个网络名
     *
     * @param item 容器 API 对象
     * @return 网络名
     */
    private static String firstNetwork(JsonNode item) {
        JsonNode networks = item.path("NetworkSettings").path("Networks");
        if (networks.isObject()) {
            var it = networks.fieldNames();
            if (it.hasNext()) {
                return it.next();
            }
        }
        return "";
    }

    /**
     * epoch 秒转 CLI 日期文本（yyyy-MM-dd HH:mm:ss Z 'CST'，与 docker ps CreatedAt 形态一致）
     *
     * @param epochSecond epoch 秒
     * @return 日期文本
     */
    private static String epochToCliDate(long epochSecond) {
        if (epochSecond <= 0) {
            return "";
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochSecond(epochSecond)) + " CST";
    }

    /**
     * epoch 秒转 ISO-8601（镜像 createdAt 与 CLI toIso 解析口径一致）
     *
     * @param epochSecond epoch 秒
     * @return ISO 文本
     */
    private static String epochToIso(long epochSecond) {
        if (epochSecond <= 0) {
            return "";
        }
        return Instant.ofEpochSecond(epochSecond).toString();
    }

    /**
     * ISO 文本透传（空值归一为空串）
     *
     * @param iso ISO 文本
     * @return 非空 ISO 文本
     */
    private static String isoOrNull(String iso) {
        return iso == null ? "" : iso;
    }

    /**
     * 字节数转人类可读大小
     *
     * @param bytes 字节数
     * @return 如 123.4MB
     */
    private static String humanSize(long bytes) {
        if (bytes <= 0) {
            return "0B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        if (unit == 0) {
            return (int) value + units[unit];
        }
        return String.format("%.1f%s", value, units[unit]);
    }

    /**
     * 清理条目拼接
     *
     * @param sb   输出缓冲
     * @param title 标题
     * @param deleted 删除条目节点（字符串数组或对象数组）
     */
    private static void appendDeleted(StringBuilder sb, String title, JsonNode deleted) {
        if (!deleted.isArray() || deleted.isEmpty()) {
            return;
        }
        sb.append(title).append(":\n");
        for (JsonNode item : deleted) {
            String text = item.isTextual() ? item.asText("") : item.toString();
            sb.append("  ").append(text).append('\n');
        }
    }

    /**
     * URL 路径编码（容器 ID / 名称）
     *
     * @param raw 原始值
     * @return 编码值
     */
    private static String urlEncode(String raw) {
        return java.net.URLEncoder.encode(raw == null ? "" : raw, StandardCharsets.UTF_8);
    }

    /**
     * 多路复用流解包器
     *
     * <p>Docker API 在容器未启用 TTY 时，logs/exec 等端点返回帧流：
     * 每帧头 8 字节 = 流类型(1B) + 填充(3B) + 大端长度(4B)，随后为载荷。
     * 本流自动探测帧结构并剥离帧头；对 TTY 容器的原始文本流按原样透传。</p>
     */
    static class DemuxInputStream extends InputStream {

        /**
         * 底层输入流
         */
        private final InputStream in;

        /**
         * 当前帧剩余可读字节数（-1 表示原始透传模式）
         */
        private long frameRemaining = -1;

        /**
         * 是否已完成帧结构探测
         */
        private boolean probed;

        /**
         * 探测前缓存的首帧头（非帧流时先回放这些字节）
         */
        private byte[] preamble;

        /**
         * 回放读取位置
         */
        private int preamblePos;

        /**
         * 构造解包流
         *
         * @param in 底层输入流
         */
        DemuxInputStream(InputStream in) {
            this.in = new BufferedInputStream(in, 8192);
        }

        @Override
        public int read() throws IOException {
            if (!probed) {
                probe();
            }
            if (preamble != null) {
                if (preamblePos < preamble.length) {
                    return preamble[preamblePos++] & 0xFF;
                }
                preamble = null;
            }
            if (frameRemaining == 0) {
                // 当前帧读完，跳过填充对齐后读下一帧头
                skipToNextFrame();
            }
            if (frameRemaining > 0) {
                frameRemaining--;
                return in.read();
            }
            return in.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (!probed) {
                probe();
            }
            if (preamble != null) {
                int n = Math.min(length, preamble.length - preamblePos);
                System.arraycopy(preamble, preamblePos, buffer, offset, n);
                preamblePos += n;
                if (preamblePos >= preamble.length) {
                    preamble = null;
                }
                return n;
            }
            if (frameRemaining == 0) {
                skipToNextFrame();
            }
            if (frameRemaining > 0) {
                int want = (int) Math.min(length, frameRemaining);
                int n = in.read(buffer, offset, want);
                if (n > 0) {
                    frameRemaining -= n;
                }
                return n;
            }
            return in.read(buffer, offset, length);
        }

        /**
         * 首次读取时探测帧结构：首 8 字节若为合法帧头则进入解包模式，否则原样透传。
         *
         * @throws IOException 读取异常
         */
        private void probe() throws IOException {
            probed = true;
            in.mark(DEMUX_HEADER_LENGTH + 1);
            byte[] head = new byte[DEMUX_HEADER_LENGTH];
            int read = 0;
            while (read < DEMUX_HEADER_LENGTH) {
                int n = in.read(head, read, DEMUX_HEADER_LENGTH - read);
                if (n < 0) {
                    // 流已结束（空日志）
                    in.reset();
                    frameRemaining = -1;
                    return;
                }
                read += n;
            }
            int type = head[0] & 0xFF;
            boolean paddingZero = (head[1] == 0 && head[2] == 0 && head[3] == 0);
            long frameLen = ((head[4] & 0xFFL) << 24) | ((head[5] & 0xFFL) << 16)
                    | ((head[6] & 0xFFL) << 8) | (head[7] & 0xFFL);
            if ((type == 0 || type == 1 || type == 2) && paddingZero && frameLen > 0 && frameLen <= DEMUX_MAX_FRAME) {
                // 合法帧头：进入解包模式
                frameRemaining = frameLen;
                return;
            }
            // 非帧流（TTY 容器）：原样透传，先回放已读字节
            in.reset();
            preamble = head;
            preamblePos = 0;
            frameRemaining = -1;
        }

        /**
         * 读完一帧后读取下一帧头（跳过流结束判定）。
         *
         * @throws IOException 读取异常
         */
        private void skipToNextFrame() throws IOException {
            byte[] head = new byte[DEMUX_HEADER_LENGTH];
            int read = 0;
            while (read < DEMUX_HEADER_LENGTH) {
                int n = in.read(head, read, DEMUX_HEADER_LENGTH - read);
                if (n < 0) {
                    frameRemaining = -1;
                    return;
                }
                read += n;
            }
            int type = head[0] & 0xFF;
            long frameLen = ((head[4] & 0xFFL) << 24) | ((head[5] & 0xFFL) << 16)
                    | ((head[6] & 0xFFL) << 8) | (head[7] & 0xFFL);
            if ((type == 0 || type == 1 || type == 2) && frameLen <= DEMUX_MAX_FRAME) {
                frameRemaining = frameLen;
            } else {
                // 结构异常，回落透传
                preamble = head;
                preamblePos = 0;
                frameRemaining = -1;
            }
        }
    }
}
