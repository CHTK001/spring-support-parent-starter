package com.chua.starter.device.provider.sdk.protocol;

import com.chua.starter.device.provider.sdk.AbstractPlatformSdk;
import com.chua.starter.device.provider.sdk.PlatformApiResult;
import com.chua.starter.device.provider.sdk.PlatformApiSpec;
import com.chua.starter.device.provider.sdk.PlatformApiVersion;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
import com.chua.starter.device.spi.model.VendorDevice;
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

/**
 * ONVIF 标准协议 SDK 实现。
 *
 * <h3>协议说明</h3>
 * <p>ONVIF 用 SOAP over HTTP（设备服务/媒体服务/云台服务/事件服务）+ WS-Discovery 发现，
 * 直连设备 IP（{@code http://设备IP:80/onvif/device_service}），没有云端网关。
 * 认证为 WS-Security UsernameToken（digest，含 nonce/created）。</p>
 *
 * <p>本实现直连设备：凭据 {@code baseUrl} 填设备 ONVIF 地址，
 * {@code appKey}/{@code appSecret} 填设备用户名/密码。
 * 未配置 {@code baseUrl} 时明确报错，不伪造数据。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
public class OnvifSdk extends AbstractPlatformSdk {

    /** 平台标识 */
    public static final String PLATFORM = "onvif";

    /** 厂商标识（标准协议，不绑定厂商） */
    public static final String VENDOR = "standard";

    /** HTTP 客户端 */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** ONVIF 设备服务命名空间 */
    private static final String DEVICE_NS = "http://www.onvif.org/ver10/device/wsdl/";

    /** ONVIF 媒体服务命名空间 */
    private static final String MEDIA_NS = "http://www.onvif.org/ver10/media/wsdl";

    /** ONVIF 云台服务命名空间 */
    private static final String PTZ_NS = "http://www.onvif.org/ver20/ptz/wsdl";

    /** ONVIF 事件服务命名空间 */
    private static final String EVENTS_NS = "http://www.onvif.org/ver10/events/wsdl";

    /**
     * 构造 ONVIF SDK 实现。
     *
     * @param info 连接信息
     */
    public OnvifSdk(DeviceConnectInfo info) {
        super(info);
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public PlatformApiVersion apiVersion() {
        return PlatformApiVersion.documented("onvif-2.0", "https://www.onvif.org/profiles/");
    }

    @Override
    public List<PlatformApiSpec> apis() {
        return Gb28181Apis.onvif();
    }

    /**
     * 设备 ONVIF 服务地址（凭据必填，直连设备）。
     *
     * @return base url；未配置返回 null
     */
    private String baseUrl() {
        String base = extra("baseUrl");
        return base == null || base.isBlank() ? null : base.trim().replaceAll("/+$", "");
    }

    @Override
    public boolean testConnection() {
        String base = baseUrl();
        if (base == null) {
            log.warn("[ONVIF] 未配置设备 ONVIF 地址（baseUrl）");
            return false;
        }
        try {
            JsonNode root = soap(base + "/onvif/device_service", "GetDeviceInformation");
            return root != null;
        } catch (Exception ex) {
            log.warn("[ONVIF] 连通性探测失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public List<VendorDevice> listDevices() {
        String base = baseUrl();
        if (base == null) {
            throw new IllegalStateException(
                    "ONVIF 未配置设备地址：请在凭据里填 baseUrl（如 http://192.168.1.64/onvif/device_service）");
        }
        // ONVIF 是单设备直连协议：一个凭据对应一台设备，返回该设备本身
        JsonNode info = soap(base + "/onvif/device_service", "GetDeviceInformation");
        List<VendorDevice> result = new ArrayList<>();
        if (info != null) {
            JsonNode body = info.findPath("Body");
            JsonNode resp = body.isMissingNode() ? info : body;
            JsonNode data = resp.findPath("GetDeviceInformationResponse");
            String manufacturer = data.path("Manufacturer").asText("");
            String model = data.path("Model").asText("");
            String firmware = data.path("FirmwareVersion").asText("");
            String serial = data.path("SerialNumber").asText("");
            result.add(new VendorDevice(
                    serial.isEmpty() ? base : serial,
                    (manufacturer + " " + model).trim(),
                    model.isEmpty() ? firmware : model,
                    "camera",
                    serial,
                    URI.create(base).getHost(),
                    URI.create(base).getPort(),
                    "",
                    null,
                    null,
                    1,
                    true));
        }
        return result;
    }

    @Override
    public PlatformApiResult invoke(String code, Map<String, Object> params) {
        PlatformApiSpec spec = apis().stream()
                .filter(item -> item.code().equals(code))
                .findFirst()
                .orElse(null);
        if (spec == null) {
            return PlatformApiResult.fail(code, "未登记的能力编码: " + code, null, 0L);
        }
        long start = System.currentTimeMillis();
        String base = baseUrl();
        if (base == null) {
            return PlatformApiResult.fail(code,
                    "ONVIF 未配置设备地址（baseUrl）；ONVIF 直连设备，需填设备 ONVIF 服务地址",
                    null, System.currentTimeMillis() - start);
        }
        try {
            String action = spec.code().startsWith("onvif.")
                    ? spec.code().substring("onvif.".length()) : spec.code();
            String namespace = serviceNamespaceOf(action);
            // 媒体/云台/事件服务地址由设备通过 GetCapabilities 告知，取不到时回落设备服务地址
            String url = base + "/onvif/device_service";
            if (!DEVICE_NS.equals(namespace)) {
                String service = MEDIA_NS.equals(namespace) ? "Media"
                        : PTZ_NS.equals(namespace) ? "PTZ" : "Events";
                String discovered = serviceUrl(base, service);
                if (discovered != null) {
                    url = discovered;
                }
            }
            JsonNode root = soap(url, action, namespace, "");
            Object normalized = root == null ? null : MAPPER.convertValue(root, Object.class);
            return PlatformApiResult.ok(code, normalized, root == null ? null : root.toString(),
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return PlatformApiResult.fail(code, ex.getMessage(), null, System.currentTimeMillis() - start);
        }
    }

    /**
     * 发一次 ONVIF SOAP 请求（WS-Security UsernameToken）。
     *
     * @param url    目标服务地址
     * @param action ONVIF 动作名
     * @return 响应根节点
     */
    private JsonNode soap(String url, String action) {
        return soap(url, action, serviceNamespaceOf(action), "");
    }

    /**
     * 发一次 ONVIF SOAP 请求。
     *
     * <p>ONVIF 每个服务（device/media/ptz/events）是不同的 WSDL 地址与命名空间，
     * 把 Media 的 {@code GetProfiles} 发到 device_service 会被设备判为无效动作。
     * 因此这里必须显式带命名空间与该服务的 SOAP 动作头。</p>
     *
     * @param url        目标服务地址
     * @param action     动作名
     * @param namespace  该动作所属服务的命名空间
     * @param innerBody  动作体内部 XML（为空表示无参动作）
     * @return 响应根节点
     */
    private JsonNode soap(String url, String action, String namespace, String innerBody) {
        String prefix = prefixOf(namespace);
        try {
            String nonce = java.util.UUID.randomUUID().toString();
            String created = java.time.Instant.now().toString();
            String username = info.appKey() == null ? "" : info.appKey();
            String password = info.appSecret() == null ? "" : info.appSecret();
            String nonceB64 = Base64.getEncoder().encodeToString(nonce.getBytes(StandardCharsets.UTF_8));
            String passwordDigest = digest(nonce, created, password);
            String envelope = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" "
                    + "xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" "
                    + "xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\" "
                    + "xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\" "
                    + "xmlns:tev=\"http://www.onvif.org/ver10/events/wsdl\" "
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
                    + "<s:Body><" + prefix + ":" + action + ">"
                    + innerBody + "</" + prefix + ":" + action + "></s:Body></s:Envelope>";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/soap+xml; charset=utf-8; action=\"" + namespace + action + "\"")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                String body = response.body() == null ? "" : response.body();
                throw new IllegalStateException("ONVIF HTTP " + response.statusCode()
                        + ": " + body.substring(0, Math.min(300, body.length())));
            }
            return xmlToJson(response.body());
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("ONVIF 调用失败: " + action + " -> " + ex.getMessage(), ex);
        }
    }

    /**
     * 动作所属服务的命名空间（ONVIF 规范固定值）。
     *
     * @param action 动作名
     * @return 命名空间
     */
    private static String serviceNamespaceOf(String action) {
        String a = action.toLowerCase();
        if (a.startsWith("getprofiles") || a.startsWith("getstreamuri")
                || a.startsWith("getsnapshoturi") || a.startsWith("getvideosources")
                || a.startsWith("getvideoncoder")) {
            return MEDIA_NS;
        }
        if (a.startsWith("continuousmove") || a.startsWith("absolute") || a.startsWith("relative")
                || a.startsWith("stop") || a.startsWith("getnodes") || a.startsWith("getstatus")) {
            return PTZ_NS;
        }
        if (a.startsWith("createpullpoint") || a.startsWith("pullmessages")
                || a.startsWith("geteventproperties") || a.startsWith("unsubscribe")) {
            return EVENTS_NS;
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
        if (EVENTS_NS.equals(namespace)) {
            return "tev";
        }
        return "tds";
    }

    /**
     * 从 GetCapabilities 响应里取各服务的真实地址。
     *
     * <p>ONVIF 的服务端点不固定在 {@code /onvif/device_service}，必须由设备自己告知；
     * 部分设备会返回多条 XAddr（多网卡），这里取第一条。</p>
     *
     * @param base    凭据里的设备地址
     * @param service 服务键：Media / PTZ / Events
     * @return 服务地址；未取到返回 null
     */
    private String serviceUrl(String base, String service) {
        try {
            JsonNode root = soap(base + "/onvif/device_service", "GetCapabilities", DEVICE_NS, "");
            JsonNode found = findFirst(root, service, "XAddr");
            if (found != null && !found.asText("").isBlank()) {
                return found.asText();
            }
        } catch (Exception ex) {
            log.debug("[ONVIF] GetCapabilities 未取到 {} 地址: {}", service, ex.getMessage());
        }
        return null;
    }

    /**
     * 深度优先查找第一个匹配的嵌套字段。
     *
     * @param node     节点
     * @param parent   目标父元素名（如 Media）
     * @param child    子字段名（如 XAddr）
     * @return 命中的文本节点，未命中返回 null
     */
    private static JsonNode findFirst(JsonNode node, String parent, String child) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            JsonNode childNode = node.get(child);
            if (childNode != null && !childNode.isContainerNode()) {
                return childNode;
            }
            JsonNode parentNode = node.get(parent);
            if (parentNode != null) {
                JsonNode hit = findFirst(parentNode, parent, child);
                if (hit != null) {
                    return hit;
                }
            }
            var it = node.fields();
            while (it.hasNext()) {
                JsonNode hit = findFirst(it.next().getValue(), parent, child);
                if (hit != null) {
                    return hit;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode hit = findFirst(item, parent, child);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /**
     * 计算 WS-Security PasswordDigest = Base64(SHA1(nonce + created + password))。
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
     * 把 SOAP XML 响应转成 JSON 树（只保留元素名与文本，够诊断与字段取值）。
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
            org.w3c.dom.Element rootEl = doc.getDocumentElement();
            root.set("name", MAPPER.getNodeFactory().textNode(rootEl.getNodeName()));
            root.set("value", convertElement(rootEl));
            return root;
        } catch (Exception ex) {
            // XML 解析失败时退化为纯文本，仍可诊断
            com.fasterxml.jackson.databind.node.ObjectNode root = MAPPER.createObjectNode();
            root.put("raw", xml == null ? "" : xml.substring(0, Math.min(2000, xml.length())));
            return root;
        }
    }

    /**
     * 递归转换 DOM 元素为 JSON。
     *
     * @param element DOM 元素
     * @return JSON 节点
     */
    private JsonNode convertElement(org.w3c.dom.Element element) {
        com.fasterxml.jackson.databind.node.ObjectNode node = MAPPER.createObjectNode();
        org.w3c.dom.NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            org.w3c.dom.Node attr = attrs.item(i);
            node.put(attr.getNodeName(), attr.getNodeValue());
        }
        org.w3c.dom.NodeList children = element.getChildNodes();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
                text.append(child.getNodeValue());
            } else if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                String name = child.getNodeName();
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
}
