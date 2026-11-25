package com.chua.starter.swagger.support;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Swagger / Knife4j 基础配置（前缀 {@code plugin.swagger}）。
 *
 * <p>统一管理文档总开关、Basic 认证、文档信息与分组列表；由
 * {@link com.chua.starter.swagger.support.environment.SwaggerEnvironmentPostProcessor}
 * 在启动早期桥接为 {@code knife4j.*} / {@code springdoc.*} 属性
 * （属性源 {@code knife4j-extension}，最低优先级，可被应用配置覆盖）。</p>
 *
 * <p>完整 YAML 配置示例（注释含各键含义与默认值）：</p>
 * <pre>
 * plugin:
 *   swagger:
 *     enable: true                  # 总开关；字段默认 false，未配置时按 true 处理（matchIfMissing=true）
 *     username: admin               # 文档 Basic 认证账号；默认空串，桥接缺省 root
 *     password: change-me           # 文档 Basic 认证密码；默认空串，桥接缺省 root123
 *     description: 业务接口文档       # 文档描述；默认 "无"
 *     version: 1.0.0                # 文档版本；默认 "1.0"
 *     knife4j:                      # 分组列表；不配置则不注册 knife4j-OpenAPI 与分组
 *       - group: user               # 分组编码；为空时取 group-name
 *         group-name: 用户模块       # 分组名 → springdoc.group-configs[i].group / display-name
 *         title: 用户服务            # 分组标题；默认 ""
 *         description: 用户相关接口   # 分组描述；默认 ""
 *         version: v1.0.0           # 分组版本；默认 "v1.0.0"
 *         base-package:             # 分组扫描包 → packages-to-scan；不写 = 不限包
 *           - com.example.user.controller
 *         paths-to-match:           # 分组匹配路径 → paths-to-match；桥接空值缺省 /**
 *           - /user/**
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@ConfigurationProperties(prefix = "plugin.swagger")
public class Knife4jProperties {


    /**
     * 文档总开关。
     *
     * <p>含义：控制 {@code Knife4jConfiguration} 是否装配，并经环境后处理器桥接为 {@code knife4j.enable}。</p>
     * <p>默认值：{@code false}；但自动装配条件为 {@code matchIfMissing=true}，即配置中未出现该键时文档默认开启，
     * 仅显式配置 {@code plugin.swagger.enable=false} 才关闭。该开关不会移除 springdoc 自身的 {@code /v3/api-docs}
     * 端点（桥接层无条件写入 {@code springdoc.api-docs.enabled=true}）。</p>
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     enable: true
     * </pre>
     */
    private boolean enable = false;

    /**
     * Knife4j 分组列表。
     *
     * <p>含义：多分组路由配置；非空时由 {@code Knife4jConfiguration} 注册 {@code knife4j-OpenAPI} Bean，
     * 并经环境后处理器展开为 {@code springdoc.group-configs[i].*} 与 {@code knife4j.documents[i].*}。</p>
     * <p>默认值：{@code null}（不配置则不注册分组与 {@code knife4j-OpenAPI}）。</p>
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     knife4j:
     *       - group: user
     *         group-name: 用户模块
     *         base-package:
     *           - com.example.user.controller
     * </pre>
     */
    private List<Knife4j> knife4j;

    /**
     * 默认请求头。
     *
     * <p>含义：声明文档调试时附加的默认请求头。</p>
     * <p>默认值：{@code null}。</p>
     * <p>⚠ 当前运行期不生效：仅未被装配的 {@code customize} 包预留类读取该配置，配置与否均不影响文档行为。</p>
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     default-header:
     *       X-Request-Source: swagger-docs
     * </pre>
     */
    private Map<String, String> defaultHeader;

    /**
     * 文档 Basic 认证账号。
     *
     * <p>含义：访问文档页面 / OpenAPI 数据的 Basic 认证用户名，桥接为 {@code knife4j.basic.username}
     * （{@code knife4j.basic.enable} 固定为 {@code true}）。</p>
     * <p>默认值：空串 {@code ""}；未配置该键时桥接占位符 {@code ${plugin.swagger.username:root}} 回落为 {@code root}。</p>
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     username: admin
     * </pre>
     */
    private String username = "";
    /**
     * 文档 Basic 认证密码。
     *
     * <p>含义：访问文档页面 / OpenAPI 数据的 Basic 认证密码，桥接为 {@code knife4j.basic.password}。</p>
     * <p>默认值：空串 {@code ""}；未配置该键时桥接占位符 {@code ${plugin.swagger.password:root123}} 回落为
     * {@code root123}（生产环境务必显式更换）。</p>
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     password: change-me
     * </pre>
     */
    private String password = "";
    /**
     * 是否开启 Swagger 注解记录用户操作日志。
     *
     * <p>含义：声明「Swagger 注解记录操作日志」意图。</p>
     * <p>默认值：{@code false}。</p>
     * <p>⚠ 当前本模块运行期无代码读取该字段，配置与否均不影响文档行为。</p>
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     log: false
     * </pre>
     */
    private boolean log;

    /**
     * 文档描述。
     *
     * <p>含义：写入 {@code knife4j-OpenAPI} 的 {@code Info.description}（需配置分组，否则该 Bean 不注册）。</p>
     * <p>默认值：{@code "无"}。</p>
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     description: 业务接口文档
     * </pre>
     */
    private String description = "无";
    /**
     * 文档版本。
     *
     * <p>含义：写入 {@code knife4j-OpenAPI} 的 {@code Info.version}（需配置分组）。</p>
     * <p>默认值：{@code "1.0"}。</p>
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     version: 1.0.0
     * </pre>
     */
    private String version = "1.0";

    /**
     * 服务条款。
     *
     * <p>含义：写入 {@code knife4j-OpenAPI} 的 {@code Info.termsOfService}（需配置分组）；
     * 支持含 {@code <br>} 的多行 HTML 文本。</p>
     * <p>默认值：内置 12 条服务条款长文本（含授权、使用限制、账户安全、责任限制、知识产权、隐私保护、争议解决等）。</p>
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     terms-of-service: https://example.com/api-terms
     * </pre>
     */
    private String termsOfService = "1.服务提供方：本条款中的“服务提供方”指指定的公司或组织，提供API服务。<br>\n" +
            "  2.用户：本条款中的“用户”指使用服务提供方提供的API服务的个人、公司或组织。<br>\n" +
            "  3.授权：用户在遵守本条款的前提下，服务提供方授予用户使用其API服务的权利。<br>\n" +
            "  4.使用限制：用户在使用API服务时，必须遵守以下限制：<br>\n" +
            "  &nbsp;&nbsp;&nbsp;&nbsp;a.用户不得使用API服务进行非法活动或违反法律法规的行为；<br>\n" +
            "  &nbsp;&nbsp;&nbsp;&nbsp;b.用户不得以任何方式干扰、破坏或损害API服务的正常运行；<br>\n" +
            "  &nbsp;&nbsp;&nbsp;&nbsp;c.用户不得滥用API服务，包括但不限于超出使用限制、恶意攻击等行为；<br>\n" +
            "  &nbsp;&nbsp;&nbsp;&nbsp;d.用户不得将API服务用于未经授权的商业用途。<br>\n" +
            "  5.账户安全：用户在使用API服务时，必须保护其账户的安全性，包括但不限于保护密码和API密钥的安全。<br>\n" +
            "  6.责任限制：服务提供方对于用户使用API服务所导致的任何直接或间接损失不承担责任，包括但不限于利润损失、数据损失等。<br>\n" +
            "  7.服务变更和终止：服务提供方有权根据需要随时变更或终止API服务，用户在此不得要求服务提供方承担任何责任。<br\\n" +
            "  8.知识产权：服务提供方保留所有API服务相关的知识产权。<br>9.隐私保护：服务提供方将根据相关法律法规保护用户的个人信息和数据隐私。<br>\n" +
            "  10.争议解决：本条款的解释和适用以及由此产生的争议应适用服务提供方所在地的法律。<br>\n" +
            "  11.其他条款：本条款中的任何条款无效或不可执行时，不影响其他条款的效力。<br>\n" +
            "  12.条款变更：服务提供方有权随时修改本条款，并通过适当的方式向用户通知。<br>\n" +
            "  \uD83D\uDCE2请用户在使用API服务之前仔细阅读并理解本条款，如用户不同意本条款的任何内容，请勿使用API服务。<br>\n" +
            "  ⚠\uFE0F用户一旦使用API服务，即视为用户已接受本条款的全部内容。";

    /**
     * 获取 刀4j 分组配置
     *
     * @return Knife4j 分组配置列表
     */
    public List<Knife4j> getKnife4j() {
        return knife4j;
    }

    /**
     * 获取默认请求头
     *
     * @return 默认请求头配置
     */
    public Map<String, String> getDefaultHeader() {
        return defaultHeader;
    }

    /**
     * 获取描述信息
     *
     * @return 描述信息
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取版本号
     *
     * @return 版本号
     */
    public String getVersion() {
        return version;
    }

    /**
     * 获取服务条款
     *
     * @return 服务条款内容
     */
    public String getTermsOfService() {
        return termsOfService;
    }

    @Data
    @Accessors(chain = true)
    /**
     * Knife4j 分组配置。
     *
     * <p>对应 {@code plugin.swagger.knife4j[]} 列表项，经环境后处理器展开为
     * {@code springdoc.group-configs[i].*} 与 {@code knife4j.documents[i].*}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   swagger:
     *     knife4j:
     *       - group: user            # 分组编码；为空取 group-name
     *         group-name: 用户模块    # 分组名
     *         title: 用户服务         # 分组标题；默认 ""
     *         description: 用户接口   # 分组描述；默认 ""
     *         version: v1.0.0        # 分组版本；默认 "v1.0.0"
     *         base-package:          # 分组扫描包；不写 = 不限包
     *           - com.example.user.controller
     *         paths-to-match:        # 分组匹配路径；桥接空值缺省 /**
     *           - /user/**
     * </pre>
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Knife4j {

        /**
         * 分组编码。
         *
         * <p>含义：写入 {@code knife4j.documents[i].group} 与 {@code knife4j.documents[i].name}。</p>
         * <p>默认值：{@code null}；桥接时为空回落取 {@code groupName}。</p>
         * <p>YAML 示例：</p>
         * <pre>
         * plugin:
         *   swagger:
         *     knife4j:
         *       - group: user
         * </pre>
         */
        private String group;
        /**
         * 分组名。
         *
         * <p>含义：写入 {@code springdoc.group-configs[i].group} 与 {@code springdoc.group-configs[i].display-name}；
         * {@code group} 为空时兼作 knife4j 侧分组编码。</p>
         * <p>默认值：{@code null}。</p>
         * <p>YAML 示例：</p>
         * <pre>
         * plugin:
         *   swagger:
         *     knife4j:
         *       - group-name: 用户模块
         * </pre>
         */
        private String groupName;

        /**
         * 分组匹配路径。
         *
         * <p>含义：写入 {@code springdoc.group-configs[i].paths-to-match}（多值逗号拼接）。</p>
         * <p>默认值：{@code null}；桥接时空值缺省 {@code "/**"}（匹配全部路径）。</p>
         * <p>YAML 示例：</p>
         * <pre>
         * plugin:
         *   swagger:
         *     knife4j:
         *       - paths-to-match:
         *           - /user/**
         * </pre>
         */
        private String[] pathsToMatch;
        /**
         * 分组扫描包。
         *
         * <p>含义：写入 {@code springdoc.group-configs[i].packages-to-scan}（多值逗号拼接）。</p>
         * <p>默认值：{@code null}（不限定扫描包）。</p>
         * <p>YAML 示例：</p>
         * <pre>
         * plugin:
         *   swagger:
         *     knife4j:
         *       - base-package:
         *           - com.example.user.controller
         * </pre>
         */
        private String[] basePackage;
        /**
         * 分组标题。
         *
         * <p>含义：写入 {@code springdoc.group-configs[i].title}。</p>
         * <p>默认值：空串 {@code ""}。</p>
         * <p>YAML 示例：</p>
         * <pre>
         * plugin:
         *   swagger:
         *     knife4j:
         *       - title: 用户服务
         * </pre>
         */
        private String title = "";

        /**
         * 分组描述。
         *
         * <p>含义：写入 {@code springdoc.group-configs[i].description}。</p>
         * <p>默认值：空串 {@code ""}。</p>
         * <p>YAML 示例：</p>
         * <pre>
         * plugin:
         *   swagger:
         *     knife4j:
         *       - description: 用户相关接口
         * </pre>
         */
        private String description = "";
        /**
         * 分组版本。
         *
         * <p>含义：写入 {@code springdoc.group-configs[i].version}。</p>
         * <p>默认值：{@code "v1.0.0"}。</p>
         * <p>YAML 示例：</p>
         * <pre>
         * plugin:
         *   swagger:
         *     knife4j:
         *       - version: v1.0.0
         * </pre>
         */
        private String version = "v1.0.0";

        /**
         * 获取分组编码
         *
         * @return 分组编码
         */
        public String getGroup() {
            return group;
        }

        /**
         * 获取分组名称
         *
         * @return 分组名称
         */
        public String getGroupName() {
            return groupName;
        }

        /**
         * 获取路径匹配配置
         *
         * @return 路径匹配数组
         */
        public String[] getPathsToMatch() {
            return pathsToMatch;
        }

        /**
         * 获取基础包配置
         *
         * @return 基础包数组
         */
        public String[] getBasePackage() {
            return basePackage;
        }

        /**
         * 获取标题
         *
         * @return 标题
         */
        public String getTitle() {
            return title;
        }

        /**
         * 获取描述
         *
         * @return 描述
         */
        public String getDescription() {
            return description;
        }

        /**
         * 获取版本
         *
         * @return 版本号
         */
        public String getVersion() {
            return version;
        }

    }
}
