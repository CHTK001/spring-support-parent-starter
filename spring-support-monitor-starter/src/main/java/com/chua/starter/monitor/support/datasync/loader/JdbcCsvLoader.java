package com.chua.starter.monitor.support.datasync.loader;

import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;

import java.nio.file.Path;
import java.util.List;

/**
 * 数据库 CSV 快速导入 SPI（与 datasync 逐行引擎分离的「LOAD 直灌」路径）。
 *
 * <p>每种关系型数据库一个实现类，经 {@code META-INF/extensions} 注册，用
 * {@code ServiceProvider.of(JdbcCsvLoader.class).getExtension(dbType)} 按方言取用。
 * 第一版提供 MySQL（{@code LOAD DATA LOCAL INFILE}）与 PostgreSQL（{@code COPY FROM STDIN}），
 * 未注册的数据库返回「不支持」，由调用方回退到 datasync 逐行路径。</p>
 *
 * <p>约定：传入的 CSV 文件必须是<b>规范 CSV</b>（首行表头、UTF-8 无 BOM、逗号分隔、
 * 双引号转义、无多余表头行），由 {@code BulkImportService} 统一转好，本接口只负责灌库。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
public interface JdbcCsvLoader {

    /**
     * SPI 名称（方言）：mysql / postgresql / ...
     *
     * @return 方言名
     */
    String name();

    /**
     * 是否支持该 JDBC URL 方言。
     *
     * @param jdbcUrl 目标库 JDBC URL
     * @return true 表示支持 LOAD 直灌
     */
    boolean supports(String jdbcUrl);

    /**
     * 把规范 CSV 灌入目标表。
     *
     * <p>列清单按 CSV 表头顺序对应：传入的 {@code columns} 与 CSV 表头顺序一致，
     * 实现方把列名拼进 LOAD/COPY 语句即可。</p>
     *
     * @param target     目标库连接信息
     * @param targetTable 目标表名（不带库前缀，按目标库当前 schema 解析）
     * @param columns    目标列名清单（与 CSV 表头顺序一致）
     * @param csvFile    规范 CSV 文件（UTF-8 无 BOM，首行表头）
     * @param encoding   文件字符集（UTF-8）
     * @return 实际写入行数
     * @throws Exception 灌入失败时抛出，原因原文透出给调用方
     */
    long load(DatasourceJdbcInfo target, String targetTable, List<String> columns, Path csvFile, String encoding) throws Exception;
}
