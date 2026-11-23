package com.chua.starter.collect.store;

import com.chua.starter.collect.engine.CollectSink;
import com.chua.starter.collect.mapper.CollectRecordMapper;
import com.chua.starter.collect.mapper.CollectTaskLogMapper;
import com.chua.starter.collect.mapper.CollectTaskMapper;
import com.chua.starter.collect.properties.CollectProperties;
import com.chua.starter.collect.service.CollectFieldService;
import lombok.RequiredArgsConstructor;

/**
 * 默认落库通道工厂。
 *
 * <p>按 {@code plugin.collect.persistence.type} 选择内置实现：
 * {@code none} 建空通道，其余（含缺省）建数据库通道。
 * 宿主注册自己的 {@code CollectSinkFactory} Bean 后本类不再装配。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@RequiredArgsConstructor
public class CollectDatabaseSinkFactory implements CollectSinkFactory {

    /**
     * 内置实现标识：数据库落库。
     */
    public static final String TYPE_DATABASE = "database";

    /**
     * 内置实现标识：不落结果。
     */
    public static final String TYPE_NONE = "none";

    /**
     * 节点日志表访问。
     */
    private final CollectTaskLogMapper taskLogMapper;

    /**
     * 结果表访问。
     */
    private final CollectRecordMapper recordMapper;

    /**
     * 任务表访问。
     */
    private final CollectTaskMapper taskMapper;

    /**
     * 字段定义服务。
     */
    private final CollectFieldService fieldService;

    /**
     * 平台配置。
     */
    private final CollectProperties properties;

    @Override
    public CollectSink create(Integer flowId, Long taskId, boolean persist) {
        String type = properties.getPersistence().getType();
        if (TYPE_NONE.equalsIgnoreCase(type)) {
            return new CollectNoopSink();
        }
        return new CollectDatabaseSink(flowId, taskId, persist,
                taskLogMapper, recordMapper, taskMapper, fieldService);
    }
}
