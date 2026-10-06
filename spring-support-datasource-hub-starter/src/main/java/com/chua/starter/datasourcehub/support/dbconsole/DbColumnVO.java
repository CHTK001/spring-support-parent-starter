package com.chua.starter.datasourcehub.support.dbconsole;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 数据库表字段元数据条目。
 *
 * <p>字段属性维度对齐 Navicat「列属性」页：除基础类型外，还带出 UNSIGNED / ZEROFILL /
 * AUTO_INCREMENT / 字符集 / 排序规则 / 枚举取值，前端据此回填表单，用户才能改这些属性
 * （此前这些信息根本没读出来，界面无从展示）。</p>
 *
 * @param name         字段名
 * @param type         字段类型名（内核原样返回，可能含长度）
 * @param size         字段长度
 * @param nullable     是否可为空
 * @param remark       字段注释
 * @param defaultValue 默认值，JSON 键为 default
 * @param position     字段序号，从 1 开始
 * @param primaryKey   是否主键，JSON 键为 isPrimaryKey
 * @param baseType     去掉长度与 UNSIGNED 后的基础类型
 * @param unsigned     是否无符号
 * @param zerofill     是否补零
 * @param autoIncrement 是否自增
 * @param charset      列字符集
 * @param collation    列排序规则
 * @param enumValues   ENUM/SET 的取值
 * @param scale        小数位
 */
public record DbColumnVO(
        String name,
        String type,
        int size,
        boolean nullable,
        String remark,
        @JsonProperty("default") String defaultValue,
        int position,
        @JsonProperty("isPrimaryKey") boolean primaryKey,
        String baseType,
        boolean unsigned,
        boolean zerofill,
        boolean autoIncrement,
        String charset,
        String collation,
        List<String> enumValues,
        int scale
) {
    /**
     * 规范构造器：集合做防御性拷贝。
     *
     * @param name         字段名
     * @param type         字段类型名
     * @param size         字段长度
     * @param nullable     是否可为空
     * @param remark       字段注释
     * @param defaultValue 默认值
     * @param position     字段序号
     * @param primaryKey   是否主键
     * @param baseType     基础类型
     * @param unsigned     是否无符号
     * @param zerofill     是否补零
     * @param autoIncrement 是否自增
     * @param charset      字符集
     * @param collation    排序规则
     * @param enumValues   枚举取值
     * @param scale        小数位
     */
    public DbColumnVO {
        enumValues = enumValues == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(enumValues));
    }

    /**
     * 兼容旧调用：只填基础字段，其余属性留空。
     *
     * @param name         字段名
     * @param type         字段类型名
     * @param size         字段长度
     * @param nullable     是否可为空
     * @param remark       字段注释
     * @param defaultValue 默认值
     * @param position     字段序号
     * @param primaryKey   是否主键
     * @return 字段元数据
     */
    public static DbColumnVO basic(String name, String type, int size, boolean nullable,
                                   String remark, String defaultValue, int position, boolean primaryKey) {
        return new DbColumnVO(name, type, size, nullable, remark, defaultValue, position, primaryKey,
                stripModifiers(type), false, false, false, "", "", List.of(), 0);
    }

    /**
     * 去掉内核类型名里的修饰词。
     *
     * <p>MySQL / MariaDB 驱动的 {@code TYPE_NAME} 会把修饰词带进来
     * （{@code bigint unsigned}、{@code tinyint(3) unsigned zerofill}），
     * 直接当类型名用会既匹配不到前端下拉、又会在保存时被后端判成「不支持的列类型」。
     * 读不到 {@code information_schema} 时的兜底路径必须先剥干净。</p>
     *
     * @param type 内核类型名
     * @return 只剩类型名与长度的大写形式
     */
    private static String stripModifiers(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        String value = type.trim().toUpperCase(java.util.Locale.ROOT)
                .replaceAll("\\s+UNSIGNED\\b", "")
                .replaceAll("\\s+ZEROFILL\\b", "")
                .trim();
        return value.replaceAll("\\s+", " ");
    }
}