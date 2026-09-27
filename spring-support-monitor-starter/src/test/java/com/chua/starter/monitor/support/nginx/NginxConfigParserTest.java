package com.chua.starter.monitor.support.nginx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chua.starter.monitor.support.model.dto.NginxPatchDTO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Nginx 配置解析器与渲染器的真实单元测试。
 *
 * <p>本测试全部使用真实输入与真实断言，不使用任何 mock：解析器与渲染器都是纯函数，
 * 没有可替换的依赖，mock 反而会让测试失去意义。</p>
 *
 * <p>测试分两类，缺一不可：</p>
 * <ul>
 *   <li><strong>往返一致</strong>：未改动时 {@code render(parse(src)) == src}。
 *       它保证注释、空行、缩进、引号、跨行指令都不会在「读进来再存回去」的过程中丢失。</li>
 *   <li><strong>结构断言</strong>：校验指令名与参数被正确拆分。
 *       这一类<strong>不能被往返测试替代</strong>——历史上就出现过词法器没在空白处断词、
 *       把 {@code root /old;} 读成指令名 {@code root /old} 的缺陷，而往返测试因为只是原样
 *       回放文本完全查不出来，只有结构断言能暴露。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@DisplayName("Nginx 配置解析与渲染")
class NginxConfigParserTest {

    /**
     * 往返一致：未改动时渲染结果必须与原文逐字节相同。
     */
    @Nested
    @DisplayName("往返无损")
    class RoundTrip {

        @Test
        @DisplayName("最简 http 结构")
        void minimalHttp() {
            assertRoundTrip("events {}\nhttp {\n    server {\n        listen 80;\n        server_name a.com;\n    }\n}\n");
        }

        @Test
        @DisplayName("保留注释、空行、行尾注释与额外空格")
        void commentsAndBlankLines() {
            assertRoundTrip("""
                    # 顶部注释

                    user  www-data;

                    http {

                        # 站点 A
                        server {
                            listen       80;
                            server_name  a.com b.com;   # 行尾注释

                            location / {
                                root  /var/www;
                                index index.html;
                            }
                        }
                    }
                    """);
        }

        @Test
        @DisplayName("保留引号与 nginx 变量")
        void quotesAndVariables() {
            assertRoundTrip("""
                    http {
                        server {
                            location /a {
                                return 301 https://$host$request_uri;
                            }
                            proxy_set_header X-Real-IP "$remote_addr";
                            add_header X-A "a b";
                        }
                    }
                    """);
        }

        @Test
        @DisplayName("保留正则 location 中的特殊字符")
        void regexLocation() {
            assertRoundTrip("""
                    http {
                        server {
                            location ~* \\.(gif|jpg|png)$ {
                                expires 30d;
                            }
                            location ~ ^/api/ {
                                proxy_pass http://backend;
                            }
                        }
                    }
                    """);
        }

        @Test
        @DisplayName("保留 map 与 upstream 块")
        void mapAndUpstream() {
            assertRoundTrip("""
                    http {
                        map $http_host $bk {
                            default  a;
                            a.com    b;
                        }
                        upstream backend {
                            least_conn;
                            server 10.0.0.1:8080 weight=2;
                            server 10.0.0.2:8080;
                            keepalive 32;
                        }
                    }
                    """);
        }

        @Test
        @DisplayName("保留跨行指令")
        void multiLineDirective() {
            assertRoundTrip("""
                    http {
                        log_format  main  '$remote_addr - $remote_user '
                                      '"$request" $status $body_bytes_sent';
                        server {
                            access_log /var/log/nginx/access.log main;
                        }
                    }
                    """);
        }

        @Test
        @DisplayName("保留 if 块与花括号变量")
        void ifBlockAndBraceVariable() {
            assertRoundTrip("""
                    http {
                        server {
                            if ($http_user_agent ~* "MSIE") {
                                rewrite ^(.*)$ /msie/$1 break;
                            }
                            set $x "${a}b";
                        }
                    }
                    """);
        }

        @Test
        @DisplayName("保留单行多语句写法")
        void singleLineMultipleStatements() {
            assertRoundTrip("http { server { listen 80; root /x; } }\n");
        }

        @Test
        @DisplayName("文件末尾无换行也不改变内容")
        void noTrailingNewline() {
            assertRoundTrip("http {\n    server {\n        listen 80;\n    }\n}");
        }

        @Test
        @DisplayName("空文件与只有注释的文件")
        void emptyAndCommentOnly() {
            assertRoundTrip("");
            assertRoundTrip("# 只有一行注释\n");
        }

        @Test
        @DisplayName("枚举型参数不被误拆")
        void enumArgument() {
            assertRoundTrip("http {\n    server {\n        if_modified_since exact;\n    }\n}\n");
        }
    }

    /**
     * 结构断言：校验词法切分与路径标识定位。
     */
    @Nested
    @DisplayName("结构切分")
    class Structure {

        /** 结构断言用的样本配置 */
        private static final String SAMPLE =
                "http {\n"
                        + "    server {\n"
                        + "        location /a {\n"
                        + "            root /old;\n"
                        + "        }\n"
                        + "        location ~* \\.(gif|jpg)$ {\n"
                        + "            expires 30d;\n"
                        + "        }\n"
                        + "        proxy_set_header X-A \"a b\";\n"
                        + "    }\n"
                        + "}\n";

        @Test
        @DisplayName("区块名与区块头部参数正确拆分")
        void blockNameAndHeadArg() {
            NginxNode root = new NginxParser(SAMPLE).parse();

            NginxNode http = NginxConfigModelSupport.resolve(root, "0");
            assertEquals("http", http.getName());
            assertTrue(http.isBlock());
            assertNull(http.getHeadArg(), "无参数区块的 headArg 应为 null");

            NginxNode server = NginxConfigModelSupport.resolve(root, "0/0");
            assertEquals("server", server.getName());
            assertTrue(server.isBlock());

            NginxNode location = NginxConfigModelSupport.resolve(root, "0/0/0");
            assertEquals("location", location.getName());
            assertEquals("/a", location.getHeadArg());

            NginxNode regex = NginxConfigModelSupport.resolve(root, "0/0/1");
            assertEquals("location", regex.getName());
            assertEquals("~* \\.(gif|jpg)$", regex.getHeadArg());
        }

        @Test
        @DisplayName("指令名与参数被正确分离")
        void directiveNameAndValue() {
            NginxNode root = new NginxParser(SAMPLE).parse();

            NginxNode rootDirective = NginxConfigModelSupport.resolve(root, "0/0/0/0");
            assertEquals("root", rootDirective.getName());
            assertEquals("/old", rootDirective.getValue());
            assertFalse(rootDirective.isBlock());

            NginxNode header = NginxConfigModelSupport.resolve(root, "0/0/2");
            assertEquals("proxy_set_header", header.getName());
            assertEquals("X-A \"a b\"", header.getValue(), "带引号的参数应保留引号原文");

            NginxNode expires = NginxConfigModelSupport.resolve(root, "0/0/1/0");
            assertEquals("expires", expires.getName());
            assertEquals("30d", expires.getValue());
        }

        @Test
        @DisplayName("修改一条指令后，其余内容逐字节不变")
        void singleChangeKeepsRestByteIdentical() {
            NginxNode root = new NginxParser(SAMPLE).parse();
            List<NginxPatchDTO> patches =
                    List.of(new NginxPatchDTO("0/0/0/0", "UPDATE", null, null, "/new", null));
            List<String> warnings = NginxConfigModelSupport.applyPatches(root, patches);

            assertTrue(warnings.isEmpty(), "不应产生告警: " + warnings);
            String rendered = NginxRenderer.render(root, SAMPLE);
            assertEquals(SAMPLE.replace("root /old;", "root /new;"), rendered);
        }

        @Test
        @DisplayName("同节点的多次修改只保留最后一次")
        void repeatedPatchKeepsLast() {
            NginxNode root = new NginxParser(SAMPLE).parse();
            NginxConfigModelSupport.applyPatches(root, List.of(
                    new NginxPatchDTO("0/0/0/0", "UPDATE", null, null, "/v2", null),
                    new NginxPatchDTO("0/0/0/0", "UPDATE", null, null, "/v3", null)));
            assertTrue(NginxRenderer.render(root, SAMPLE).contains("root /v3;"));
            assertFalse(NginxRenderer.render(root, SAMPLE).contains("root /v2;"));
        }

        @Test
        @DisplayName("新增区块后原有内容保留")
        void addBlockKeepsRest() {
            NginxNode root = new NginxParser(SAMPLE).parse();
            NginxConfigModelSupport.applyPatches(root,
                    List.of(new NginxPatchDTO("0/0", "ADD", "location", "/new", null, null)));
            String rendered = NginxRenderer.render(root, SAMPLE);
            assertTrue(rendered.contains("location /new {"), rendered);
            assertTrue(rendered.contains("root /old;"), rendered);
        }

        @Test
        @DisplayName("删除区块只影响目标")
        void removeBlockAffectsOnlyTarget() {
            NginxNode root = new NginxParser(SAMPLE).parse();
            NginxConfigModelSupport.applyPatches(root,
                    List.of(new NginxPatchDTO("0/0/0", "REMOVE", null, null, null, null)));
            String rendered = NginxRenderer.render(root, SAMPLE);
            assertFalse(rendered.contains("/old"), rendered);
            assertTrue(rendered.contains("expires 30d;"), rendered);
        }

        @Test
        @DisplayName("非法节点标识不抛异常，只记录告警")
        void invalidNodeIdProducesWarning() {
            NginxNode root = new NginxParser(SAMPLE).parse();
            List<String> warnings = NginxConfigModelSupport.applyPatches(root, List.of(
                    new NginxPatchDTO("9/9/9", "UPDATE", null, null, "/x", null)));
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("找不到节点"), warnings.get(0));
        }
    }

    /**
     * 容错：残缺配置不应导致解析崩溃。
     */
    @Nested
    @DisplayName("容错")
    class FaultTolerance {

        @Test
        @DisplayName("缺少右花括号时自动闭合并给出告警")
        void missingClosingBrace() {
            String src = "http {\n    server {\n        listen 80;\n";
            NginxParser parser = new NginxParser(src);
            NginxNode root = parser.parse();

            assertFalse(parser.warnings().isEmpty(), "应给出缺少右花括号的告警");
            String rendered = NginxRenderer.render(root, src);
            assertTrue(rendered.contains("listen 80;"), rendered);
        }

        @Test
        @DisplayName("多余的右花括号被忽略并记录告警")
        void extraClosingBrace() {
            NginxParser parser = new NginxParser("http {\n}\n}\n");
            parser.parse();
            assertFalse(parser.warnings().isEmpty());
        }

        @Test
        @DisplayName("缺少分号的指令被记录但不丢失")
        void missingSemicolon() {
            String src = "http {\n    server {\n        listen 80\n    }\n}\n";
            NginxParser parser = new NginxParser(src);
            parser.parse();
            assertFalse(parser.warnings().isEmpty());
            assertTrue(NginxRenderer.render(parser.parse(), src).contains("listen 80"));
        }

        @Test
        @DisplayName("null 输入按空文本处理")
        void nullInput() {
            NginxNode root = new NginxParser(null).parse();
            assertNotNull(root);
            assertEquals("", NginxRenderer.render(root, ""));
        }
    }

    /**
     * 统计信息供 UI 展示，必须与实际结构一致。
     */
    @Nested
    @DisplayName("统计")
    class Statistics {

        @Test
        @DisplayName("server 块计数正确")
        void serverCountIsCorrect() {
            String src = """
                    http {
                        server {
                            server_name a.com;
                            location / { root /a; }
                            location /b { root /b; }
                        }
                        server {
                            server_name b.com;
                            location / { root /c; }
                        }
                        upstream backend {
                            server 10.0.0.1:80;
                        }
                    }
                    """;
            NginxParser parser = new NginxParser(src);
            NginxNode root = parser.parse();
            var vo = NginxConfigModelSupport.toParseVo(root, "/etc/nginx/nginx.conf", src, parser.warnings());

            assertEquals(2, vo.serverCount());
            assertEquals(3, vo.locationCount());
            assertEquals(1, vo.upstreamCount());
        }

        @Test
        @DisplayName("未登记元数据的指令被单独计数")
        void unknownDirectiveCounted() {
            String src = "http {\n    third_party_directive on;\n    gzip on;\n}\n";
            NginxParser parser = new NginxParser(src);
            NginxNode root = parser.parse();
            var vo = NginxConfigModelSupport.toParseVo(root, "/etc/nginx/nginx.conf", src, parser.warnings());

            assertEquals(1, vo.unknownCount(), "只有 third_party_directive 未登记");
        }
    }

    /**
     * 区块名判定：决定表单新增时生成块还是指令。
     */
    @Nested
    @DisplayName("区块名判定")
    class BlockName {

        @Test
        @DisplayName("已知区块名判定为块")
        void knownBlockNames() {
            assertTrue(NginxBlockRegistry.isBlockName("location"));
            assertTrue(NginxBlockRegistry.isBlockName("upstream"));
            assertTrue(NginxBlockRegistry.isBlockName("server"));
            assertTrue(NginxBlockRegistry.isBlockName("if"));
            assertTrue(NginxBlockRegistry.isBlockName("location @fallback"), "命名 location 也是块");
        }

        @Test
        @DisplayName("指令名不得被误判为块")
        void directivesAreNotBlocks() {
            // proxy_pass / stub_status / empty_gif 历史上曾被误放进区块名单，
            // 会导致表单新增时生成 location 套 proxy_pass 的畸形结构
            assertFalse(NginxBlockRegistry.isBlockName("proxy_pass"));
            assertFalse(NginxBlockRegistry.isBlockName("stub_status"));
            assertFalse(NginxBlockRegistry.isBlockName("empty_gif"));
            assertFalse(NginxBlockRegistry.isBlockName("root"));
        }

        @Test
        @DisplayName("空名与 null 判定为非块")
        void blankIsNotBlock() {
            assertFalse(NginxBlockRegistry.isBlockName(null));
            assertFalse(NginxBlockRegistry.isBlockName(""));
            assertFalse(NginxBlockRegistry.isBlockName("   "));
        }
    }

    /**
     * 差异行数用于给用户一个改动量感知。
     */
    @Nested
    @DisplayName("差异统计")
    class Diff {

        @Test
        @DisplayName("内容相同时差异为零")
        void sameContentZeroDiff() {
            String src = "http {\n    server {\n        listen 80;\n    }\n}\n";
            assertEquals(0, NginxConfigModelSupport.diffLineCount(src, src));
        }

        @Test
        @DisplayName("改动一行时差异非零")
        void oneLineChanged() {
            String before = "http {\n    server {\n        root /a;\n    }\n}\n";
            String after = "http {\n    server {\n        root /b;\n    }\n}\n";
            assertEquals(2, NginxConfigModelSupport.diffLineCount(before, after));
        }
    }

    /**
     * 断言解析再渲染后与原文逐字节一致。
     *
     * @param src 原始配置文本
     */
    private static void assertRoundTrip(String src) {
        NginxParser parser = new NginxParser(src);
        NginxNode root = parser.parse();
        String rendered = NginxRenderer.render(root, src);
        assertEquals(src, rendered, "未改动时渲染结果必须与原文逐字节一致");
    }
}
