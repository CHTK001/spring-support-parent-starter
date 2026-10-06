package com.chua.starter.datasourcehub.support.nginx;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 单条 nginx 指令的元数据：决定 UI 用什么控件渲染、是否必填、能填什么值。
 *
 * <p>这份元数据是「UI 模式」和「新人模式」的关键。解析器只负责把文本拆成
 * 「指令名 + 参数文本」，并不知道 {@code client_max_body_size} 该用数字输入框、
 * {@code proxy_pass} 该用带提示的文本框。元数据补上了这一层语义，
 * 让表单化编辑不必在前端硬编码一份 nginx 指令知识。</p>
 *
 * <p>解析器对未知指令完全容错：没有元数据的指令仍然会被解析、展示与原样保存，
 * 只是 UI 退化为通用文本输入。因此这张表<strong>不需要完整</strong>，
 * 缺项只影响表单体验，不会造成配置丢失。</p>
 *
 * @param name       指令名
 * @param scopes     允许出现的区块作用域
 * @param argType    参数类型，决定 UI 控件
 * @param enumValues 枚举取值（argType 为 enum 时有效）
 * @param multiple   同一区块内是否可重复出现
 * @param required   是否必填
 * @param desc       中文说明
 * @param example    示例值
 * @author CH
 * @since 4.0.0.42
 */
public record NginxDirectiveMeta(
        String name,
        Set<String> scopes,
        String argType,
        List<String> enumValues,
        boolean multiple,
        boolean required,
        String desc,
        String example
) {

    /**
     * 规范构造器：作用域集合与枚举取值做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。本记录只能由 {@link #of} 与
     * {@link #enumOf} 两个静态工厂产出：{@code scopes} 一律由 {@code Set.of} 字面量
     * 或本包内的不可变常量传入，{@code enumValues} 由 {@code List.of} 传入，
     * 恒不为 null 且元素恒不为 null，故可使用拒绝 null 的 {@link Set#copyOf}
     * 与 {@link List#copyOf}。两个开关为基本类型，无需非空断言。</p>
     *
     * @param scopes     允许出现的区块作用域
     * @param enumValues 枚举取值（argType 为 enum 时有效）
     */
    public NginxDirectiveMeta {
        scopes = Set.copyOf(Objects.requireNonNull(scopes, "scopes 不能为 null"));
        enumValues = List.copyOf(Objects.requireNonNull(enumValues, "enumValues 不能为 null"));
    }

    /**
     * 作用域：主配置（events / http 同级）
     */
    public static final String SCOPE_MAIN = "main";

    /**
     * 作用域：events 块
     */
    public static final String SCOPE_EVENTS = "events";

    /**
     * 作用域：http 块
     */
    public static final String SCOPE_HTTP = "http";

    /**
     * 作用域：server 块
     */
    public static final String SCOPE_SERVER = "server";

    /**
     * 作用域：location 块
     */
    public static final String SCOPE_LOCATION = "location";

    /**
     * 作用域：upstream 块
     */
    public static final String SCOPE_UPSTREAM = "upstream";

    /**
     * 作用域：stream（TCP 代理）块
     */
    public static final String SCOPE_STREAM = "stream";

    /**
     * 作用域：types 块
     */
    public static final String SCOPE_TYPES = "types";

    /**
     * 作用域：map 块
     */
    public static final String SCOPE_MAP = "map";

    /**
     * 作用域：if 块
     */
    public static final String SCOPE_IF = "if";

    /**
     * 作用域：limit_except 块
     */
    public static final String SCOPE_LIMIT_EXCEPT = "limit_except";

    /**
     * 参数类型：布尔开关，无参数
     */
    public static final String TYPE_FLAG = "flag";

    /**
     * 参数类型：普通字符串
     */
    public static final String TYPE_STRING = "string";

    /**
     * 参数类型：数字
     */
    public static final String TYPE_NUMBER = "number";

    /**
     * 参数类型：带单位容量（10m / 1g / 512k）
     */
    public static final String TYPE_SIZE = "size";

    /**
     * 参数类型：时长（30s / 1h / 7d）
     */
    public static final String TYPE_DURATION = "duration";

    /**
     * 参数类型：下拉枚举
     */
    public static final String TYPE_ENUM = "enum";

    /**
     * 参数类型：文件或目录路径
     */
    public static final String TYPE_PATH = "path";

    /**
     * 参数类型：正则表达式
     */
    public static final String TYPE_REGEX = "regex";

    /**
     * 参数类型：URL / URI
     */
    public static final String TYPE_URL = "url";

    /**
     * 参数类型：主机名或 IP
     */
    public static final String TYPE_HOST = "host";

    /**
     * 参数类型：取值个数固定的列表
     */
    public static final String TYPE_LIST = "list";

    /**
     * 参数类型：多行自由文本（如 map 默认值表）
     */
    public static final String TYPE_TEXT = "text";

    /**
     * 构造无枚举的元数据。
     *
     * @param name     指令名
     * @param scopes   作用域集合
     * @param argType  参数类型
     * @param multiple 是否可重复
     * @param required 是否必填
     * @param desc     中文说明
     * @param example  示例值
     * @return 指令元数据
     */
    public static NginxDirectiveMeta of(String name, Set<String> scopes, String argType,
                                       boolean multiple, boolean required, String desc, String example) {
        return new NginxDirectiveMeta(name, scopes, argType, List.of(), multiple, required, desc, example);
    }

    /**
     * 构造带枚举的元数据。
     *
     * @param name     指令名
     * @param scopes   作用域集合
     * @param multiple 是否可重复
     * @param required 是否必填
     * @param desc     中文说明
     * @param example  示例值
     * @param values   枚举取值
     * @return 指令元数据
     */
    public static NginxDirectiveMeta enumOf(String name, Set<String> scopes, boolean multiple,
                                            boolean required, String desc, String example, String... values) {
        return new NginxDirectiveMeta(name, scopes, TYPE_ENUM, List.of(values), multiple, required, desc, example);
    }
}
