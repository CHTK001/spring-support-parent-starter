package com.chua.starter.monitor.server.query;

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
