package com.chua.spider.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.spider.support.dto.SpiderDefinitionSaveRequest;
import com.chua.spider.support.vo.SpiderDataRecordVo;
import com.chua.spider.support.vo.SpiderDefinitionVo;
import com.chua.spider.support.vo.SpiderExecutionRecordVo;

/**
 * 爬虫中心业务服务接口。
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface SpiderCenterService {

    /**
     * 分页查询爬虫定义。
     *
     * @param pageNo 页码（从 1 开始）
     * @param pageSize 每页大小
     * @param keyword 编码或名称模糊匹配，可空
     * @return 分页结果
     */
    Page<SpiderDefinitionVo> pageDefinitions(int pageNo, int pageSize, String keyword);

    /**
     * 根据编码查询定义。
     *
     * @param spiderCode 爬虫编码
     * @return 定义视图
     */
    SpiderDefinitionVo getDefinition(String spiderCode);

    /**
     * 保存或更新爬虫定义。
     *
     * @param req 保存请求
     * @return 持久化后的视图
     */
    SpiderDefinitionVo saveDefinition(SpiderDefinitionSaveRequest req);

    /**
     * 删除爬虫定义。
     *
     * @param spiderCode 爬虫编码
     * @return 是否成功
     */
    boolean deleteDefinition(String spiderCode);

    /**
     * 切换启停状态。
     *
     * @param spiderCode 爬虫编码
     * @param newStatus 新状态（0/1）
     * @return 是否成功
     */
    boolean toggleStatus(String spiderCode, Integer newStatus);

    /**
     * 分页查询采集数据。
     *
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @param spiderDefinitionCode 爬虫编码（必填）
     * @param spiderExecutionNo 执行批次号，可空
     * @param depth 指定深度过滤，null 表示不过滤
     * @return 分页结果
     */
    Page<SpiderDataRecordVo> pageDataRecords(
            int pageNo,
            int pageSize,
            String spiderDefinitionCode,
            String spiderExecutionNo,
            Integer depth
    );

    /**
     * 分页查询执行记录。
     *
     * @param pageNo 页码
     * @param pageSize 每页大小
     * @param spiderDefinitionCode 爬虫编码
     * @return 分页结果
     */
    Page<SpiderExecutionRecordVo> pageExecutions(int pageNo, int pageSize, String spiderDefinitionCode);
}