package com.chua.starter.spider.vo;

import com.chua.starter.spider.entity.SpiderDataRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集数据视图对象。
 * <p>{@code spiderDepth} 字段直接暴露给前端，前端表格独立列展示。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "采集数据视图")
public class SpiderDataRecordVo {

    /**
     * 主键
     */
    private Long spiderId;

    /**
     * 爬虫编码
     */
    private String spiderDefinitionCode;

    /**
     * 执行批次号
     */
    private String spiderExecutionNo;

    /**
     * 目标 URL
     */
    private String spiderTargetUrl;

    /**
     * 数据 schema 编码
     */
    private String spiderDataCode;

    /**
     * 数据项名称
     */
    private String spiderDataName;

    /**
     * JSON 字符串载荷
     */
    private String spiderDataPayload;

    /**
     * 状态
     */
    private String spiderDataStatus;

    /**
     * 爬取深度：0=列表页，1=第一层详情页，依此类推
     */
    private Integer spiderDepth;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 将实体转换为视图对象。
     *
     * @param e 实体
     * @return 视图对象
     */
    public static SpiderDataRecordVo from(SpiderDataRecord e) {
        if (e == null) {
            return null;
        }
        SpiderDataRecordVo v = new SpiderDataRecordVo();
        v.spiderId = e.getSpiderId();
        v.spiderDefinitionCode = e.getSpiderDefinitionCode();
        v.spiderExecutionNo = e.getSpiderExecutionNo();
        v.spiderTargetUrl = e.getSpiderTargetUrl();
        v.spiderDataCode = e.getSpiderDataCode();
        v.spiderDataName = e.getSpiderDataName();
        v.spiderDataPayload = e.getSpiderDataPayload();
        v.spiderDataStatus = e.getSpiderDataStatus();
        v.spiderDepth = e.getSpiderDepth();
        v.createTime = e.getCreateTime();
        return v;
    }
}
