package com.chua.starter.monitor.support.nginx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chua.starter.datasourcehub.support.model.NginxSiteVO;
import com.chua.starter.datasourcehub.support.nginx.NginxConfigModelSupport;
import com.chua.starter.datasourcehub.support.nginx.NginxNode;
import com.chua.starter.datasourcehub.support.nginx.NginxParser;
import com.chua.starter.datasourcehub.support.nginx.NginxSiteExtractor;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 站点抽取器的真实单元测试。
 *
 * <p>全部使用真实配置文本与真实断言，不使用 mock。抽取器是纯函数，没有可替换的依赖。</p>
 *
 * <p>重点验证两件容易出错的事：<strong>节点路径必须和 {@code /config/tree} 返回的坐标一致</strong>
 * （不一致会导致前端点进站点后编辑到别的块），以及<strong>可疑配置要能被识别出来</strong>
 * （识别不出来等于白做）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@DisplayName("Nginx 站点抽取")
class NginxSiteExtractorTest {

    /** 抽取用配置文件路径 */
    private static final String FILE = "/etc/nginx/conf.d/site.conf";

    @Test
    @DisplayName("抽取静态站点的关键字段")
    void staticSite() {
        String src = """
                http {
                    server {
                        listen 80;
                        server_name www.example.com example.com;
                        root /var/www/html;
                        index index.html index.htm;
                        location / {
                            try_files $uri $uri/ /index.html;
                        }
                    }
                }
                """;
        List<NginxSiteVO> sites = NginxSiteExtractor.extract(new NginxParser(src).parse(), FILE);
        assertEquals(1, sites.size());

        NginxSiteVO s = sites.get(0);
        assertEquals(List.of("www.example.com", "example.com"), s.serverNames());
        assertEquals("www.example.com example.com", s.displayName());
        assertEquals(List.of(80), s.listenPorts());
        assertFalse(s.https());
        assertFalse(s.defaultServer());
        assertEquals("/var/www/html", s.rootDir());
        assertEquals(List.of("index.html", "index.htm"), s.indexFiles());
        assertEquals(1, s.locationCount());
        assertTrue(s.warnings().isEmpty(), "正常静态站点不应有告警: " + s.warnings());
    }

    @Test
    @DisplayName("识别 HTTPS 站点与证书")
    void httpsSite() {
        String src = """
                http {
                    server {
                        listen 443 ssl http2;
                        server_name a.com;
                        ssl_certificate /etc/nginx/cert/fullchain.pem;
                        ssl_certificate_key /etc/nginx/cert/privkey.pem;
                        root /srv/a;
                    }
                }
                """;
        NginxSiteVO s = only(src);
        assertTrue(s.https());
        assertTrue(s.hasCertificate());
        assertEquals(List.of(443), s.listenPorts());
        assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }

    @Test
    @DisplayName("开启 SSL 却没配证书要告警")
    void sslWithoutCertificate() {
        String src = """
                http {
                    server {
                        listen 443 ssl;
                        server_name a.com;
                    }
                }
                """;
        NginxSiteVO s = only(src);
        assertTrue(s.https());
        assertFalse(s.hasCertificate());
        assertTrue(joined(s).contains("ssl_certificate"), joined(s));
    }

    @Test
    @DisplayName("反向代理目标与 upstream 引用")
    void reverseProxy() {
        String src = """
                http {
                    upstream backend {
                        server 10.0.0.1:8080;
                    }
                    server {
                        listen 80;
                        server_name api.example.com;
                        location / {
                            proxy_pass http://backend;
                            proxy_set_header Host $host;
                        }
                    }
                    server {
                        listen 80;
                        server_name direct.example.com;
                        location / {
                            proxy_pass http://127.0.0.1:9000;
                            proxy_set_header Host $host;
                        }
                    }
                }
                """;
        List<NginxSiteVO> sites = NginxSiteExtractor.extract(new NginxParser(src).parse(), FILE);
        assertEquals(2, sites.size());

        NginxSiteVO viaUpstream = sites.get(0);
        assertEquals(List.of("http://backend"), viaUpstream.proxyTargets());
        assertEquals(List.of("backend"), viaUpstream.upstreamRefs(), "纯名字应识别为 upstream 引用");

        NginxSiteVO direct = sites.get(1);
        assertEquals(List.of("http://127.0.0.1:9000"), direct.proxyTargets());
        assertTrue(direct.upstreamRefs().isEmpty(), "带 IP 的目标不是 upstream 引用");
    }

    @Test
    @DisplayName("proxy_pass 缺少 Host 头要告警")
    void proxyWithoutHostHeader() {
        String src = """
                http {
                    server {
                        listen 80;
                        server_name api.example.com;
                        location / {
                            proxy_pass http://127.0.0.1:9000;
                        }
                    }
                }
                """;
        assertTrue(joined(only(src)).contains("proxy_set_header Host"), joined(only(src)));
    }

    @Test
    @DisplayName("识别兜底 server")
    void defaultServer() {
        String src = """
                http {
                    server {
                        listen 80 default_server;
                        server_name _;
                        return 404;
                    }
                    server {
                        listen 80;
                        server_name real.example.com;
                        root /var/www;
                    }
                }
                """;
        List<NginxSiteVO> sites = NginxSiteExtractor.extract(new NginxParser(src).parse(), FILE);
        assertTrue(sites.get(0).defaultServer());
        assertEquals("(未命名站点 / 兜底)", sites.get(0).displayName());
        assertFalse(sites.get(1).defaultServer());
    }

    @Test
    @DisplayName("没有 server_name 也视为兜底")
    void noServerNameIsDefault() {
        String src = "http {\n    server {\n        listen 80;\n        root /x;\n    }\n}\n";
        assertTrue(only(src).defaultServer());
    }

    @Test
    @DisplayName("既无 root 也无代理也无 location 要告警")
    void emptySiteWarns() {
        String src = "http {\n    server {\n        listen 80;\n        server_name a.com;\n    }\n}\n";
        assertTrue(joined(only(src)).contains("访问不到任何内容"), joined(only(src)));
    }

    @Test
    @DisplayName("没有 listen 指令要告警")
    void noListenWarns() {
        String src = "http {\n    server {\n        server_name a.com;\n        root /x;\n    }\n}\n";
        assertTrue(joined(only(src)).contains("不会生效"), joined(only(src)));
    }

    @Test
    @DisplayName("节点路径与语法树坐标一致，可直接用于定位编辑")
    void nodePathMatchesTreeCoordinates() {
        String src = """
                http {
                    upstream backend {
                        server 10.0.0.1:8080;
                    }
                    server {
                        listen 80;
                        server_name first.example.com;
                    }
                    server {
                        listen 80;
                        server_name second.example.com;
                    }
                }
                """;
        NginxNode root = new NginxParser(src).parse();
        List<NginxSiteVO> sites = NginxSiteExtractor.extract(root, FILE);
        assertEquals(2, sites.size());

        // 第一个 server 是根的第 2 个子节点（http 的第 1 个子节点）
        assertEquals("0/1", sites.get(0).nodePath());
        assertEquals("first.example.com", sites.get(0).displayName());

        NginxNode viaPath = NginxConfigModelSupport.resolve(root, sites.get(0).nodePath());
        assertEquals("server", viaPath.getName());
        assertNull(viaPath.getHeadArg(), "server 块没有头部参数，域名在 server_name 指令里");
        assertEquals(2, viaPath.children().size(), "listen + server_name 两条指令");

        NginxNode viaPath2 = NginxConfigModelSupport.resolve(root, sites.get(1).nodePath());
        assertEquals("server", viaPath2.getName());
        assertEquals(2, viaPath2.children().size());
        assertNotSame(viaPath, viaPath2, "两个站点的路径必须定位到不同的块");
        assertEquals("second.example.com", directiveOf(viaPath2, "server_name"));
        assertEquals("first.example.com", directiveOf(viaPath, "server_name"));
    }

    @Test
    @DisplayName("嵌套 location 内的指令计入所属站点")
    void nestedLocationCounted() {
        String src = """
                http {
                    server {
                        listen 80;
                        server_name a.com;
                        location / { proxy_pass http://backend; }
                        location /api/ { proxy_pass http://api; }
                        location ~ \\.php$ { fastcgi_pass unix:/run/php.sock; }
                    }
                }
                """;
        NginxSiteVO s = only(src);
        assertEquals(3, s.locationCount());
        assertEquals(List.of("http://backend", "http://api"), s.proxyTargets());
    }

    @Test
    @DisplayName("同一 listen 端口只出现一次")
    void listenPortDeduped() {
        String src = """
                http {
                    server {
                        listen 80;
                        listen 80 default_server;
                        server_name a.com;
                    }
                }
                """;
        assertEquals(List.of(80), only(src).listenPorts());
    }

    @Test
    @DisplayName("没有 server 块时返回空清单")
    void noServerBlocks() {
        String src = "http {\n    gzip on;\n    upstream backend {\n        server 10.0.0.1:80;\n    }\n}\n";
        assertTrue(NginxSiteExtractor.extract(new NginxParser(src).parse(), FILE).isEmpty());
    }

    @Test
    @DisplayName("未配置 root 时返回 null 而不是空串")
    void absentRootIsNull() {
        String src = "http {\n    server {\n        listen 80;\n        server_name a.com;\n    }\n}\n";
        assertNull(only(src).rootDir());
    }

    /**
     * 抽取唯一站点，不唯一则直接失败。
     *
     * @param src 配置文本
     * @return 站点视图
     */
    private static NginxSiteVO only(String src) {
        List<NginxSiteVO> sites = NginxSiteExtractor.extract(new NginxParser(src).parse(), FILE);
        assertEquals(1, sites.size(), "样本应恰好包含一个 server 块");
        return sites.get(0);
    }

    /**
     * 取块内第一条同名叶子指令的参数值。
     *
     * @param node 块节点
     * @param name 指令名
     * @return 参数值
     */
    private static String directiveOf(NginxNode node, String name) {
        for (NginxNode child : node.children()) {
            if (!child.isBlock() && name.equals(child.getName())) {
                return child.getValue();
            }
        }
        return null;
    }

    /**
     * 把告警拼成一段文本便于断言。
     *
     * @param site 站点视图
     * @return 告警文本
     */
    private static String joined(NginxSiteVO site) {
        return String.join(" | ", site.warnings());
    }
}
