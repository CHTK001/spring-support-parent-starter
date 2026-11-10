package com.chua.starter.device.provider.standard;

import com.chua.starter.device.provider.platform.AbstractOpenApiSession;
import com.chua.starter.device.spi.model.DeviceChannel;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.PresetInfo;
import com.chua.starter.device.spi.model.PtzCommand;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ONVIF 设备会话（直连设备，SOAP over HTTP）。
 *
 * <h3>与云平台会话的区别</h3>
 * <p>ONVIF 没有云端网关：一个凭据对应一台设备，地址在 {@code baseUrl}，
 * 用户名/密码在 {@code appKey}/{@code appSecret}。所有调用都走 SOAP
 * （WS-Security UsernameToken digest），并需按服务（device/media/ptz）
 * 分别寻址——把媒体动作发到 device_service 会被设备判为无效动作。</p>
 *
 * <h3>已实现的 ONVIF 动作</h3>
 * <ul>
 *   <li>设备：{@code GetDeviceInformation}、{@code GetCapabilities}、{@code GetSystemDateAndTime}</li>
 *   <li>媒体：{@code GetProfiles}、{@code GetStreamUri}、{@code GetSnapshotUri}</li>
 *   <li>云台：{@code ContinuousMove}、{@code Stop}、{@code GetPresets}、{@code SetPreset}、
 *       {@code GotoPreset}、{@code RemovePreset}</li>
 * </ul>
 *
 * <p><b>未验证说明</b>：本机无 ONVIF 设备可达，以下实现按 ONVIF 2.0 规范编写，
 * 未经真实设备联调，因此能力矩阵标 {@code IMPLEMENTED_UNVERIFIED}。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class OnvifSession extends AbstractOpenApiSession {

    /** ONVIF 设备服务命名空间 */
    private static final String DEVICE_NS = "http://www.onvif.org/ver10/device/wsdl/";

    /** ONVIF 媒体服务命名空间 */
    private static final String MEDIA_NS = "http://www.onvif.org/ver10/media/wsdl";

    /** ONVIF 云台服务命名空间 */
    private static final String PTZ_NS = "http://www.onvif.org/ver20/ptz/wsdl";

    /** 默认设备服务路径 */
    private static final String DEVICE_SERVICE = "/onvif/device_service";

    /** SOAP 超时（秒） */
    private static final int TIMEOUT = 30;

    /** HTTP 客户端 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 云台命令到 ONVIF PTZ 速度分量 */
    private static final Map<PtzCommand, double[]> PAN_TILT = Map.of(
            PtzCommand.UP, new double[]{0, 1},
            PtzCommand.DOWN, new double[]{0, -1},
            PtzCommand.LEFT, new double[]{-1, 0},
            PtzCommand.RIGHT, new double[]{1, 0});

    /** 设备服务地址（不含尾部斜杠） */
    private final String base;

    /** 缓存的服务端点：media / ptz */
    private final Map<String, String> endpoints = new LinkedHashMap<>();

    /** 媒体配置令牌（ProfileToken），多数动作都要它 */
    private volatile String profileToken;

    /**
     * 构造 ONVIF 会话。
     *
     * @param info 连接信息（baseUrl / appKey / appSecret 必填）
     */
    public OnvifSession(DeviceConnectInfo info) {
        super(info, info == null || info.extra() == null ? "" : String.valueOf(info.extra().get("baseUrl")));
        Object raw = info == null || info.extra() == null ? null : info.extra().get("baseUrl");
        String url = raw == null ? "" : String.valueOf(raw).trim();
        this.base = url.replaceAll("/+$", "");
    }

    @Override
    public String getDeviceId() {
        return base;
    }

    @Override
    public boolean isOnline() {
        return testConnection();
    }

    @Override
    public boolean testConnection() {
        if (base.isBlank()) {
            log.warn("[OnvifSession] 未配置设备地址 baseUrl");
            return false;
        }
        try {
            JsonNode root = soap(DEVICE_SERVICE, "GetDeviceInformation", DEVICE_NS, "");
            return root != null && !root.has("raw");
        } catch (Exception ex) {
            log.warn("[OnvifSession] 连通性探测失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        online = false;
    }

    @Override
    public List<DeviceChannel> listChannels() {
        JsonNode root = soap(mediaService(), "GetProfiles", MEDIA_NS, "");
        List<DeviceChannel> result = new ArrayList<>();
        int index = 1;
        for (JsonNode profile : collect(root, "Profiles")) {
            String token = text(profile, "token");
            if (token.isBlank()) {
                token = text(profile, "Token");
            }
            String name = text(profile, "Name");
            result.add(new DeviceChannel(
                    token.isBlank() ? base + "#" + index : token,
                    index,
                    name.isBlank() ? ("Profile " + index) : name,
                    base,
                    true,
                    text(profile, "VideoEncoderConfiguration"),
                    1));
            index++;
        }
        return result;
    }

    @Override
    public String getLiveUrl(int channel, String protocol) {
        String token = resolveProfileToken();
        String body = "<trt:ProfileToken>" + token + "</trt:ProfileToken>"
                + "<trt:StreamSetup><tt:Stream>RTP-Unicast</tt:Stream>"
                + "<tt:Transport><tt:Protocol>" + ("tcp".equalsIgnoreCase(protocol) ? "TCP" : "HTTP")
                + "</tt:Protocol></tt:Transport></trt:StreamSetup>";
        JsonNode root = soap(mediaService(), "GetStreamUri", MEDIA_NS, body);
        String uri = firstText(root, "Uri");
        if (uri.isBlank()) {
            throw new IllegalStateException("ONVIF GetStreamUri 未返回 Uri: " + root);
        }
        return uri;
    }

    @Override
    public java.io.InputStream capture(int channel) {
        String token = resolveProfileToken();
        JsonNode root = soap(mediaService(), "GetSnapshotUri", MEDIA_NS,
                "<trt:ProfileToken>" + token + "</trt:ProfileToken>");
        String uri = firstText(root, "Uri");
        if (uri.isBlank()) {
            throw new IllegalStateException("ONVIF GetSnapshotUri 未返回 Uri: " + root);
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(Duration.ofSeconds(TIMEOUT))
                    .header("Authorization", basicAuth())
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
            byte[] bytes = resp.body();
            return new java.io.ByteArrayInputStream(bytes == null ? new byte[0] : bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("ONVIF 抓图下载失败: " + uri + " -> " + ex.getMessage(), ex);
        }
    }

    @Override
    public void ptzControl(PtzCommand cmd, double speed) {
        double s = Math.max(0.05, Math.min(1.0, speed <= 0 ? 0.5 : speed));
        String body;
        double[] panTilt = PAN_TILT.get(cmd);
        if (panTilt != null) {
            body = "<tptz:ProfileToken>" + resolveProfileToken() + "</tptz:ProfileToken>"
                    + "<tptz:Velocity><tt:PanTilt x=\"" + (panTilt[0] * s) + "\" y=\"" + (panTilt[1] * s)
                    + "\"/></tptz:Velocity>";
        } else if (cmd == PtzCommand.ZOOM_IN || cmd == PtzCommand.ZOOM_OUT) {
            double zoom = cmd == PtzCommand.ZOOM_IN ? s : -s;
            body = "<tptz:ProfileToken>" + resolveProfileToken() + "</tptz:ProfileToken>"
                    + "<tptz:Velocity><tt:Zoom x=\"" + zoom + "\"/></tptz:Velocity>";
        } else {
            throw new IllegalArgumentException("ONVIF 不支持的云台命令: " + cmd);
        }
        soap(ptzService(), "ContinuousMove", PTZ_NS, body);
        try {
            // ONVIF 连续移动必须显式 Stop，否则云台会一直转
            Thread.sleep(Math.max(150L, (long) (500L / Math.max(0.05, s))));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        soap(ptzService(), "Stop", PTZ_NS,
                "<tptz:ProfileToken>" + resolveProfileToken() + "</tptz:ProfileToken>"
                        + "<tptz:PanTilt>true</tptz:PanTilt><tptz:Zoom>true</tptz:Zoom>");
    }

    @Override
    public void setPreset(int presetId, String name) {
        String body = "<tptz:ProfileToken>" + resolveProfileToken() + "</tptz:ProfileToken>"
                + "<tptz:PresetName>" + xmlEscape(name == null ? ("preset" + presetId) : name)
                + "</tptz:PresetName>";
        soap(ptzService(), "SetPreset", PTZ_NS, body);
    }

    @Override
    public void callPreset(int presetId) {
        String token = presetTokenOf(presetId);
        soap(ptzService(), "GotoPreset", PTZ_NS,
                "<tptz:ProfileToken>" + resolveProfileToken() + "</tptz:ProfileToken>"
                        + "<tptz:PresetToken>" + token + "</tptz:PresetToken>");
    }

    @Override
    public void deletePreset(int presetId) {
        String token = presetTokenOf(presetId);
        soap(ptzService(), "RemovePreset", PTZ_NS,
                "<tptz:ProfileToken>" + resolveProfileToken() + "</tptz:ProfileToken>"
                        + "<tptz:PresetToken>" + token + "</tptz:PresetToken>");
    }

    @Override
    public List<PresetInfo> listPresets() {
        JsonNode root = soap(ptzService(), "GetPresets", PTZ_NS,
                "<tptz:ProfileToken>" + resolveProfileToken() + "</tptz:ProfileToken>");
        List<PresetInfo> result = new ArrayList<>();
        for (JsonNode preset : collect(root, "Preset")) {
            String token = text(preset, "token");
            if (token.isBlank()) {
                token = text(preset, "Token");
            }
            String name = text(preset, "Name");
            try {
                result.add(new PresetInfo(Integer.parseInt(token), name));
            } catch (NumberFormatException ex) {
                log.debug("[OnvifSession] 预置位令牌非数字，跳过: {}", token);
            }
        }
        return result;
    }

    /**
     * 取媒体服务端点（设备通过 GetCapabilities 告知，取不到回落同源默认路径）。
     *
     * @return 媒体服务地址
     */
    private String mediaService() {
        return endpoint("Media");
    }

    /**
     * 取云台服务端点。
     *
     * @return 云台服务地址
     */
    private String ptzService() {
        return endpoint("PTZ");
    }

    /**
     * 解析服务端点并缓存。
     *
     * @param service 服务键：Media / PTZ
     * @return 服务地址
     */
    private String endpoint(String service) {
        String cached = endpoints.get(service);
        if (cached != null) {
            return cached;
        }
        synchronized (endpoints) {
            cached = endpoints.get(service);
            if (cached != null) {
                return cached;
            }
            String resolved = null;
            try {
                JsonNode root = soap(DEVICE_SERVICE, "GetCapabilities", DEVICE_NS, "");
                JsonNode hit = findFirst(root, service, "XAddr");
                if (hit != null && !hit.asText("").isBlank()) {
                    resolved = hit.asText();
                }
            } catch (Exception ex) {
                log.debug("[OnvifSession] GetCapabilities 未取到 {} 地址: {}", service, ex.getMessage());
            }
            if (resolved == null) {
                // 规范的设备通常把媒体/云台服务挂在同源路径下
                resolved = base + "/onvif/" + ("PTZ".equals(service) ? "ptz_service" : "media_service");
            }
            endpoints.put(service, resolved);
            return resolved;
        }
    }

    /**
     * 解析媒体配置令牌（取第一个 Profile）。
     *
     * @return ProfileToken
     */
    private String resolveProfileToken() {
        String cached = profileToken;
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        synchronized (this) {
            if (profileToken != null && !profileToken.isBlank()) {
                return profileToken;
            }
            JsonNode root = soap(mediaService(), "GetProfiles", MEDIA_NS, "");
            String token = "";
            for (JsonNode profile : collect(root, "Profiles")) {
                token = text(profile, "token");
                if (token.isBlank()) {
                    token = text(profile, "Token");
                }
                if (!token.isBlank()) {
                    break;
                }
            }
            if (token.isBlank()) {
                throw new IllegalStateException("ONVIF 设备未返回任何媒体 Profile");
            }
            profileToken = token;
            return token;
        }
    }

    /**
     * 把预置位编号映射成设备侧令牌（优先按序号、其次按令牌字面值）。
     *
     * @param presetId 预置位编号
     * @return 设备侧令牌
     */
    private String presetTokenOf(int presetId) {
        String byIndex = String.valueOf(presetId);
        for (PresetInfo preset : listPresets()) {
            if (preset.presetId() == presetId) {
                return byIndex;
            }
        }
        return byIndex;
    }

    /**
     * 执行一次 ONVIF SOAP 调用。
     *
     * @param servicePath 服务路径（相对于 base）
     * @param action      动作名
     * @param namespace   服务命名空间
     * @param innerBody   动作体内部 XML
     * @return 解析后的响应
     */
    @Override
    protected JsonNode doRequest(String method, String path, Map<String, Object> params) {
        String action = params == null ? "" : String.valueOf(params.getOrDefault("action", ""));
        String inner = params == null ? "" : String.valueOf(params.getOrDefault("body", ""));
        String ns = serviceNamespaceOf(action);
        return soap(path, action, ns, inner);
    }

    /**
     * 发一次 SOAP 请求（WS-Security UsernameToken digest）。
     *
     * @param path      服务路径（相对于 base）
     * @param action    动作名
     * @param namespace 服务命名空间
     * @param innerBody 动作体内部 XML
     * @return 解析后的响应
     */
    private JsonNode soap(String path, String action, String namespace, String innerBody) {
        if (base.isBlank()) {
            throw new IllegalStateException("ONVIF 未配置设备地址 baseUrl");
        }
        String url = path.startsWith("http") ? path : base + (path.startsWith("/") ? path : "/" + path);
        String prefix = prefixOf(namespace);
        try {
            String nonce = UUID.randomUUID().toString();
            String created = java.time.Instant.now().toString();
            String username = xmlEscape(info.appKey() == null ? "" : info.appKey());
            String password = info.appSecret() == null ? "" : info.appSecret();
            String nonceB64 = Base64.getEncoder().encodeToString(nonce.getBytes(StandardCharsets.UTF_8));
            String passwordDigest = digest(nonce, created, password);
            String envelope = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" "
                    + "xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" "
                    + "xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\" "
                    + "xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\" "
                    + "xmlns:tt=\"http://www.onvif.org/ver10/schema\">"
                    + "<s:Header><wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/"
                    + "oasis-200401-wss-wssecurity-secext-1.0.xsd\">"
                    + "<wsse:UsernameToken><wsse:Username>" + username + "</wsse:Username>"
                    + "<wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/"
                    + "oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">"
                    + passwordDigest + "</wsse:Password>"
                    + "<wsse:Nonce EncodingType=\"http://docs.oasis-open.org/wss/2004/01/"
                    + "oasis-200401-wss-soap-message-security-1.0#Base64Binary\">" + nonceB64 + "</wsse:Nonce>"
                    + "<wsu:Created xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/"
                    + "oasis-200401-wss-wssecurity-utility-1.0.xsd\">" + created + "</wsu:Created>"
                    + "</wsse:UsernameToken></wsse:Security></s:Header>"
                    + "<s:Body><" + prefix + ":" + action + ">" + innerBody
                    + "</" + prefix + ":" + action + "></s:Body></s:Envelope>";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT))
                    .header("Content-Type", "application/soap+xml; charset=utf-8; action=\""
                            + namespace + action + "\"")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                String body = response.body() == null ? "" : response.body();
                throw new IllegalStateException("ONVIF HTTP " + response.statusCode() + ": "
                        + body.substring(0, Math.min(300, body.length())));
            }
            return xmlToJson(response.body());
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("ONVIF 调用失败: " + action + " -> " + ex.getMessage(), ex);
        }
    }

    /**
     * WS-Security PasswordDigest = Base64(SHA1(nonce + created + password))。
     *
     * @param nonce    随机串
     * @param created  创建时间
     * @param password 密码
     * @return Base64 摘要
     */
    private static String digest(String nonce, String created, String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            md.update(nonce.getBytes(StandardCharsets.UTF_8));
            md.update(created.getBytes(StandardCharsets.UTF_8));
            md.update(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(md.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("ONVIF 摘要计算失败", ex);
        }
    }

    /**
     * 抓图下载用的 Basic 认证头。
     *
     * @return Authorization 头值
     */
    private String basicAuth() {
        String raw = (info.appKey() == null ? "" : info.appKey()) + ":"
                + (info.appSecret() == null ? "" : info.appSecret());
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 动作所属服务命名空间。
     *
     * @param action 动作名
     * @return 命名空间
     */
    private static String serviceNamespaceOf(String action) {
        String a = action.toLowerCase();
        if (a.startsWith("getprofiles") || a.startsWith("getstreamuri") || a.startsWith("getsnapshoturi")
                || a.startsWith("getvideosources")) {
            return MEDIA_NS;
        }
        if (a.startsWith("continuousmove") || a.startsWith("stop") || a.startsWith("getpresets")
                || a.startsWith("setpreset") || a.startsWith("gotopreset") || a.startsWith("removepreset")
                || a.startsWith("getnodes") || a.startsWith("getstatus")) {
            return PTZ_NS;
        }
        return DEVICE_NS;
    }

    /**
     * 命名空间对应的 XML 前缀。
     *
     * @param namespace 命名空间
     * @return 前缀
     */
    private static String prefixOf(String namespace) {
        if (MEDIA_NS.equals(namespace)) {
            return "trt";
        }
        if (PTZ_NS.equals(namespace)) {
            return "tptz";
        }
        return "tds";
    }

    /**
     * SOAP XML 转 JSON（保留元素名与文本，够取值与诊断）。
     *
     * @param xml SOAP 响应
     * @return JSON 根节点
     */
    private JsonNode xmlToJson(String xml) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root = MAPPER.createObjectNode();
            javax.xml.parsers.DocumentBuilderFactory factory =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            org.w3c.dom.Document doc = factory.newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            root.set("value", convertElement(doc.getDocumentElement()));
            return root;
        } catch (Exception ex) {
            com.fasterxml.jackson.databind.node.ObjectNode root = MAPPER.createObjectNode();
            root.put("raw", xml == null ? "" : xml.substring(0, Math.min(2000, xml.length())));
            return root;
        }
    }

    /**
     * 递归把 DOM 元素转成 JSON。
     *
     * @param element DOM 元素
     * @return JSON 节点
     */
    private JsonNode convertElement(org.w3c.dom.Element element) {
        com.fasterxml.jackson.databind.node.ObjectNode node = MAPPER.createObjectNode();
        org.w3c.dom.NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            org.w3c.dom.Node attr = attrs.item(i);
            String name = attr.getNodeName();
            node.put(name.contains(":") ? name.substring(name.indexOf(':') + 1) : name, attr.getNodeValue());
        }
        org.w3c.dom.NodeList children = element.getChildNodes();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
                text.append(child.getNodeValue());
            } else if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                String raw = child.getNodeName();
                String name = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
                JsonNode existing = node.get(name);
                JsonNode value = convertElement((org.w3c.dom.Element) child);
                if (existing == null) {
                    node.set(name, value);
                } else if (existing.isArray()) {
                    ((com.fasterxml.jackson.databind.node.ArrayNode) existing).add(value);
                } else {
                    com.fasterxml.jackson.databind.node.ArrayNode array = MAPPER.createArrayNode();
                    array.add(existing);
                    array.add(value);
                    node.set(name, array);
                }
            }
        }
        String trimmed = text.toString().trim();
        if (!trimmed.isEmpty()) {
            node.put("text", trimmed);
        }
        return node;
    }

    /**
     * 深度优先查找第一个匹配的嵌套叶子字段。
     *
     * @param node   节点
     * @param parent 目标父元素名（如 Media）
     * @param child  子字段名（如 XAddr）
     * @return 命中的文本节点，未命中返回 null
     */
    private static JsonNode findFirst(JsonNode node, String parent, String child) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode hit = findFirst(item, parent, child);
                if (hit != null) {
                    return hit;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        JsonNode childNode = node.get(child);
        if (childNode != null && !childNode.isContainerNode()) {
            return childNode;
        }
        var it = node.fields();
        while (it.hasNext()) {
            JsonNode hit = findFirst(it.next().getValue(), parent, child);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * 收集指定元素名的全部对象节点（深度优先）。
     *
     * @param root 根节点
     * @param name 元素名
     * @return 命中的对象列表
     */
    private static List<JsonNode> collect(JsonNode root, String name) {
        List<JsonNode> result = new ArrayList<>();
        collectInto(root, name, result);
        return result;
    }

    /**
     * 递归收集实现。
     *
     * @param node   节点
     * @param name   元素名
     * @param out    结果
     */
    private static void collectInto(JsonNode node, String name, List<JsonNode> out) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectInto(item, name, out);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        var it = node.fields();
        while (it.hasNext()) {
            var entry = it.next();
            if (name.equals(entry.getKey())) {
                JsonNode v = entry.getValue();
                if (v.isArray()) {
                    v.forEach(out::add);
                } else if (v.isObject()) {
                    out.add(v);
                }
            }
            collectInto(entry.getValue(), name, out);
        }
    }

    /**
     * 深度优先取第一个匹配元素的文本。
     *
     * @param node 节点
     * @param name 元素名
     * @return 文本，未命中为空串
     */
    private static String firstText(JsonNode node, String name) {
        List<JsonNode> hits = collect(node, name);
        for (JsonNode hit : hits) {
            String t = text(hit, "text");
            if (!t.isBlank()) {
                return t;
            }
        }
        return "";
    }

    /**
     * 取对象的文本字段。
     *
     * @param node 对象
     * @param key  字段名
     * @return 文本，缺失为空串
     */
    private static String text(JsonNode node, String key) {
        if (node == null || !node.isObject()) {
            return "";
        }
        JsonNode v = node.get(key);
        if (v == null) {
            return "";
        }
        return v.isValueNode() ? v.asText("") : v.path("text").asText("");
    }

    /**
     * XML 文本转义。
     *
     * @param v 原值
     * @return 转义后的值
     */
    private static String xmlEscape(String v) {
        if (v == null) {
            return "";
        }
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
