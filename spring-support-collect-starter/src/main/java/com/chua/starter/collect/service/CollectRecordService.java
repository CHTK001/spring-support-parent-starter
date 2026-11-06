package com.chua.starter.collect.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.collect.entity.CollectRecord;
import com.chua.starter.collect.vo.CollectRecordVO;

import java.util.List;

/**
 * 采集结果服务。
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectRecordService extends IService<CollectRecord> {

    /**
     * 结果分页，payload 解析成有序字段列表。
     *
     * @param page      分页对象
     * @param condition 查询条件
     * @return 结果分页
     */
    IPage<CollectRecordVO> pageRecords(IPage<CollectRecord> page, CollectRecord condition);

    /**
     * 结果详情。
     *
     * @param recordId 记录ID
     * @return 记录出参
     */
    CollectRecordVO detail(Long recordId);

    /**
     * 删除单条结果。
     *
     * @param recordId 记录ID
     * @return 是否成功
     */
    boolean deleteRecord(Long recordId);

    /**
     * 批量删除结果。
     *
     * @param recordIds 记录ID列表
     * @return 删除条数
     */
    int deleteRecords(List<Long> recordIds);

    /**
     * 清空结果，流程与任务至少指定一个。
     *
     * @param flowId 流程ID
     * @param taskId 任务ID
     * @return 删除条数
     */
    int clearRecords(Integer flowId, Long taskId);

    /**
     * 结果总数，流程为空时统计全库。
     *
     * @param flowId 流程ID
     * @return 记录数
     */
    long countRecords(Integer flowId);
}
