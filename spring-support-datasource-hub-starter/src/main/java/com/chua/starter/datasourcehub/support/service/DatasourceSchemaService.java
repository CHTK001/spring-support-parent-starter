package com.chua.starter.datasourcehub.support.service;

import com.chua.starter.datasourcehub.support.model.FieldMappingVO;
import com.chua.starter.datasourcehub.support.model.SchemaColumnVO;
import com.chua.starter.datasourcehub.support.model.SchemaTableVO;
import com.chua.starter.datasourcehub.support.model.TableDdlVO;
import com.chua.starter.datasourcehub.support.model.dto.FieldMappingDTO;
import com.chua.starter.datasourcehub.support.model.dto.TableDdlDTO;

import java.util.List;

/**
 * 数据源结构服务：库表浏览、建表 DDL、字段映射。
 *
 * <p>这些是"数据源模块"的能力，采集/监控等系统只消费不重复实现：
 * 采集的入库节点把目标表与字段交给本服务，由本服务负责类型推断、跨方言 DDL 与
 * "表已存在怎么办"的策略。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface DatasourceSchemaService {

    /**
     * 列出数据源里的表与视图。
     *
     * @param datasource 数据源引用：留空或 {@code app} 表示应用主库，否则为台账 ID 或名称
     * @return 表名清单，按名称排序去重
     */
    List<String> listTables(String datasource);

    /**
     * 列出数据源里的表与视图（含表备注与类型）。
     *
     * <p>{@link #listTables(String)} 只给表名串，前端三联选的表下拉要显示
     * 中文备注时调这里：一次拿全「表名 + 表备注 + TABLE/VIEW」，不用逐表再查。</p>
     *
     * @param datasource 数据源引用：留空或 {@code app} 表示应用主库，否则为台账 ID 或名称
     * @return 表元数据，按名称排序去重
     */
    List<SchemaTableVO> listTableInfos(String datasource);

    /**
     * 列出指定表的列元数据。
     *
     * @param datasource 数据源引用：留空或 {@code app} 表示应用主库，否则为台账 ID 或名称
     * @param table      表名，必须合法标识符
     * @return 列元数据，按列序号排序
     */
    List<SchemaColumnVO> listColumns(String datasource, String table);

    /**
     * 判断表是否存在。
     *
     * @param datasource 数据源引用
     * @param table      表名
     * @return 存在返回 true
     */
    boolean tableExists(String datasource, String table);

    /**
     * 预览建表 / 补列将要执行的 DDL（不落库）。
     *
     * @param request 建表请求
     * @return 预览结果（含 SQL 文本）
     */
    TableDdlVO previewDdl(TableDdlDTO request);

    /**
     * 执行建表 / 补列。
     *
     * @param request 建表请求
     * @return 执行结果
     */
    TableDdlVO executeDdl(TableDdlDTO request);

    /**
     * 解析字段映射：来源字段与目标表列的对应关系、需新增的列与类型建议。
     *
     * @param request 映射请求
     * @return 每个来源字段的映射结论
     */
    List<FieldMappingVO> resolveMapping(FieldMappingDTO request);
}
