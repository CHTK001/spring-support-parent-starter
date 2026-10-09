package com.chua.starter.monitor.support.server;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.starter.filesystem.support.properties.FileStorageServerProperties;
import com.chua.starter.monitor.support.entity.MsServerNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件存储节点 CORS 装配门禁。
 *
 * <p>背景缺陷：独立文件服务（{@code FileStorageServerLauncher}）默认开 CORS，
 * 服务管理节点漏配——前端把 {@code filestorage.server} 指到节点地址时，
 * 自定义头（x-oauth-token / X-Trace-Id / X-Requested-With / X-Req-Fingerprint）
 * 触发的预检拿不到 {@code Access-Control-Allow-Origin}，整页请求被浏览器拦下，
 * 节点「能 curl 不能开」。本门禁锁两件事：</p>
 * <ol>
 *   <li>节点默认值必须<b>逐字段等于</b>独立文件服务的默认值（两边经
 *       {@link FileStorageServerProperties.Cors#applyTo} 单点映射，谁改默认值两边同时生效）；</li>
 *   <li>节点 config JSON 的 {@code cors} 覆盖必须真被消费（出现即整体替换）。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.42
 */
@DisplayName("文件存储节点 CORS 装配")
class ServerNodeRegistryFileStorageCorsTest {

    /** 被测注册表（解析路径不触达 mapper / 插件工厂，传 null 即可） */
    private final ServerNodeRegistry registry = new ServerNodeRegistry(null, null, new ObjectMapper());

    /** 造节点：config JSON 原样塞进实体 */
    private MsServerNode nodeWithConfig(String configJson) {
        MsServerNode node = new MsServerNode();
        node.setId(1L);
        node.setName("PWTEST-cors-gate");
        node.setConfig(configJson);
        return node;
    }

    @Test
    @DisplayName("缺省（无 config.cors）必须镜像独立文件服务默认值：开启+回显来源+允许凭据")
    void defaultsMirrorStandaloneFileServer() {
        ServerSetting.CorsConfig cors = new ServerSetting.CorsConfig();

        registry.resolveFileStorageCors(nodeWithConfig(null)).applyTo(cors);

        assertTrue(cors.isAllowOrigin(), "独立文件服务默认开 CORS，节点必须同默认");
        assertEquals("*", cors.getAllowedOrigins(), "默认任意来源（凭据开启时 CorsFilter 回显实际来源）");
        assertTrue(cors.isAllowCredentials(), "预览页 credentials:'include' 依赖 ACAC:true");
        assertNotNull(cors.getAllowedMethods());
        assertTrue(cors.getAllowedMethods().contains("PUT"), "上传用 PUT，方法表漏了会导致预检失败: " + cors.getAllowedMethods());
        // 前端 axios 每请求必带的三个第一方头 + 文件服务默认令牌头：缺一个预检整条失败
        assertTrue(cors.getAllowedHeaders().contains("x-oauth-token"), "缺令牌头: " + cors.getAllowedHeaders());
        assertTrue(cors.getAllowedHeaders().contains("X-Trace-Id"), "缺 X-Trace-Id: " + cors.getAllowedHeaders());
        assertTrue(cors.getAllowedHeaders().contains("X-Requested-With"), "缺 X-Requested-With: " + cors.getAllowedHeaders());
        assertTrue(cors.getAllowedHeaders().contains("X-Req-Fingerprint"), "缺加密指纹头: " + cors.getAllowedHeaders());
        // 前端要读的自定义响应头必须暴露，否则跨域下 headers.get() 恒为 null
        assertTrue(cors.getExposeHeaders().contains("Content-Disposition"), "缺下载文件名头: " + cors.getExposeHeaders());
        assertTrue(cors.getExposeHeaders().contains("X-FileStorage-Preview"), "缺预览通道标识头: " + cors.getExposeHeaders());
    }

    @Test
    @DisplayName("config.cors 出现即整体替换默认（收紧白名单/关闭开关都要能表达）")
    void configCorsOverridesDefaultsAsWhole() {
        String configJson = "{\"cors\":{\"enable\":false,\"allowedOrigins\":\"http://a.example,http://b.example\"}}";
        ServerSetting.CorsConfig cors = new ServerSetting.CorsConfig();

        registry.resolveFileStorageCors(nodeWithConfig(configJson)).applyTo(cors);

        assertFalse(cors.isAllowOrigin(), "显式关闭必须生效（不能再镜像默认开启）");
        assertEquals("http://a.example,http://b.example", cors.getAllowedOrigins(), "来源白名单按配置收紧");
        // 整体替换：未在覆盖里出现的字段取 Cors 自身默认，而不是节点默认
        assertEquals(new FileStorageServerProperties.Cors().getAllowedHeaders(), cors.getAllowedHeaders(),
                "覆盖块内未出现的字段按 Cors 默认值回填（与 yaml 属性绑定语义一致）");
    }

    @Test
    @DisplayName("config.cors 非对象（写错类型）不炸：静默回退默认值")
    void nonObjectCorsValueFallsBackToDefaults() {
        FileStorageServerProperties.Cors resolved = registry.resolveFileStorageCors(
                nodeWithConfig("{\"cors\":\"not-a-map\"}"));

        assertTrue(resolved.isEnable(), "写错类型按未配置处理，节点保持可用（默认开启）");
    }

    @Test
    @DisplayName("applyTo 对 null 目标安全（setting.getCors() 缺失时不抛 NPE）")
    void applyToNullTargetIsNoOp() {
        FileStorageServerProperties.Cors cors = new FileStorageServerProperties.Cors();

        cors.applyTo(null); // 不得抛异常
    }
}
