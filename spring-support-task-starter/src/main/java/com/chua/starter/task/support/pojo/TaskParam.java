package com.chua.starter.task.support.pojo;

import com.chua.common.support.collection.TypeHashMap;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 任务参数
 *
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class TaskParam extends TypeHashMap {

    public TaskParam() {
    }

    public TaskParam(Map args) {
        super(args);
    }
}
