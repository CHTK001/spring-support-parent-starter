package com.chua.starter.monitor.arthas;

import com.chua.common.support.json.Json;
import com.chua.common.support.net.NetAddress;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.monitor.pojo.OnlineNodeInfo;
import com.chua.starter.monitor.service.NodeManagementService;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.HandlerMapping;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Arthas 控制台后端代理
 * - 前端选择包含 REPORT_ARTHAS_CLIENT_PORT 的在线节点后，调用 connect 获取后端代理地址
 * - 前端以 iframe 访问 /v1/arthas/console/{nodeId}/**，由本控制器反向代理到实际 arthas console
 *
 * 注意：
 * - 元数据键名为 report.client.arthas.port（对应 ReportDiscoveryEnvironment.REPORT_ARTHAS_CLIENT_PORT）
 * - 元数据的值预期为 ws(s)://host:port/ws
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/arthas")
@Api(tags = "Arthas 控制台代理")
@Tag(name = "Arthas 控制台代理")
public class ArthasConsoleProxyController {

    private static final String ARTHAS_META_KEY = "report.client.arthas.port";

    private final NodeManagementService nodeManagementService;
    private final RestTemplate restTemplate;

    @PostMapping("/connect")
    @Operation(summary = "连接指定节点的 Arthas 控制台", description = "返回后端代理的控制台访问地址")
    public ReturnResult<Map<String, Object>> connect(@RequestParam("nodeId") String nodeId) {
        try {
            if (StringUtils.isBlank(nodeId)) {
                return ReturnResult.error("节点ID不能为空");
            }
            var nodeResult = nodeManagementService.getNodeDetails(nodeId);
            if (!nodeResult.isSuccess() || nodeResult.getData() == null) {
                return ReturnResult.error("节点不存在或不可用");
            }
            OnlineNodeInfo node = nodeResult.getData();
            String base = buildConsoleBaseUrl(node);
            if (base == null) {
                return ReturnResult.error("节点未携带 Arthas 元数据或地址无效");
            }
            // 前端通过该代理地址加载控制台
            Map<String, Object> data = new HashMap<>();
            data.put("consoleUrl", "/monitor/api/v1/arthas/console/" + nodeId + "/");
            return ReturnResult.ok(data);
        } catch (Exception e) {
            log.error("连接 Arthas 控制台失败: nodeId={}", nodeId, e);
            return ReturnResult.error("连接 Arthas 控制台失败: " + e.getMessage());
        }
    }

    @GetMapping("/console/{nodeId}/**")
    @Operation(summary = "Arthas 控制台反向代理(GET)", description = "通过后端代理转发控制台请求")
    public ResponseEntity<byte[]> proxyConsoleGet(@PathVariable("nodeId") String nodeId,
                                                  HttpServletRequest request,
                                                  @RequestHeader HttpHeaders headers) {
        try {
            var nodeResult = nodeManagementService.getNodeDetails(nodeId);
            if (!nodeResult.isSuccess() || nodeResult.getData() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(("节点不可用").getBytes());
            }

            String path = extractRemainingPath(request);
            OnlineNodeInfo node = nodeResult.getData();
            String base = buildConsoleBaseUrl(node);
            if (base == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(("节点未携带 Arthas 元数据").getBytes());
            }

            String target = buildTargetUrl(base, path, request.getQueryString());
            log.debug("Proxy GET -> {}", target);

            HttpHeaders forwardHeaders = new HttpHeaders();
            forwardHeaders.putAll(headers);
            // 移除可能影响代理的头
            forwardHeaders.remove(HttpHeaders.HOST);

            RequestEntity<Void> req = new RequestEntity<>(forwardHeaders, HttpMethod.GET, URI.create(target));
            ResponseEntity<byte[]> resp = restTemplate.exchange(req, byte[].class);

            HttpHeaders respHeaders = new HttpHeaders();
            respHeaders.putAll(resp.getHeaders());
            return new ResponseEntity<>(resp.getBody(), respHeaders, resp.getStatusCode());
        } catch (Exception e) {
            log.error("代理控制台失败: nodeId={}, uri={}", nodeId, request.getRequestURI(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("代理失败: " + e.getMessage()).getBytes());
        }
    }

    // ============== 工具方法 ==============

    /**
     * 从请求中提取 /console/{nodeId}/ 之后的剩余路径
     */
    private String extractRemainingPath(HttpServletRequest request) {
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String pathWithin = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        AntPathMatcher matcher = new AntPathMatcher();
        String remaining = matcher.extractPathWithinPattern(pattern, pathWithin);
        if (remaining == null) return "";
        return remaining;
    }

    /**
     * 根据节点元数据构建 Arthas 控制台基础地址（http/https），去除末尾 /ws，并确保以 / 结尾
     */
    private String buildConsoleBaseUrl(OnlineNodeInfo node) {
        if (node == null || node.getMetadata() == null) return null;
        Object tunnel = node.getMetadata().get(ARTHAS_META_KEY);
        if (tunnel == null) return null;
        String addr = String.valueOf(tunnel).trim();
        if (StringUtils.isBlank(addr)) return null;
        try {
            // 兼容 ws(s)://host:port/ws 或 http(s)://host:port/
            String url = addr;
            url = url.replaceAll("/+$", "");
            if (StringUtils.startsWithIgnoreCase(url, "ws://")) {
                url = "http://" + url.substring("ws://".length());
            } else if (StringUtils.startsWithIgnoreCase(url, "wss://")) {
                url = "https://" + url.substring("wss://".length());
            }
            if (StringUtils.endsWithIgnoreCase(url, "/ws")) {
                url = url.substring(0, url.length() - 3);
            }
            if (!url.endsWith("/")) {
                url = url + "/";
            }
            // 简单校验
            NetAddress.of(url);
            return url;
        } catch (Exception e) {
            log.warn("解析 Arthas 地址失败: {}", addr, e);
            return null;
        }
    }

    /**
     * 组装目标URL，附加路径与查询
     */
    private String buildTargetUrl(String base, String path, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append(base);
        if (StringUtils.isNotBlank(path)) {
            sb.append(path);
        }
        if (StringUtils.isNotBlank(query)) {
            if (sb.indexOf("?") < 0) sb.append('?');
            else sb.append('&');
            sb.append(query);
        }
        return sb.toString();
    }
}

