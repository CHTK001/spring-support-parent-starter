package com.chua.starter.oss.support.pojo;

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

    private Integer pageNum = 1;
    private Integer pageSize = 10;


}
