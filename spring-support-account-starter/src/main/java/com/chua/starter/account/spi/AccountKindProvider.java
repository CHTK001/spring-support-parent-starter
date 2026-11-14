package com.chua.starter.account.spi;

import com.chua.starter.account.manage.store.SysAccount;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 账号种类能力 SPI —— 「这个种类的账号长什么样、怎么校验、能干什么」。
 *
 * <p>账号管理页的骨架（表结构、唯一键、归属隔离、凭据加解密、事务与批量语义、
 * HTTP 契约）留在 core，<b>不</b>开放给本接口；本接口只收敛「换一个厂商 / 种类就要
 * 改 core」的那部分：种类元数据、表单字段、落库前校验、新增默认值、能力位。</p>
 *
 * <p>实现通过 utilities 的 {@code @Spi} 注册，并在
 * {@code META-INF/extensions/com.chua.starter.account.spi.AccountKindProvider}
 * 登记；调用方一律走 {@code AccountKindCatalog} 门面，不直接枚举 SPI。</p>
 *
 * <p><b>凭据边界</b>：{@link #validate(SysAccount, String)} 拿到的明文由编排层解密后传入，
 * 实现不得持久化、不得回显、不得写日志。</p>
 *
 * @author CH
 * @since 4.0.0.61
 */
public interface AccountKindProvider {

    /**
     * 稳定落库值（{@code API_KEY} / {@code EMAIL} / {@code SMS} …）。
     *
     * <p>落库后不允许改名：改名等于让存量账号变成未知种类。</p>
     *
     * @return 种类值
     */
    String kind();

    /**
     * 中文标题（下拉与标签用）。
     *
     * @return 标题
     */
    String label();

    /**
     * 图标名（ReIcon 名）。
     *
     * @return 图标名
     */
    default String icon() {
        return "Key";
    }

    /**
     * 排序（越小越靠前），相同值按注册顺序。
     *
     * @return 排序值
     */
    default int order() {
        return 100;
    }

    /**
     * 收录到密钥库时的密钥类型（{@code api} / {@code mail} / {@code sms}）。
     *
     * @return 密钥类型
     */
    default String secretType() {
        return "api";
    }

    /**
     * 表单字段描述（前端按此渲染，新增种类零改前端）。
     *
     * @return 字段清单（按展示顺序）
     */
    default List<AccountField> fields() {
        return List.of();
    }

    /**
     * 能力位（界面据此显隐按钮）。
     *
     * <p>只声明真有实现的：没装对应 SPI 实现时不得标为支持。</p>
     *
     * @return 能力位集合
     */
    default Set<AccountCapability> capabilities() {
        return Set.of();
    }

    /**
     * 落库前校验种类专属规则（必填、格式、互斥）。
     *
     * <p>核心的「编码 / 名称不能为空」由编排层保证，这里只处理本种类额外的规则；
     * 校验失败抛 {@code ApiException}，由 {@code AccountApiExceptionAdvice} 统一转信封。</p>
     *
     * @param account     待落库账号（{@code kind} 已规范化为大写）
     * @param plainSecret 本次请求提交的明文凭据；未提交时为 {@code null}
     */
    default void validate(SysAccount account, String plainSecret) {
    }

    /**
     * 新增时补默认值（{@code endpoint} / {@code config} 等非敏感字段）。
     *
     * <p>只在 {@code create} 调用；实现不得读写 {@code secret}。</p>
     *
     * @param account 待落库账号
     */
    default void applyDefaults(SysAccount account) {
    }

    /**
     * 规范化种类值（去空白 + 大写）。
     *
     * @param kind 原始种类
     * @return 规范化后的种类；输入为空时返回 {@code null}
     */
    static String normalize(String kind) {
        if (kind == null) {
            return null;
        }
        String value = kind.trim();
        return value.isEmpty() ? null : value.toUpperCase(Locale.ROOT);
    }
}
