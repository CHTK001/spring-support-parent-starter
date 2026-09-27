package com.chua.starter.oauth.client.support.user;

import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.google.common.base.Splitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 静态账号可变凭据存储。
 *
 * <p>静态协议（{@code plugin.oauth.protocol=static}）的账号默认来自配置
 * {@code plugin.oauth.temp.user}，配置在进程生命周期内不可变。为支持「账号安全」页修改密码，
 * 本存储在首次使用时按配置初始化一份内存副本，登录鉴权与改密统一读写该副本：
 * 修改后立即对后续登录生效（进程重启后回到配置默认值，这是静态/开发模式的固有边界）。</p>
 *
 * <p>配置与校验格式与 {@link StaticTempLogin} 完全一致：
 * {@code 用户名:密码;用户名2:密码2}，密码登录兼容「配置明文」与「配置明文的 MD5」两种提交。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class StaticUserStore {

    /**
     * 单例
     */
    private static final StaticUserStore INSTANCE = new StaticUserStore();

    /**
     * 用户名 -> 配置明文密码（保持配置顺序）
     */
    private final Map<String, String> credentials = new LinkedHashMap<>();

    /**
     * 是否已按配置初始化
     */
    private boolean initialized;

    private StaticUserStore() {
    }

    /**
     * 获取单例。
     *
     * @return 存储实例
     */
    public static StaticUserStore get() {
        return INSTANCE;
    }

    /**
     * 取当前生效的账号配置字符串。
     *
     * <p>首次调用按原始配置初始化内存副本；之后始终返回内存副本渲染结果，
     * 保证改密后 {@link StaticTempLogin} 的登录鉴权读到新口令。</p>
     *
     * @param rawConfig 原始配置 {@code plugin.oauth.temp.user}
     * @return 当前生效的配置字符串
     */
    public synchronized String effective(String rawConfig) {
        if (!initialized) {
            parseInto(rawConfig, credentials);
            initialized = true;
        }
        return render();
    }

    /**
     * 校验账号口令是否匹配（与登录同一判定：明文或配置明文的 MD5）。
     *
     * @param username 用户名
     * @param password 提交的口令
     * @return 匹配返回 true
     */
    public synchronized boolean matches(String username, String password) {
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return false;
        }
        String stored = credentials.get(username);
        if (null == stored) {
            return false;
        }
        return stored.equals(password) || DigestUtils.md5(stored).equals(password);
    }

    /**
     * 更新账号口令（明文）。
     *
     * @param username    用户名
     * @param newPassword 新口令明文
     * @return 账号存在且更新成功返回 true
     */
    public synchronized boolean updatePassword(String username, String newPassword) {
        if (StringUtils.isBlank(username) || StringUtils.isBlank(newPassword)) {
            return false;
        }
        if (!credentials.containsKey(username)) {
            return false;
        }
        credentials.put(username, newPassword);
        return true;
    }

    /**
     * 将当前内存副本渲染为配置字符串。
     *
     * @return 配置字符串
     */
    public synchronized String render() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : credentials.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * 解析配置字符串到目标 Map（与 {@link StaticTempLogin} 同规则）。
     *
     * @param rawConfig 原始配置
     * @param target    目标 Map
     */
    private void parseInto(String rawConfig, Map<String, String> target) {
        if (StringUtils.isBlank(rawConfig)) {
            return;
        }
        for (String entry : Splitter.on(";").omitEmptyStrings().trimResults().splitToList(rawConfig)) {
            List<String> parts = Splitter.on(":").omitEmptyStrings().limit(2).trimResults().splitToList(entry);
            if (parts.isEmpty()) {
                continue;
            }
            String user = parts.get(0);
            // 与 StaticTempLogin 一致：仅配置用户名时，密码取用户名本身
            String pass = parts.size() > 1 ? parts.get(1) : user;
            target.put(user, pass);
        }
    }

    /**
     * 仅用于诊断：返回当前账号名列表。
     *
     * @return 账号名列表
     */
    public synchronized List<String> usernames() {
        return new ArrayList<>(credentials.keySet());
    }
}
