package com.chua.starter.collect.engine;

import com.chua.starter.collect.dto.CollectParamDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 采集结果条目。
 *
 * <p>引擎产出一条记录时的载体，cells 为有序名称值对，落库时序列化为 payload JSON。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class CollectRecordEntry {

    /**
     * 产出节点ID。
     */
    private String nodeId;

    /**
     * 来源 URL。
     */
    private String url;

    /**
     * 业务主键值，非空时按同键覆盖。
     */
    private String key;

    /**
     * 字段值列表。
     */
    private List<CollectParamDTO> cells = new ArrayList<>();
}
