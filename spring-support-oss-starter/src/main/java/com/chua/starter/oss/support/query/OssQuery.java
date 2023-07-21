package com.chua.starter.oss.support.query;

import com.chua.common.support.pojo.Mode;
import lombok.Data;

/**
 * query
 * @author CH
 */
@Data
public class OssQuery {

    private String bucket;
    private String path;
    private String fromPath;

    private Mode mode;
}
