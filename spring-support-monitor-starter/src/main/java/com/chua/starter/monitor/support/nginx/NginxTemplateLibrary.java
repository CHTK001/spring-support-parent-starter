package com.chua.starter.monitor.support.nginx;

import com.chua.starter.monitor.support.model.NginxTemplateVO;
import java.util.ArrayList;
import java.util.List;

/**
 * Nginx 配置模板库（面向新人的起步模板）。
 *
 * <p>每个模板都是一段<strong>可直接落盘运行</strong>的完整配置，并带中文注释说明
 * 每一段在做什么。模板里的 <code>{{占位符}}</code> 由前端向导收集用户输入后替换，
 * 因此不需要用户理解 nginx 指令也能得到一份正确配置。</p>
 *
 * <p>模板刻意只覆盖「一个需求对应一个 server」的常见形态，不追求覆盖 nginx 的全部能力；
 * 用户跑通之后可在标准模式里继续调整。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class NginxTemplateLibrary {

    /**
     * 工具类不允许实例化。
     */
    private NginxTemplateLibrary() {
    }

    /**
     * 取全部模板。
     *
     * @return 模板列表
     */
    public static List<NginxTemplateVO> all() {
        List<NginxTemplateVO> list = new ArrayList<>();
        list.add(staticSite());
        list.add(spa());
        list.add(reverseProxy());
        list.add(httpsSite());
        list.add(loadBalance());
        list.add(websocket());
        list.add(basicAuth());
        list.add(largeUpload());
        list.add(staticCache());
        list.add(customErrorPages());
        list.add(logFormat());
        return list;
    }

    /**
     * 按标识取模板。
     *
     * @param key 模板标识
     * @return 模板，未找到为 null
     */
    public static NginxTemplateVO find(String key) {
        if (key == null) {
            return null;
        }
        return all().stream().filter(t -> t.key().equals(key)).findFirst().orElse(null);
    }

    /**
     * 静态站点：把域名指向一个本地目录。
     *
     * @return 模板
     */
    private static NginxTemplateVO staticSite() {
        return new NginxTemplateVO("static-site", "静态站点",
                "把一个域名指向服务器上的文件夹，访问域名即列出/显示该目录里的网页",
                "公司官网、文档站、下载页等纯静态内容",
                "easy",
                """
                        # ============================================================
                        # 静态站点配置
                        # 作用：浏览器访问 http://{{DOMAIN}} 时，返回 {{ROOT}} 目录里的文件
                        # ============================================================

                        server {
                            # 监听的端口。80 是 HTTP；同一个 server 可以写多条 listen
                            listen 80;

                            # 站点域名。多个域名用空格隔开；写 _ 表示匹配任意域名
                            server_name {{DOMAIN}};

                            # 网站根目录。必须是绝对路径，末尾斜杠可加可不加
                            root {{ROOT}};
                            index index.html index.htm;

                            # 访问路径不存在时，尝试回退到首页（单页应用友好）
                            location / {
                                try_files $uri $uri/ /index.html;
                            }

                            # 不允许浏览服务器上的目录列表（安全建议，保留）
                            autoindex off;
                        }
                        """,
                List.of(
                        "把 {{DOMAIN}} 换成你的域名（如 www.example.com），{{ROOT}} 换成网页实际存放的绝对路径（如 /var/www/html）",
                        "上线前先把域名解析到本机，否则别人访问不到",
                        "如果提示 403，多半是 {{ROOT}} 目录 nginx 用户没有读权限，需要 chmod o+rx 或调整属主"),
                List.of("DOMAIN", "ROOT"));
    }

    /**
     * 单页应用：前端路由刷新不 404。
     *
     * @return 模板
     */
    private static NginxTemplateVO spa() {
        return new NginxTemplateVO("spa", "单页应用（SPA）",
                "把前端打包产物发布出去，并解决刷新子路由 404 的问题",
                "Vue / React 等history 模式的前端项目",
                "easy",
                """
                        # ============================================================
                        # 单页应用（SPA）配置
                        # 关键点：try_files 的最后一个参数是首页，
                        # 任何找不到的路径都回退到首页，由前端路由接手解析
                        # ============================================================

                        server {
                            listen 80;
                            server_name {{DOMAIN}};
                            root {{ROOT}};
                            index index.html;

                            location / {
                                # $uri 是浏览器请求的路径；$uri/ 把它当目录试一次；
                                # 最后 /index.html 是兜底
                                try_files $uri $uri/ /index.html;
                            }

                            # 接口请求单独走 location，避免被上面的回退规则吃掉
                            location {{API_PREFIX}} {
                                proxy_pass {{API_UPSTREAM}};
                                proxy_set_header Host $host;
                                proxy_set_header X-Real-IP $remote_addr;
                                proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                            }
                        }
                        """,
                List.of(
                        "{{API_PREFIX}} 是后端接口前缀（如 /api/），带结尾斜杠；{{API_UPSTREAM}} 是后端地址（如 http://127.0.0.1:8080）",
                        "proxy_set_header Host $host 很重要，否则后端拿到的域名会是 127.0.0.1",
                        "如果后端也部署在同机且用的是 unix socket，proxy_pass 写成 http://unix:/run/app.sock:"),
                List.of("DOMAIN", "ROOT", "API_PREFIX", "API_UPSTREAM"));
    }

    /**
     * 反向代理：把域名转发到后端服务。
     *
     * @return 模板
     */
    private static NginxTemplateVO reverseProxy() {
        return new NginxTemplateVO("reverse-proxy", "反向代理",
                "把外部请求转发到内部服务，对外只暴露一个域名",
                "后端服务不能直接暴露、或需要统一域名与 HTTPS 时",
                "easy",
                """
                        # ============================================================
                        # 反向代理配置
                        # 作用：访问 http://{{DOMAIN}}{{LOCATION}} 的请求，
                        #       全部转发给 {{UPSTREAM}}
                        # ============================================================

                        server {
                            listen 80;
                            server_name {{DOMAIN}};

                            location {{LOCATION}} {
                                # 转发目标。末尾带 / 时会剥掉 location 的前缀，
                                # 不带 / 则把完整路径原样转发给后端
                                proxy_pass {{UPSTREAM}};

                                # 后端要拿到真实域名与客户端 IP，必须显式传递
                                proxy_set_header Host $host;
                                proxy_set_header X-Real-IP $remote_addr;
                                proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                                proxy_set_header X-Forwarded-Proto $scheme;

                                # 等待后端响应的超时。跑批、长任务接口要调大
                                proxy_read_timeout {{TIMEOUT}};
                                proxy_connect_timeout 60s;

                                # 后端返回 502/503 时自动换一个后端重试（只有一个后端时无实际作用）
                                proxy_next_upstream error timeout http_502 http_503;
                            }
                        }
                        """,
                List.of(
                        "{{LOCATION}} 是路径前缀，如 / 、/api/。填 / 表示转发全部请求",
                        "{{UPSTREAM}} 是后端地址，如 http://127.0.0.1:3000",
                        "后端拿到的 IP 依赖 X-Forwarded-For，若后端要按 IP 鉴权必须让它信任这个头",
                        "上传大文件超时或报 413 时，见「大文件上传」模板"),
                List.of("DOMAIN", "LOCATION", "UPSTREAM", "TIMEOUT"));
    }

    /**
     * HTTPS 站点。
     *
     * @return 模板
     */
    private static NginxTemplateVO httpsSite() {
        return new NginxTemplateVO("https", "HTTPS 站点",
                "开启 HTTPS，并强制把 HTTP 跳转到 HTTPS",
                "正式对外的服务，需要浏览器不显示「不安全」标记",
                "normal",
                """
                        # ============================================================
                        # HTTPS 站点配置
                        # 前提：证书文件已存在 {{CERT_PATH}} 与 {{KEY_PATH}}
                        #       自签名证书浏览器会告警，正式站点请用正规证书
                        # ============================================================

                        # 80 端口只做一件事：把所有人导向 HTTPS
                        server {
                            listen 80;
                            server_name {{DOMAIN}};
                            # 301 是永久跳转。$host 是请求的域名，$request_uri 是完整路径
                            return 301 https://$host$request_uri;
                        }

                        server {
                            # ssl 表示该端口启用 HTTPS；http2 让同一端口同时支持 HTTP/2
                            listen 443 ssl;
                            server_name {{DOMAIN}};

                            # 证书与私钥路径
                            ssl_certificate {{CERT_PATH}};
                            ssl_certificate_key {{KEY_PATH}};

                            # 下面四行是现代浏览器的推荐套件与协议，去掉会触发安全扫描告警
                            ssl_protocols TLSv1.2 TLSv1.3;
                            ssl_prefer_server_ciphers off;
                            ssl_session_cache shared:SSL:10m;
                            ssl_session_timeout 1d;

                            ssl_certificate_cache max=2m;

                            root {{ROOT}};
                            index index.html;

                            location / {
                                try_files $uri $uri/ /index.html;
                            }
                        }

                        # 80 端口的 server 块也补一个，用于 ACME 证书自动续签的验证请求
                        server {
                            listen 80;
                            server_name {{DOMAIN}};
                            # 续签时 Let's Encrypt 会访问 http://域名/.well-known/acme-challenge/xxx
                            # 这个请求必须走 HTTP 且不被上面的 301 拦掉，因此单独开一个 server
                            location ^~ /.well-known/acme-challenge/ {
                                root {{WEBROOT}};
                            }

                            location / {
                                return 301 https://$host$request_uri;
                            }
                        }
                        """,
                List.of(
                        "证书免费申请可用 certbot 或阿里云 DV 证书；证书到期必须续签，否则浏览器直接报错",
                        "证书续签后需要 nginx -s reload 才会生效，可配合定时任务",
                        "若只想先跑通，把 listen 443 换成 80 并删掉证书相关行即可"),
                List.of("DOMAIN", "CERT_PATH", "KEY_PATH", "ROOT", "WEBROOT"));
    }

    /**
     * 负载均衡。
     *
     * @return 模板
     */
    private static NginxTemplateVO loadBalance() {
        return new NginxTemplateVO("load-balance", "负载均衡",
                "把请求分发给多台后端，支持按权重或最少连接分配",
                "多台后端机器需要统一入口分担流量",
                "normal",
                """
                        # ============================================================
                        # 负载均衡配置
                        # upstream 定义一组后端，proxy_pass 引用名字而不是写 IP
                        # ============================================================

                        # upstream 必须写在 http 块内，与 server 平级
                        upstream {{UPSTREAM_NAME}} {
                            # 最少连接：谁空闲就分给谁，适合处理耗时不均的后端
                            least_conn;

                            # 权重越大分到的请求越多。max_conns 限制单台并发
                            server {{BACKEND1}} weight=2;
                            server {{BACKEND2}} weight=1;
                            # 标记为备用机：只有正常机全挂了才启用
                            # server {{BACKEND3}} backup;

                            # 与后端保持的空闲长连接数，可降低握手开销
                            # 配合下面的 proxy_http_version 1.1 才有意义
                            keepalive 32;
                        }

                        server {
                            listen 80;
                            server_name {{DOMAIN}};

                            location / {
                                # 引用 upstream 名字，注意结尾不要再加 /
                                proxy_pass http://{{UPSTREAM_NAME}};

                                proxy_http_version 1.1;
                                proxy_set_header Connection "";
                                proxy_set_header Host $host;
                                proxy_set_header X-Real-IP $remote_addr;
                                proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

                                # 某台后端挂了自动换下一台，最多试 2 次
                                proxy_next_upstream error timeout http_502 http_503;
                                proxy_next_upstream_tries 2;
                            }
                        }
                        """,
                List.of(
                        "upstream 块必须与 server 平级（写在 http 里），不能写在 server 里",
                        "写 proxy_pass 时不要加路径，upstream 名字也不能带路径",
                        "本机自测可以配两个后端端口，或直接用 keepalived / consul 动态注册"),
                List.of("UPSTREAM_NAME", "BACKEND1", "BACKEND2", "DOMAIN"));
    }

    /**
     * WebSocket 代理。
     *
     * @return 模板
     */
    private static NginxTemplateVO websocket() {
        return new NginxTemplateVO("websocket", "WebSocket 代理",
                "把 WebSocket 长连接正确地转发到后端",
                "IM、协同编辑、实时推送等需要长连接的功能",
                "normal",
                """
                        # ============================================================
                        # WebSocket 代理配置
                        # 关键点：Upgrade / Connection 两个请求头必须显式透传，
                        #          且读超时必须调长，否则连接会被 nginx 掐断
                        # ============================================================

                        map $http_upgrade $connection_upgrade {
                            # 客户端带 Upgrade 头就回 upgrade，否则给空值（不能写 close，
                            # 否则普通 HTTP 请求会被当成要关闭连接）
                            default upgrade;
                            ''      close;
                        }

                        server {
                            listen 80;
                            server_name {{DOMAIN}};

                            location {{LOCATION}} {
                                proxy_pass {{UPSTREAM}};

                                proxy_http_version 1.1;
                                proxy_set_header Upgrade $http_upgrade;
                                proxy_set_header Connection $connection_upgrade;

                                proxy_set_header Host $host;
                                proxy_set_header X-Real-IP $remote_addr;

                                # 长连接的关键：默认 60s 会把空闲的 WebSocket 断开
                                proxy_read_timeout {{TIMEOUT}};
                                proxy_send_timeout {{TIMEOUT}};
                            }
                        }
                        """,
                List.of(
                        "map 块必须与 server 平级，且整个 nginx 里只能有一个 $connection_upgrade",
                        "没配 map 而直接写 Connection \"upgrade\"，会让普通 HTTP 请求也带上 upgrade 而失败",
                        "代理后前端连不上，先看浏览器 Network 里握手是不是 101，不是就是头没透传对"),
                List.of("DOMAIN", "LOCATION", "UPSTREAM", "TIMEOUT"));
    }

    /**
     * 基础认证。
     *
     * @return 模板
     */
    private static NginxTemplateVO basicAuth() {
        return new NginxTemplateVO("basic-auth", "密码保护",
                "给某个路径加一层用户名密码，挡住未授权访问",
                "内部工具、管理后台临时加访问密码",
                "normal",
                """
                        # ============================================================
                        # 密码保护配置
                        # 密码文件由 htpasswd 工具生成，一行一个 用户名:哈希
                        # 生成命令：htpasswd -c /etc/nginx/.htpasswd admin
                        # ============================================================

                        server {
                            listen 80;
                            server_name {{DOMAIN}};

                            location / {
                                proxy_pass {{UPSTREAM}};

                                proxy_set_header Host $host;
                                proxy_set_header X-Real-IP $remote_addr;

                                # auth_basic 的参数只是弹窗标题，随便起
                                auth_basic "Restricted";
                                # 密码文件路径
                                auth_basic_user_file {{PASSWD_FILE}};
                            }
                        }
                        """,
                List.of(
                        "htpasswd 生成的第二遍不加 -c 才是追加用户，-c 会覆盖整个文件",
                        "密码是哈希存储的，明文不会留在文件里，忘了只能重置不能找回",
                        "浏览器会缓存凭据，同一浏览器切换账号要用无痕窗口"),
                List.of("DOMAIN", "UPSTREAM", "PASSWD_FILE"));
    }

    /**
     * 大文件上传。
     *
     * @return 模板
     */
    private static NginxTemplateVO largeUpload() {
        return new NginxTemplateVO("large-upload", "大文件上传",
                "放宽上传体积限制，并避免上传时请求体被反复写盘",
                "上传视频、安装包、大数据文件等，默认 1m 限制远远不够",
                "easy",
                """
                        # ============================================================
                        # 大文件上传配置
                        # 默认 client_max_body_size 只有 1m，超了直接返回 413
                        # ============================================================

                        server {
                            listen 80;
                            server_name {{DOMAIN}};
                            root {{ROOT}};
                            index index.html;

                            client_max_body_size {{MAX_SIZE}};

                            location / {
                                try_files $uri $uri/ /index.html;
                            }

                            # 上传专用路径
                            location {{UPLOAD_PATH}} {
                                # 直接透传给后端，不落盘，速度快很多
                                proxy_pass {{UPSTREAM}};

                                proxy_set_header Host $host;
                                proxy_set_header X-Real-IP $remote_addr;

                                # 不把上传内容先缓存到临时文件，避免大文件写两遍盘
                                proxy_request_buffering off;

                                # 传大文件耗时可能很长，超时必须放宽
                                proxy_read_timeout 600s;
                                proxy_send_timeout 600s;
                                # 转发大请求体时，nginx 自身读请求体也要放宽
                                client_body_timeout 600s;
                            }
                        }
                        """,
                List.of(
                        "改了 client_max_body_size 仍报 413，检查是否被更内层的 location 覆盖了",
                        "如果上传路径需要 PHP 之类先处理文件，就不能开 proxy_request_buffering off",
                        "上传超大文件建议直接用对象存储直传，nginx 只签发凭证不中转"),
                List.of("DOMAIN", "ROOT", "MAX_SIZE", "UPLOAD_PATH", "UPSTREAM"));
    }

    /**
     * 静态资源缓存。
     *
     * @return 模板
     */
    private static NginxTemplateVO staticCache() {
        return new NginxTemplateVO("static-cache", "静态资源缓存",
                "给图片、JS、CSS 加长缓存，同时让首页不缓存",
                "减少重复下载、降低首屏时间",
                "easy",
                """
                        # ============================================================
                        # 静态资源缓存配置
                        # 思路：带哈希的静态资源可以永久缓存；入口 html 绝不能缓存，
                        #      否则用户会一直拿到旧版本
                        # ============================================================

                        server {
                            listen 80;
                            server_name {{DOMAIN}};
                            root {{ROOT}};
                            index index.html;

                            # 首页不缓存
                            location = /index.html {
                                add_header Cache-Control "no-cache, no-store, must-revalidate";
                            }

                            # 带内容哈希的构建产物，可以永久缓存
                            location ^~ /assets/ {
                                expires 1y;
                                add_header Cache-Control "public, immutable";
                            }

                            # 常见静态资源类型
                            location ~* \\.(jpg|jpeg|png|gif|ico|webp|svg|woff2?|ttf|eot)$ {
                                expires 30d;
                                add_header Cache-Control "public";
                            }

                            location / {
                                try_files $uri $uri/ /index.html;
                            }

                            # 开启 gzip，文本类资源体积可降60% 以上
                            gzip on;
                            gzip_min_length 1k;
                            gzip_comp_level 6;
                            gzip_types text/plain text/css application/javascript application/json image/svg+xml;
                        }
                        """,
                List.of(
                        "^~ 表示「非正则优先」，用了它本段不会被下面的正则 location 抢先匹配",
                        "gzip 对图片和视频无效（本来已压缩），别浪费 CPU",
                        "改了前端代码没生效，先确认浏览器是不是吃了缓存，可用 Ctrl+F5 强制刷新验证"),
                List.of("DOMAIN", "ROOT"));
    }

    /**
     * 自定义错误页。
     *
     * @return 模板
     */
    private static NginxTemplateVO customErrorPages() {
        return new NginxTemplateVO("error-pages", "自定义错误页",
                "404 / 502 / 503 显示自己的页面，而不是 nginx 默认样式",
                "提升站点观感；502 页面还能顺手告知运维",
                "easy",
                """
                        # ============================================================
                        # 自定义错误页配置
                        # ============================================================

                        server {
                            listen 80;
                            server_name {{DOMAIN}};
                            root {{ROOT}};

                            # 语法：error_page 状态码 路径;
                            # =404 表示「状态码保持 404，但内容用这个文件」
                            # 不写等号会把状态码也改成 200，SEO 上不推荐
                            error_page 404 /404.html;
                            error_page 500 502 503 504 /50x.html;

                            # 拦截上游返回的错误，配合上面的 error_page 一起用
                            proxy_intercept_errors on;

                            location / {
                                try_files $uri $uri/ /index.html;
                            }

                            # 错误页本身要能访问到，但不能被外部直接当普通页面访问
                            location = /404.html {
                                internal;
                            }
                            location = /50x.html {
                                internal;
                            }
                        }
                        """,
                List.of(
                        "错误页文件要真实存在，否则 nginx 会报「open() 失败」并在错误日志里留记录",
                        "用 =404 这种带等号写法保留原始状态码，对 SEO 更友好",
                        "502 说明后端挂了，可在 50x.html 里显示维护公告"),
                List.of("DOMAIN", "ROOT"));
    }

    /**
     * 日志格式。
     *
     * @return 模板
     */
    private static NginxTemplateVO logFormat() {
        return new NginxTemplateVO("log-format", "日志格式",
                "定义更详细的访问日志，便于排查是谁访问了什么、耗时多久",
                "需要按 IP 统计、排查慢接口时",
                "normal",
                """
                        # ============================================================
                        # 自定义访问日志格式
                        # log_format 必须写在 http 块内，与 server 平级
                        # ============================================================

                        http {
                            # 名字随便起，main 是 nginx 默认名，重新定义会覆盖它
                            log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                                        '$status $body_bytes_sent "$http_referer" '
                                        '"$http_user_agent" rt=$request_time urt=$upstream_response_time';

                            # access_log 引用格式名
                            access_log /var/log/nginx/access.log main;

                            server {
                                listen 80;
                                server_name {{DOMAIN}};
                                root {{ROOT}};

                                # 这台机器单独用另一种日志（含 upstream 信息），排查代理问题用
                                access_log /var/log/nginx/{{DOMAIN}}.access.log main;
                                error_log  /var/log/nginx/{{DOMAIN}}.error.log warn;

                                location / {
                                    proxy_pass {{UPSTREAM}};
                                    proxy_set_header Host $host;
                                    proxy_set_header X-Real-IP $remote_addr;
                                    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                                }
                            }
                        }
                        """,
                List.of(
                        "rt 是 nginx 自己处理耗时，urt 是后端响应耗时，urt 为空说明没走代理",
                        "变量用 $开头，写错了 nginx -t 会直接报错，提示「unknown variable」",
                        "线上日志量大时建议关闭或调低 error_log 级别（warn / error）"),
                List.of("DOMAIN", "ROOT", "UPSTREAM"));
    }
}
