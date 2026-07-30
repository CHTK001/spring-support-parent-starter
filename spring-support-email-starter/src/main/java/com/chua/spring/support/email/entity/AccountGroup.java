package com.chua.spring.support.email.entity;

import lombok.Data;
import java.util.Date;

/**
 * 账户分组实体
 * 
 * @author CH
 */
@Data
public class AccountGroup {
    private String id;
    private String name;
    private String description;
    private String color;
    private Integer sortOrder;
    private Date createdAt;
    private Date updatedAt;
}
