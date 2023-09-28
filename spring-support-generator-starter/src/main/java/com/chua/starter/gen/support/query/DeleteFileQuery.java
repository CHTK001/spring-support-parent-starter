package com.chua.starter.gen.support.query;

import lombok.Data;

/**
 * @author CH
 */
@Data
public class DeleteFileQuery {

    private String genId;
    private String path;
    private String type;
}
