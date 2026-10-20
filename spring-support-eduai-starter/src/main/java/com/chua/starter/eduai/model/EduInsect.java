package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.chua.starter.mybatis.pojo.SysBase;

/**
 * 昆虫图鉴实体
 *
 * <p>数据来源为开源项目 insect-world 的中文数据包（63 个物种 / 14 个目），
 * 已随 V2.0__initdata_all_eduai.sql 落库。结构化字段拆列存放，
 * {@code insectFacts}、{@code insectHotspots}、{@code insectLifecycle}、
 * {@code insectRelatives} 四项以 JSON 文本保存，由服务层解析后下发给前端。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_insect")
public class EduInsect extends SysBase {

    /**
     * 昆虫标识
     */
    @TableId(type = IdType.AUTO)
    private Long insectId;

    /**
     * 物种英文标识（slug），如 rhinoceros-beetle
     */
    private String insectCode;

    /**
     * 中文名
     */
    private String insectName;

    /**
     * 学名
     */
    private String insectLatin;

    /**
     * 一句话雅称
     */
    private String insectEpithet;

    /**
     * 所属目（slug）：coleoptera/lepidoptera/hymenoptera 等
     */
    private String insectOrder;

    /**
     * 简介
     */
    private String insectSummary;

    /**
     * 形态特征（JSON 数组：[{key,value,icon}]）
     */
    private String insectFacts;

    /**
     * 3D 标注点（JSON 数组：[{id,label,note,anchor,tone}]）
     */
    private String insectHotspots;

    /**
     * 生态角色
     */
    private String insectEcology;

    /**
     * 冷知识
     */
    private String insectTrivia;

    /**
     * 变态类型：complete-完全变态，incomplete-不完全变态
     */
    private String insectMetamorphosis;

    /**
     * 生活史阶段（JSON 数组）
     */
    private String insectLifecycle;

    /**
     * 分布
     */
    private String insectRange;

    /**
     * 常见度 / 保护状态
     */
    private String insectStatus;

    /**
     * 近缘种（JSON 数组）
     */
    private String insectRelatives;

    /**
     * 主题色（hex），用于前端卡片配色
     */
    private String insectAccent;

    /**
     * 复用的 3D 建模标识（slug）
     *
     * <p>为空时用 {@link #insectCode} 自己的建模文件；指定后可复用另一个物种的模型，
     * 这样新增物种不必写 three.js 建模代码（例如本地种可先借用形态相近物种的模型）。
     * 3D 资产是程序化生成、按 slug 注册的，建模文件必须存在于
     * pages/eduai/src/insect/three/builders/ 下。</p>
     */
    private String insectModelCode;

    /**
     * 难度：1-5
     */
    private Integer insectDifficulty;

    /**
     * 归属用户 ID（服务端按登录态写入，客户端不可指定）
     */
    @TableField("insect_owner_user_id")
    private String ownerUserId;
}
