package com.chua.starter.vuesql.pojo;

import com.chua.starter.vuesql.enums.Action;
import com.chua.starter.vuesql.enums.Type;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 结构
 *
 * @author CH
 */
@Data
@Builder
public class Construct {

    private Integer id;
    private String name;
    private Type type;
    private String icon;

    private Integer pid;

    @Builder.Default
    private Action action = Action.NONE;

    private List<Construct> children;
}
