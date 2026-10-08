package com.chua.starter.datasourcehub.support.dbconsole;

/**
 * AI 文本生成 SQL 的结果。
 *
 * <p>{@code sql} 已剥离模型输出的 markdown 围栏，可直接落入编辑器；
 * 是否执行由用户在控制台显式点击决定，本结果不携带任何执行副作用。</p>
 *
 * @param sql   生成的 SQL 语句
 * @param model 实际使用的模型名，未解析出时为空
 * @author CH
 * @since 4.0.0.42
 */
public record DbText2SqlVO(
        String sql,
        String model
) {
}
