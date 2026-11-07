package com.chua.starter.template.support.model;

import com.chua.starter.template.support.entity.TemplateProvider;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 模板提供商（对外模型）
 *
 * <p>与前端 {@code ProviderInfo} 一一对应：{@code templateTypes} 在库里以逗号串存储，
 * 出参时拆成数组；入参时再合并回去。</p>
 *
 * @since 4.0.0.80
 * @author CH
 */
@Data
public class TemplateProviderInfo {

    /** 提供商 ID */
    private String id;

    /** 提供商名称 */
    private String name;

    /** 接入方：aliyun / tencent / self */
    private String vendor;

    /** Access Key */
    private String ak;

    /** Secret Key */
    private String sk;

    /** 服务端点 */
    private String endpoint;

    /** 支持的模板类型 */
    private List<String> templateTypes = new ArrayList<>();

    /** 是否支持自定义模板设计 */
    private Boolean customDesign;

    /** 状态：online / offline */
    private String status;

    /** 绑定的来源同步器标识：message / paper / 空 */
    private String source;

    /** 该提供商下的模板数（聚合统计，不入库） */
    private Long templateCount;

    /** 最近一次同步时间 */
    private LocalDateTime lastSyncAt;

    /** 负责人 */
    private String owner;

    /** 说明 */
    private String remark;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 实体转对外模型
     *
     * @param entity 提供商实体
     * @return 对外模型
     */
    public static TemplateProviderInfo of(TemplateProvider entity) {
        TemplateProviderInfo info = new TemplateProviderInfo();
        if (entity == null) {
            return info;
        }
        info.setId(entity.getTemplateProviderId());
        info.setName(entity.getTemplateProviderName());
        info.setVendor(entity.getTemplateProviderVendor());
        info.setAk(entity.getTemplateProviderAk());
        info.setSk(entity.getTemplateProviderSk());
        info.setEndpoint(entity.getTemplateProviderEndpoint());
        info.setTemplateTypes(splitTypes(entity.getTemplateProviderTypes()));
        info.setCustomDesign(Boolean.TRUE.equals(entity.getTemplateProviderCustomDesign()));
        info.setStatus(entity.getTemplateProviderStatus());
        info.setSource(entity.getTemplateProviderSource());
        info.setLastSyncAt(entity.getTemplateProviderLastSyncAt());
        info.setOwner(entity.getTemplateProviderOwner());
        info.setRemark(entity.getTemplateProviderRemark());
        info.setUpdatedAt(entity.getTemplateProviderUpdatedAt());
        return info;
    }

    /**
     * 对外模型转实体
     *
     * @param id 提供商 ID（新增时为空，由调用方生成）
     * @return 提供商实体
     */
    public TemplateProvider toEntity(String id) {
        TemplateProvider entity = new TemplateProvider();
        entity.setTemplateProviderId(id);
        entity.setTemplateProviderName(name);
        entity.setTemplateProviderVendor(vendor);
        entity.setTemplateProviderAk(ak);
        entity.setTemplateProviderSk(sk);
        entity.setTemplateProviderEndpoint(endpoint);
        entity.setTemplateProviderTypes(joinTypes(templateTypes));
        entity.setTemplateProviderCustomDesign(Boolean.TRUE.equals(customDesign));
        entity.setTemplateProviderStatus(status == null || status.trim().isEmpty() ? "online" : status);
        entity.setTemplateProviderSource(source);
        entity.setTemplateProviderLastSyncAt(lastSyncAt);
        entity.setTemplateProviderOwner(owner);
        entity.setTemplateProviderRemark(remark);
        return entity;
    }

    /**
     * 逗号串拆成类型列表
     *
     * @param types 逗号分隔的类型串
     * @return 类型列表，空串返回空列表
     */
    public static List<String> splitTypes(String types) {
        if (types == null || types.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String item : Arrays.asList(types.split(","))) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 类型列表合并为逗号串
     *
     * @param types 类型列表
     * @return 逗号分隔的类型串，空列表返回 {@code null}
     */
    public static String joinTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return null;
        }
        List<String> cleaned = new ArrayList<>(types.size());
        for (String item : types) {
            if (item != null && !item.trim().isEmpty()) {
                cleaned.add(item.trim());
            }
        }
        return cleaned.isEmpty() ? null : String.join(",", cleaned);
    }
}
