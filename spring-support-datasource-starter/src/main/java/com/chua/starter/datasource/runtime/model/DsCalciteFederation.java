package com.chua.starter.datasource.runtime.model;

import lombok.Data;

import java.util.List;

/**
 * Calcite 联邦配置实体（表 ds_calcite_federation）。
 *
 * <p>一条记录 = 一个对外暴露的 Calcite schema 聚合配置；成员连接参数由前端
 * 从数据源台账展开后存入 members JSON，后端不依赖任何台账模块。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
public class DsCalciteFederation {

    /**
     * 主键
     */
    private Long id;

    /**
     * 联邦对外 schema 名（Calcite 根 schema 下的子 schema 名）
     */
    private String schemaName;

    /**
     * 默认 schema 名（SQL 中未限定 schema 时使用的成员；可为空）
     */
    private String defaultSchema;

    /**
     * 是否启用（0 否 1 是）
     */
    private Integer enabled = 1;

    /**
     * 成员连接参数 JSON：[{schemaName,url,username,password,driverClassName}]
     */
    private String members;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间（epoch millis）
     */
    private Long createdAt;

    /**
     * 更新时间（epoch millis）
     */
    private Long updatedAt;

    /**
     * 乐观锁版本号（更新成功时自增；保存须携带读取时的旧值，不匹配则拒绝写入）
     */
    private Integer version;

    /**
     * 反序列化后的成员列表（不落库，便于服务层/页面透传）
     */
    private List<FederationMember> memberList;
}
