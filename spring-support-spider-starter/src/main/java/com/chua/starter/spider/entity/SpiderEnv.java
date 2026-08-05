package com.chua.starter.spider.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 环境变量实体。
 *
 * <p>对应 {@code spider_env} 表，供爬虫在运行期通过 {@code {{env.KEY}}}
 * 语法在 URL / 请求体 / 提取表达式等位置插值使用。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("spider_env")
public class SpiderEnv extends SpiderBase {

    /**
     * 变量名（如 COOKIE、TOKEN）
     */
    private String envKey;

    /**
     * 变量值
     */
    private String envValue;

    /**
     * 描述
     */
    private String description;

    /**
     * 是否启用（1 启用 / 0 停用）
     */
    private Integer enabled;
}