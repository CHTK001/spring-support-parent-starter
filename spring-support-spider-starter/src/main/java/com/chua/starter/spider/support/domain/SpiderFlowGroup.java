package com.chua.starter.spider.support.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 编排画布分组框。
 *
 * <p>用于在 ScReteEditor 中将相关节点归组（如登录分组、详情分组），
 * 支持半透明背景、可拖动、整体移动组内节点。</p>
 *
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpiderFlowGroup {

    /** 分组唯一 ID */
    private String groupId;

    /** 分组标题 */
    private String title;

    /** 分组内节点 ID 列表 */
    private List<String> nodeIds;

    /** 分组框左上角 X 坐标 */
    private Double x;

    /** 分组框左上角 Y 坐标 */
    private Double y;

    /** 分组框宽度 */
    private Double width;

    /** 分组框高度 */
    private Double height;

    /** 背景色（CSS 颜色值，如 rgba(100,149,237,0.15)） */
    private String backgroundColor;
}
