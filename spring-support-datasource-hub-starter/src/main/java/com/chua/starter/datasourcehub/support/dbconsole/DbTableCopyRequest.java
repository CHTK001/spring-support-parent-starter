package com.chua.starter.datasourcehub.support.dbconsole;

/**
 * 跨库复制表的请求体。
 *
 * <p>源与目标是两组独立的「数据源 + 库 + 表」：同一数据源的不同库、不同数据源的库，
 * 以及同库换名复制都走同一个结构，不另开接口。两个开关缺省值刻意不对称：
 * 数据默认复制（用户点「复制表」通常就是要连数据），覆盖默认关闭（同名表直接失败，
 * 免得一次手滑把已有表 DROP 掉）。</p>
 *
 * @param sourceDatasourceId 源数据源 ID，null/0 表示应用默认主数据源
 * @param sourceDatabase     源库名，可为空（沿用连接默认库）
 * @param sourceTable        源表名
 * @param targetDatasourceId 目标数据源 ID，null/0 表示应用默认主数据源
 * @param targetDatabase     目标库名，可为空（沿用连接默认库）
 * @param targetTable        目标表名
 * @param copyData           是否连同数据一起复制，缺省 true
 * @param overwrite          目标同名表是否覆盖（写临时表后原子换名），缺省 false
 * @author CH
 * @since 4.0.0.51
 */
public record DbTableCopyRequest(
        Long sourceDatasourceId,
        String sourceDatabase,
        String sourceTable,
        Long targetDatasourceId,
        String targetDatabase,
        String targetTable,
        Boolean copyData,
        Boolean overwrite
) {

    /**
     * 是否复制数据（缺省 true）。
     *
     * @return true=连数据一起复制
     */
    public boolean copyDataOrDefault() {
        return copyData == null || copyData;
    }

    /**
     * 是否覆盖同名目标表（缺省 false）。
     *
     * @return true=覆盖同名表
     */
    public boolean overwriteOrDefault() {
        return Boolean.TRUE.equals(overwrite);
    }
}
