package com.chua.starter.datasourcehub.support.nginx;

import com.chua.starter.datasourcehub.support.model.NginxSiteVO;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从配置语法树里抽出「站点」清单。
 *
 * <p>面向的使用场景是「按站点组织配置」：运维真正关心的是「我那几个域名分别配了什么」，
 * 而不是 {@code conf.d} 下有哪些文件。这个类把所有 {@code server} 块抽平，
 * 并为每个站点算出它在自己文件语法树里的路径标识，使前端能只针对这一个块做表单编辑。</p>
 *
 * <p>只做<strong>读取与归纳</strong>，不修改任何节点。发现的可疑之处以只读提示返回，
 * 由使用者决定怎么处理，不代替人做判断。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class NginxSiteExtractor {

    /**
     * listen 参数里的端口
     */
    private static final Pattern LISTEN_PORT = Pattern.compile("(\\d{2,5})");

    /**
     * proxy_pass 目标里的主机部分
     */
    private static final Pattern PROXY_HOST = Pattern.compile("^(?:https?://)?([^/:;\\s]+)");

    /**
     * 工具类不允许实例化。
     */
    private NginxSiteExtractor() {
    }

    /**
     * 抽取语法树里所有的 server 块。
     *
     * @param root 语法树根
     * @param file 所属配置文件绝对路径
     * @return 站点清单，按出现顺序
     */
    public static List<NginxSiteVO> extract(NginxNode root, String file) {
        List<NginxSiteVO> out = new ArrayList<>();
        walk(root, "", out, file);
        return out;
    }

    /**
     * 递归查找 server 块。
     *
     * @param node     当前节点
     * @param path     当前节点的路径标识
     * @param out      输出
     * @param file     所属文件路径
     */
    private static void walk(NginxNode node, String path, List<NginxSiteVO> out, String file) {
        List<NginxNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            NginxNode child = children.get(i);
            String childPath = path.isEmpty() ? String.valueOf(i) : path + "/" + i;
            if (child.isBlock() && "server".equals(child.getName())) {
                out.add(toSite(child, childPath, file));
            }
            walk(child, childPath, out, file);
        }
    }

    /**
     * 把一个 server 块归纳成站点视图。
     *
     * @param server server 块
     * @param path   路径标识
     * @param file   所属文件路径
     * @return 站点视图
     */
    private static NginxSiteVO toSite(NginxNode server, String path, String file) {
        List<String> serverNames = splitWords(directiveValue(server, "server_name"));
        List<Integer> ports = new ArrayList<>();
        List<String> indexFiles = new ArrayList<>();
        List<String> proxyTargets = new ArrayList<>();
        boolean https = false;
        boolean hasCertificate = false;
        boolean hasHostHeader = false;
        String rootDir = null;

        // 先把整棵树里的叶子指令摊平，再逐条归类。
        // 不用 lambda 是因为要归类的结果都是被反复修改的局部变量，无法作为 effectively final 被捕获。
        List<Directive> flat = new ArrayList<>();
        collectDirectives(server, flat);
        for (Directive d : flat) {
            String name = d.name();
            String value = d.value();
            if ("listen".equals(name)) {
                Matcher m = LISTEN_PORT.matcher(value);
                if (m.find()) {
                    try {
                        int port = Integer.parseInt(m.group(1));
                        if (port > 0 && port < 65536 && !ports.contains(port)) {
                            ports.add(port);
                        }
                    } catch (NumberFormatException ignored) {
                        // 非数字监听（如 unix:）直接忽略
                    }
                }
                if (value.contains("ssl")) {
                    https = true;
                }
            } else if ("ssl_certificate".equals(name) || "ssl_certificate_key".equals(name)) {
                hasCertificate = true;
            } else if ("root".equals(name) && rootDir == null) {
                rootDir = value;
            } else if ("index".equals(name)) {
                for (String f : splitWords(value)) {
                    if (!indexFiles.contains(f)) {
                        indexFiles.add(f);
                    }
                }
            } else if ("proxy_pass".equals(name)) {
                if (!value.isEmpty() && !proxyTargets.contains(value)) {
                    proxyTargets.add(value);
                }
            } else if ("proxy_set_header".equals(name)
                    && value.regionMatches(true, 0, "Host", 0, 4)) {
                hasHostHeader = true;
            }
        }
        // location 是块而不是叶子指令，必须单独遍历子节点统计，
        // 否则摊平指令列表里根本不会出现 location，计数恒为 0
        int locationCount = countLocationBlocks(server);
        boolean hasProxy = !proxyTargets.isEmpty();

        boolean defaultServer = serverNames.isEmpty()
                || (serverNames.size() == 1 && "_".equals(serverNames.get(0)));

        List<String> upstreamRefs = upstreamRefs(proxyTargets);
        List<String> warnings = new ArrayList<>();
        if (!defaultServer && ports.isEmpty()) {
            warnings.add("没有 listen 指令，该 server 块不会生效");
        }
        if (https && !hasCertificate) {
            warnings.add("listen 带了 ssl 但没有配置 ssl_certificate，nginx 启动会直接失败");
        }
        if (hasProxy && !hasHostHeader) {
            warnings.add("用了 proxy_pass 但没有 proxy_set_header Host，后端拿到的会是本机域名");
        }
        if (!defaultServer && rootDir == null && !hasProxy && locationCount == 0) {
            warnings.add("既没有 root 也没有 proxy_pass，这个站点访问不到任何内容");
        }

        String displayName = defaultServer ? "(未命名站点 / 兜底)" : String.join(" ", serverNames);
        return new NginxSiteVO(path, file, serverNames, displayName, ports, https, defaultServer,
                rootDir, indexFiles, proxyTargets, locationCount, upstreamRefs, hasCertificate, warnings);
    }

    /**
     * 从 proxy_pass 目标里挑出 upstream 名字。
     *
     * <p>判据：目标是纯名字（不含点号与端口），就认为它引用的是某个 upstream；
     * 含 IP、域名或端口的直接指向后端，不算 upstream 引用。</p>
     *
     * @param proxyTargets proxy_pass 目标
     * @return upstream 名字列表
     */
    private static List<String> upstreamRefs(List<String> proxyTargets) {
        Set<String> refs = new LinkedHashSet<>();
        for (String target : proxyTargets) {
            Matcher m = PROXY_HOST.matcher(target);
            if (!m.find()) {
                continue;
            }
            String host = m.group(1);
            if (host.isEmpty() || host.indexOf('.') >= 0 || host.indexOf(':') >= 0) {
                continue;
            }
            refs.add(host);
        }
        return new ArrayList<>(refs);
    }

    /**
     * 取本层级第一条同名指令的参数值。
     *
     * @param node 父节点
     * @param name 指令名
     * @return 参数值，没有则 null
     */
    private static String directiveValue(NginxNode node, String name) {
        for (NginxNode child : node.children()) {
            if (!child.isBlock() && name.equals(child.getName())) {
                return child.getValue();
            }
        }
        return null;
    }

    /**
     * 深度优先摊平所有叶子指令（含嵌套块内）。
     *
     * @param node 起始节点
     * @param out  输出
     */
    private static void collectDirectives(NginxNode node, List<Directive> out) {
        for (NginxNode child : node.children()) {
            if (child.isBlock()) {
                if ("server".equals(child.getName())) {
                    // 不该出现嵌套 server，跳过以免把子站点的配置算到父站点上
                    continue;
                }
                collectDirectives(child, out);
            } else {
                out.add(new Directive(child.getName(), child.getValue()));
            }
        }
    }

    /**
     * 统计子树里的 location 块数量。
     *
     * <p>只数 location 本身，不递归数它内部的嵌套 location——那属于子路径的语义，
     * 混在总数里会让人以为站点有那么多规则。</p>
     *
     * @param node 起始节点
     * @return location 块数量
     */
    private static int countLocationBlocks(NginxNode node) {
        int count = 0;
        for (NginxNode child : node.children()) {
            if (!child.isBlock()) {
                continue;
            }
            if ("location".equals(child.getName())) {
                count++;
            } else if (!"server".equals(child.getName())) {
                count += countLocationBlocks(child);
            }
        }
        return count;
    }

    /**
     * 一条叶子指令。
     *
     * @param name  指令名
     * @param value 参数值
     */
    private record Directive(String name, String value) {
    }

    /**
     * 按空白切分并去掉空项。
     *
     * @param value 原始值，可为 null
     * @return 切分结果
     */
    private static List<String> splitWords(String value) {
        List<String> out = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return out;
        }
        for (String token : value.trim().split("\\s+")) {
            String t = token.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
