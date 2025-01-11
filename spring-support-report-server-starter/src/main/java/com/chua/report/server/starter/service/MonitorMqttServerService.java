package com.chua.report.server.starter.service;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.report.server.starter.entity.FileStorage;
import com.chua.report.server.starter.entity.MonitorMqttServer;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.mybatis.entity.Query;

import java.util.Set;

/**
 * 监控MQTT服务器服务接口
 * 继承自IService，专注于MonitorMqttServer实体的管理
 */
public interface MonitorMqttServerService extends IService<MonitorMqttServer> {

    /**
     * 根据ID删除监控MQTT服务器信息
     *
     * @param id 监控MQTT服务器的ID
     * @return 返回删除操作的结果，包括是否成功
     */
    ReturnResult<Boolean> deleteFor(Integer id);

    /**
     * 更新监控MQTT服务器信息
     *
     * @param t 待更新的监控MQTT服务器实体
     * @return 返回更新操作的结果，包括是否成功
     */
    ReturnResult<Boolean> updateFor(MonitorMqttServer t);

    /**
     * 保存监控MQTT服务器信息
     *
     * @param t 待保存的监控MQTT服务器实体
     * @return 返回保存操作的结果，包括是否成功和保存后的文件存储信息
     */
    ReturnResult<MonitorMqttServer> saveFor(MonitorMqttServer t);

    /**
     * 分页查询监控MQTT服务器信息
     *
     * @param page 分页查询对象，包含分页参数
     * @param entity 查询条件，即MonitorMqttServer实体
     * @return 返回分页查询结果，包括文件存储信息列表和分页信息
     */
    ReturnPageResult<MonitorMqttServer> pageFor(Query<MonitorMqttServer> page, MonitorMqttServer entity);

    /**
     * 启动监控MQTT服务器
     *
     * @param monitorMqttId 监控MQTT服务器的ID
     * @return 返回启动操作的结果，包括是否成功
     */
    ReturnResult<Boolean> start(Integer monitorMqttId);

    /**
     * 停止监控MQTT服务器
     *
     * @param monitorMqttId 监控MQTT服务器的ID
     * @return 返回停止操作的结果，包括是否成功
     */
    ReturnResult<Boolean> stop(Integer monitorMqttId);
}
