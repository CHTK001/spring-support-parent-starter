package com.chua.starter.spider.dto;

import com.chua.starter.spider.entity.SpiderDefinition;
import com.chua.starter.spider.entity.SpiderExecutionLog;
import com.chua.starter.spider.entity.SpiderDataRecord;
import com.chua.starter.spider.entity.SpiderFlow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
* 前端视图对象转换器。
*
* <p>将后端实体转换为前端约定字段命名（spider* / flow*）的视图对象，
* 反向转换用于保存请求回填实体。</p>
*
* @since 4.0.0.42
* @author CH
 */
public final class SpiderConverter {

    /**
    * JSON 解析器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
    * 私有构造方法。
     */
    private SpiderConverter() {
    }

    /**
    * 实体 → 定义视图。
    *
    * @param entity 爬虫定义实体
    * @return 定义视图对象
     */
    public static SpiderDefinitionVO toDefinitionVO(SpiderDefinition entity) {
        if (entity == null) {
            return null;
        }
        SpiderDefinitionVO vo = new SpiderDefinitionVO();
        vo.setSpiderId(entity.getId());
        vo.setSpiderCode(entity.getCode());
        vo.setSpiderName(entity.getName());
        vo.setSpiderEntryUrl(entity.getEntryUrl());
        vo.setSpiderSiteHost(entity.getSiteHost());
        vo.setSpiderExecutorType(entity.getExecutorType());
        vo.setSpiderExecutorRounds(entity.getExecutorRounds());
        vo.setSpiderCron(entity.getCronExpression());
        vo.setSpiderCollectDeepUrl(entity.getCollectDeepUrl());
        vo.setSpiderMaxDepth(entity.getMaxDepth());
        vo.setSpiderMaxPages(entity.getMaxPages());
        vo.setSpiderStatus(entity.getEnabled());
        vo.setSpiderRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
    * 定义视图 → 实体。
    *
    * @param vo 定义视图对象
    * @return 爬虫定义实体
     */
    public static SpiderDefinition toDefinition(SpiderDefinitionVO vo) {
        if (vo == null) {
            return null;
        }
        SpiderDefinition entity = new SpiderDefinition();
        entity.setId(vo.getSpiderId());
        entity.setCode(vo.getSpiderCode());
        entity.setName(vo.getSpiderName());
        entity.setEntryUrl(vo.getSpiderEntryUrl());
        entity.setSiteHost(vo.getSpiderSiteHost());
        entity.setExecutorType(vo.getSpiderExecutorType());
        entity.setExecutorRounds(vo.getSpiderExecutorRounds());
        entity.setCronExpression(vo.getSpiderCron());
        entity.setCollectDeepUrl(vo.getSpiderCollectDeepUrl());
        entity.setMaxDepth(vo.getSpiderMaxDepth());
        entity.setMaxPages(vo.getSpiderMaxPages());
        entity.setEnabled(vo.getSpiderStatus());
        entity.setRemark(vo.getSpiderRemark());
        return entity;
    }

    /**
    * 实体列表 → 视图列表。
    *
    * @param entities 实体列表
    * @return 视图列表
     */
    public static List<SpiderDefinitionVO> toDefinitionVOList(List<SpiderDefinition> entities) {
        List<SpiderDefinitionVO> result = new ArrayList<>();
        if (entities != null) {
            for (SpiderDefinition entity : entities) {
                result.add(toDefinitionVO(entity));
            }
        }
        return result;
    }

    /**
    * 执行日志 → 流程节点日志视图。
    *
    * @param log 执行日志实体
    * @return 节点日志视图
     */
    public static FlowNodeLogVO toNodeLogVO(SpiderExecutionLog log) {
        if (log == null) {
            return null;
        }
        FlowNodeLogVO vo = new FlowNodeLogVO();
        vo.setFlowNodeKey(log.getNodeId());
        vo.setFlowNodeType(log.getNodeType());
        vo.setFlowNodeStatus(log.getStatus());
        vo.setFlowNodeMessage(log.getErrorMessage());
        vo.setFlowNodeCostMs(costMs(log));
        vo.setCreateTime(log.getStartAt());
        return vo;
    }

    /**
    * 节点日志实体列表 → 视图列表。
    *
    * @param logs 执行日志实体列表
    * @return 节点日志视图列表
     */
    public static List<FlowNodeLogVO> toNodeLogVOList(List<SpiderExecutionLog> logs) {
        List<FlowNodeLogVO> result = new ArrayList<>();
        if (logs != null) {
            for (SpiderExecutionLog log : logs) {
                result.add(toNodeLogVO(log));
            }
        }
        return result;
    }

    /**
    * 采集记录 → 视图。
    *
    * @param record 采集记录实体
    * @return 采集记录视图
     */
    public static DataRecordVO toDataRecordVO(SpiderDataRecord record) {
        if (record == null) {
            return null;
        }
        DataRecordVO vo = new DataRecordVO();
        vo.setSpiderId(record.getId());
        vo.setSpiderExecutionNo(record.getExecutionNo());
        vo.setSpiderTargetUrl(record.getSourceUrl());
        vo.setSpiderDataName(record.getTitle());
        vo.setSpiderDepth(null);
        vo.setSpiderDataStatus(record.getStatus());
        vo.setCreateTime(record.getRecordTime());
        vo.setSpiderDataPayload(record.getRawData());
        return vo;
    }

    /**
    * 采集记录列表 → 视图列表。
    *
    * @param records 采集记录实体列表
    * @return 视图列表
     */
    public static List<DataRecordVO> toDataRecordVOList(List<SpiderDataRecord> records) {
        List<DataRecordVO> result = new ArrayList<>();
        if (records != null) {
            for (SpiderDataRecord record : records) {
                result.add(toDataRecordVO(record));
            }
        }
        return result;
    }

    /**
    * 流程实体 → 流程视图。
    *
    * @param flow 流程实体
    * @return 流程视图
     */
    public static SpiderFlowVO toFlowVO(SpiderFlow flow) {
        if (flow == null) {
            return null;
        }
        SpiderFlowVO vo = new SpiderFlowVO();
        vo.setFlowCode(flow.getId() == null ? "" : String.valueOf(flow.getId()));
        vo.setFlowName(flow.getName());
        vo.setFlowStatus(1);
        vo.setFlowVersion(1);
        vo.setFlowNodeCount(countNodes(flow.getNodesJson()));
        vo.setUpdateTime(flow.getUpdateTime());
        return vo;
    }

    /**
    * 流程列表 → 视图列表。
    *
    * @param flows 流程实体列表
    * @return 视图列表
     */
    public static List<SpiderFlowVO> toFlowVOList(List<SpiderFlow> flows) {
        List<SpiderFlowVO> result = new ArrayList<>();
        if (flows != null) {
            for (SpiderFlow flow : flows) {
                result.add(toFlowVO(flow));
            }
        }
        return result;
    }

    /**
    * 统计节点 JSON 中的节点数量。
    *
    * @param nodesJson 节点 JSON 数组
    * @return 节点数量
     */
    private static int countNodes(String nodesJson) {
        if (nodesJson == null || nodesJson.isBlank()) {
            return 0;
        }
        try {
            JsonNode node = MAPPER.readTree(nodesJson);
            return node.isArray() ? node.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
    * 计算节点耗时（毫秒）。
    *
    * @param log 执行日志
    * @return 耗时，无结束时间返回 空
     */
    private static Long costMs(SpiderExecutionLog log) {
        if (log.getStartAt() == null || log.getEndAt() == null) {
            return null;
        }
        return java.time.Duration.between(log.getStartAt(), log.getEndAt()).toMillis();
    }
}