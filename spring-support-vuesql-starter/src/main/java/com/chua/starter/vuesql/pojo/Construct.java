package com.chua.starter.vuesql.pojo;

import com.chua.starter.vuesql.enums.Type;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

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

    private List<Construct> children;
}
