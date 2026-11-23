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

    /**
     * 全文检索结果。
     *
     * <p>默认走内嵌 Lucene 索引（keyword 匹配业务主键、抓取地址与 payload 全部单元格文本），
     * 命中后按索引顺序回表组装；索引未启用或不可用时降级为数据库 LIKE 模糊查询。</p>
     *
     * @param keyword   检索关键词，必填
     * @param condition 附加过滤条件（流程/任务）
     * @param page      分页对象
     * @return 结果分页
     */
    IPage<CollectRecordVO> searchRecords(String keyword, CollectRecord condition, IPage<CollectRecord> page);

    /**
     * 全量重建全文索引（扫描整张结果表）。
     *
     * @return 重建条数
     */
    int rebuildSearchIndex();
}
