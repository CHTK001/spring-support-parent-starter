package com.chua.starter.datasourcehub.support.dbconsole;

/**
 * 导入高级选项（对齐 Navicat 第 6 步的「高级」对话框）。
 *
 * @param extendedInsert     使用扩展插入语句（同一条 INSERT 带多组值）。
 *                           Navicat 里它只影响生成的 SQL 形态；本实现的写入走
 *                           {@code PreparedStatement.addBatch}，效果上等价，
 *                           因此该开关只控制「每批打包多少行」以外的日志说明，
 *                           关闭时按单行提交（更慢但错误定位更细）。
 * @param maxStatementKb     最大语句大小（KB）：关闭扩展插入时的单条语句上限，
 *                           0 表示不限制。用于避免超长 SQL 被服务端拒绝。
 * @param nullForEmpty       「使用 NULL 取代空白字符串」：空串与全空白字段写成 NULL
 *                           而不是 {@code ''}。对可空列有用，对 NOT NULL 列会被
 *                           服务端拒绝并计入错误。
 * @param ignoreForeignKey   「忽略外键约束」：会话级 {@code SET FOREIGN_KEY_CHECKS=0}
 *                           （仅 MySQL/MariaDB 支持；其它内核忽略该开关）。
 * @param continueOnError    「遇到错误时继续」：单行失败不中断整批。
 *                           false 时首行失败即中止。
 * @author CH
 * @since 4.0.0.45
 */
public record ImportAdvanced(
        boolean extendedInsert,
        int maxStatementKb,
        boolean nullForEmpty,
        boolean ignoreForeignKey,
        boolean continueOnError
) {

    /**
     * 默认高级选项：扩展插入开、语句上限 1024KB、不转 NULL、不忽略外键、遇错继续。
     *
     * <p>与 Navicat 的出厂勾选一致：前三项决定了「大文件也能导进去」，
     * 「遇错继续」是导入向导的核心价值（一行脏数据不该毁掉整批）。</p>
     *
     * @return 默认高级选项
     */
    public static ImportAdvanced defaults() {
        return new ImportAdvanced(true, 1024, false, false, true);
    }

    /**
     * 规范构造器：夹紧非法数值。
     *
     * @param extendedInsert   是否扩展插入
     * @param maxStatementKb   语句上限（KB）
     * @param nullForEmpty     空串转 NULL
     * @param ignoreForeignKey 忽略外键
     * @param continueOnError  遇错继续
     */
    public ImportAdvanced {
        if (maxStatementKb < 0) {
            maxStatementKb = 0;
        }
    }

    /**
     * 单条语句最多承载的行数（扩展插入关闭时为 1）。
     *
     * <p>用「语句大小上限 ÷ 单行估算」不适合在写入前算，因此这里只把
     * {@code extendedInsert} 翻译成 batch 粒度；真正的容量保护由
     * {@link DbImporter} 的分批逻辑承担（{@code maxBatchRows}）。</p>
     *
     * @param batchRows 调用方期望的批行数
     * @return 实际批行数
     */
    public int effectiveBatchRows(int batchRows) {
        if (!extendedInsert) {
            return 1;
        }
        return batchRows > 0 ? batchRows : 500;
    }
}
