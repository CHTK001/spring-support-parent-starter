package com.chua.starter.account;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.account.spi.AccountKindProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 账号种类注册表门面 —— 收敛「种类 SPI 的枚举与规范化」。
 *
 * <p>上层（service / controller）只依赖本门面，不直接感知 SPI 有几个实现、
 * 注册表键长什么样；后续新增种类时改动面收敛于此。</p>
 *
 * <p>⚠️ 去重键取实现自己的 {@link AccountKindProvider#kind()}，不能用注册表的键：
 * SPI 框架会给每个实现额外登记一条「类简名」definition，与 {@code @Spi} 别名
 * 指向同一实例，用注册表键去重会重复（与 {@link ChannelRegistry} 同一口径）。</p>
 *
 * <p><b>兜底口径</b>：{@link #supported(String)} 在 SPI 未初始化（如单测不加载
 * {@code META-INF/extensions}）时回落到 {@link AccountKind} 的三个内置常量，
 * 保证账号页不会因为「一个种类都没发现」而整体写不进去。生产环境三个内置实现
 * 都在 classpath 上，走的是 SPI 分支。</p>
 *
 * @author CH
 * @since 4.0.0.61
 */
public final class AccountKindCatalog {

    /**
     * 未声明密钥类型时的默认值（与前端 {@code SECRET_TYPE_BY_KIND} 一致）。
     */
    private static final String DEFAULT_SECRET_TYPE = "api";

    /**
     * 工具类，禁止实例化。
     */
    private AccountKindCatalog() {
    }

    /**
     * 全部种类实现（按 {@code kind()} 去重，保持注册顺序）。
     *
     * @return 种类值 → 种类实现
     */
    public static Map<String, AccountKindProvider> providers() {
        Map<String, AccountKindProvider> raw = ServiceProvider.of(AccountKindProvider.class).list();
        Map<String, AccountKindProvider> result = new LinkedHashMap<>();
        for (AccountKindProvider provider : raw.values()) {
            if (provider == null || provider.kind() == null) {
                continue;
            }
            result.putIfAbsent(provider.kind(), provider);
        }
        return result;
    }

    /**
     * 按种类值取实现（大小写不敏感）。
     *
     * @param kind 种类值
     * @return 种类实现；未注册时返回 {@code null}
     */
    public static AccountKindProvider of(String kind) {
        String normalized = AccountKindProvider.normalize(kind);
        if (normalized == null) {
            return null;
        }
        for (AccountKindProvider provider : providers().values()) {
            if (provider.kind().toUpperCase(Locale.ROOT).equals(normalized)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * 是否为受支持的种类（SPI 声明 + 内置常量兜底）。
     *
     * @param kind 种类值
     * @return true 表示受支持
     */
    public static boolean supported(String kind) {
        return of(kind) != null || AccountKind.supported(AccountKindProvider.normalize(kind));
    }

    /**
     * 全部种类实现，按 {@link AccountKindProvider#order()} 升序（同值保持注册顺序）。
     *
     * @return 有序种类清单
     */
    public static List<AccountKindProvider> sorted() {
        List<AccountKindProvider> list = new ArrayList<>(providers().values());
        list.sort(Comparator.comparingInt(AccountKindProvider::order));
        return list;
    }

    /**
     * 种类 → 密钥类型（{@code api} / {@code mail} / {@code sms}）。
     *
     * @param kind 种类值
     * @return 密钥类型；未注册时返回 {@code api}
     */
    public static String secretTypeOf(String kind) {
        AccountKindProvider provider = of(kind);
        String secretType = provider == null ? null : provider.secretType();
        return secretType == null || secretType.isBlank() ? DEFAULT_SECRET_TYPE : secretType;
    }

    /**
     * 落库前校验种类专属规则。
     *
     * @param account     待落库账号（{@code kind} 已规范化为大写）
     * @param plainSecret 本次请求提交的明文凭据；未提交时为 {@code null}
     */
    public static void validate(SysAccount account, String plainSecret) {
        if (account == null) {
            return;
        }
        AccountKindProvider provider = of(account.getKind());
        if (provider != null) {
            provider.validate(account, plainSecret);
        }
    }

    /**
     * 新增时补种类默认值（只补非敏感字段）。
     *
     * @param account 待落库账号
     */
    public static void applyDefaults(SysAccount account) {
        if (account == null) {
            return;
        }
        AccountKindProvider provider = of(account.getKind());
        if (provider != null) {
            provider.applyDefaults(account);
        }
    }
}
