package com.chua.starter.datasearch.provider.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一家厂商的模型目录。
 *
 * @author CH
 * @since 4.0.0.55
 */
@Data
public class ProviderModelCatalog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 厂商键
     */
    private String providerId;

    /**
     * 展示名
     */
    private String name;

    /**
     * 官方文档入口
     */
    private String doc;

    /**
     * 数据源标识（models.dev 等）
     */
    private String source;

    /**
     * 模型清单
     */
    private List<ProviderModelInfo> models = new ArrayList<>();
}
