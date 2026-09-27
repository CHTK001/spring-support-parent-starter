package com.chua.payment.support.service;

import com.chua.payment.support.dto.OrderPartitionConfigDTO;
import com.chua.payment.support.entity.OrderPartitionConfig;
import com.chua.payment.support.vo.OrderPartitionConfigVO;
import com.chua.payment.support.vo.OrderPartitionPreviewVO;

import java.util.List;

/**
 * 订单分表配置服务接口，定义分片策略的管理与执行契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface OrderPartitionConfigService {

    /**
     * 列表分表配置。
     * @return 分表配置列表
     */
    List<OrderPartitionConfigVO> listConfigs();

    /**
     * 更新分表配置。
     * @param businessType 业务类型
     * @param dto dto
     * @return 更新后的分表配置
     */
    OrderPartitionConfigVO updateConfig(String businessType, OrderPartitionConfigDTO dto);

    /**
     * 预览分表执行结果。
     * @param businessType 业务类型
     */
    OrderPartitionPreviewVO preview(String businessType);

    /**
     * 执行自动建表。
     * @param businessType 业务类型
     */
    void executeAutoCreate(String businessType);

    /**
     * 执行自动迁移。
     * @param businessType 业务类型
     */
    void executeAutoMigrate(String businessType);

    /**
     * 获取分表配置实体。
     * @param businessType 业务类型
     * @return 分表配置实体
     */
    OrderPartitionConfig getConfigEntity(String businessType);
}
