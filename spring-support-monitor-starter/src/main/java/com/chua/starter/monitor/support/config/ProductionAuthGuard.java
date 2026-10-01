package com.chua.starter.monitor.support.config;

import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 生产环境鉴权守卫：防止「本地联调免鉴权」配置被原样带到生产。
 *
 * <p><b>为什么需要它</b>：monitor 的默认 {@code application.yml} 里写着
 * {@code plugin.oauth.enable=false} 且把 {@code /ms/**} 整段放进白名单，
 * 注释也说明了这是「本地联调免鉴权」。但这条配置<b>没有任何机制阻止它出现在生产</b>：
 * 实测在开启免鉴权配置时，任何能连到服务端口的人都可以
 * 无凭据创建 / 启动 / 停止 / 删除服务节点 —— 而节点能拉起任意协议服务器
 * （代理节点可指向内网地址，文件存储节点可指定读取根路径）。
 * 「本地便利」与「生产配置」之间缺少这道 fail-safe，才是真正的缺口。</p>
 *
 * <p><b>为什么默认不改变现有行为</b>：本守卫仅在
 * {@code plugin.oauth.require-auth=true} 时装配，该开关由 {@code application-prod.yml}
 * 打开。于是：本地联调不配置该开关，行为与今天完全一致；一旦以 prod profile 启动，
 * 守卫就会在启动期把「鉴权被绕过」的每一种情形逐条列出来并拒绝启动，
 * 而不是等到出事才发现。</p>
 *
 * <p><b>为什么不用「读配置文件判断」</b>：判定依据取自 Spring 绑定后的
 * {@link AuthClientProperties}，而不是再次解析 yaml。绑定后的值才是真正生效的值，
 * 也包含来自环境变量、命令行、白名单文件与运行期动态增删的最终结果。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
@ConditionalOnProperty(prefix = "plugin.oauth", name = "require-auth", havingValue = "true")
public class ProductionAuthGuard implements InitializingBean {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(ProductionAuthGuard.class);

    /**
     * 需要受到鉴权保护的路径前缀。
     *
     * <p>服务管理接口全在此前缀下。之所以写死在这里而不是做成配置：
     * 它是本守卫的判定基准，做成配置等于「配置可以关掉守卫的基准」，
     * 那就回到了原来没有 fail-safe 的状态。</p>
     */
    private static final List<String> PROTECTED_PREFIXES = List.of("/ms");

    /**
     * 绑定后的鉴权配置
     */
    private final AuthClientProperties authProperties;

    /**
     * 创建生产环境鉴权守卫
     *
     * @param authProperties 绑定后的鉴权配置
     */
    public ProductionAuthGuard(AuthClientProperties authProperties) {
        this.authProperties = authProperties;
    }

    /**
     * 启动期校验：存在任何鉴权绕过情形即拒绝启动
     *
     * @throws IllegalStateException 列出全部绕过情形
     */
    @Override
    public void afterPropertiesSet() {
        var bypasses = detectBypasses(
                authProperties.isEnable(),
                authProperties.isLocalAdminFallback(),
                authProperties.getEffectiveWhitelist(),
                PROTECTED_PREFIXES);
        if (bypasses.isEmpty()) {
            log.info("[生产鉴权守卫] 校验通过：鉴权已启用，/ms 未被白名单放行");
            return;
        }
        bypasses.forEach(reason -> log.error("[生产鉴权守卫] 鉴权被绕过：{}", reason));
        throw new IllegalStateException(
                "生产环境禁止免鉴权运行，已发现 " + bypasses.size() + " 处绕过："
                        + String.join("；", bypasses)
                        + "。请启用 plugin.oauth.enable、移除覆盖 /ms 的白名单条目、"
                        + "并关闭 plugin.oauth.local-admin-fallback 后重启。");
    }

    /**
     * 判定当前配置是否存在鉴权绕过。
     *
     * <p>抽成纯函数是为了能脱离 Spring 容器做单测：守卫本身一旦装配就会
     * 在启动期抛异常，而它的判定逻辑必须能被独立验证若干种边界，
     * 否则「守卫有效」只能靠真的把服务起崩来证明。</p>
     *
     * @param oauthEnabled        {@code plugin.oauth.enable} 的生效值
     * @param localAdminFallback  {@code plugin.oauth.local-admin-fallback} 的生效值
     * @param configuredWhitelist 生效的白名单条目（应传 {@code getEffectiveWhitelist()}：
     *                            它合并了内置默认、yaml 配置与运行期动态增删，是真正生效的那一份；
     *                            只看 yaml 会漏掉运行期被加进来的条目）
     * @param protectedPrefixes   受保护的路径前缀
     * @return 每条一个可读原因；无绕过时返回空列表
     */
    public static List<String> detectBypasses(boolean oauthEnabled,
                                              boolean localAdminFallback,
                                              List<String> configuredWhitelist,
                                              List<String> protectedPrefixes) {
        List<String> reasons = new ArrayList<>(3);
        if (!oauthEnabled) {
            reasons.add("plugin.oauth.enable=false（鉴权整体关闭，所有接口裸奔）");
        }
        if (localAdminFallback) {
            reasons.add("plugin.oauth.local-admin-fallback=true（取不到用户上下文时按本地 admin 放行）");
        }
        if (configuredWhitelist != null) {
            for (String pattern : configuredWhitelist) {
                if (null == pattern || pattern.isBlank()) {
                    continue;
                }
                String trimmed = pattern.trim();
                for (String prefix : protectedPrefixes) {
                    if (covers(trimmed, prefix)) {
                        reasons.add("白名单条目 " + trimmed + " 覆盖了受保护路径 " + prefix + "/**");
                    }
                }
            }
        }
        return reasons;
    }

    /**
     * 判断一条 Ant 风格路径模式是否覆盖某个前缀下的<b>整棵子树</b>。
     *
     * <p>判据是结构性的：只有以 {@code **} 结尾的模式才可能覆盖子树，因此只需看
     * {@code **} 之前的那段「子树根」与受保护前缀的关系。严格区分 {@code *} 与 {@code **}：
     * {@code /ms/*} 只匹配一层，<b>不</b>覆盖 {@code /ms/monitor/server}；把它误判为覆盖，
     * 守卫会在本来安全的配置上误报，久而久之就会被人当成噪声关掉 —— 那等于把 fail-safe
     * 又关回去了。</p>
     *
     * <p>另有一类容易漏：双星出现在<b>模式中间</b>时（典型如「双星 + 斜杠 + 字面段」这种
     * 登录、验证码类内置条目），它后面还有字面段要匹配，不可能覆盖整棵子树，
     * 不能因为开头是双星就判为覆盖。</p>
     *
     * @param pattern 白名单条目（Ant 风格）
     * @param prefix  受保护前缀
     * @return 覆盖整棵前缀子树时返回 {@code true}
     */
    public static boolean covers(String pattern, String prefix) {
        if (null == pattern || null == prefix) {
            return false;
        }
        String trimmed = pattern.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        int index = trimmed.indexOf("**");
        if (index < 0) {
            // 没有双星就不可能覆盖无限深度的子树
            return false;
        }
        String tail = trimmed.substring(index + 2);
        if (!tail.isEmpty() && !"/".equals(tail)) {
            // 双星后面若还有字面段（如「双星 + 斜杠 + captcha」这类内置登录条目），
            // 后面那段仍需匹配，覆盖不了整棵子树
            return false;
        }
        String root = trimmed.substring(0, index);
        if (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        if (root.isEmpty()) {
            // 裸 /** 或 **：覆盖一切
            return true;
        }
        return prefix.equals(root) || prefix.startsWith(root + "/");
    }
}