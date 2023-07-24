package com.chua.starter.oss.support.query;

import lombok.Data;

/**
 * 预览
 * @author CH
 */
@Data
public class DeepQuery {

    private String ossBucket;

    private String name;

    private String path;
    private String ossId;

    private Integer page = 1;
    private Integer pageSize = 10;


}
