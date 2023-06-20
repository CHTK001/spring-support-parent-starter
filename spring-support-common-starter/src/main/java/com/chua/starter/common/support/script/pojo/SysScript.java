package com.chua.starter.common.support.script.pojo;

import com.chua.common.support.database.annotation.Column;
import com.chua.common.support.database.annotation.Table;
import com.chua.starter.common.support.script.enums.ScriptType;
import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

/**
 * 系统脚本
 * @author CH
 */
@Data
@Entity
@javax.persistence.Table(name = "sys_script")
@Table(comment = "系统脚本")
public class SysScript {
    /**
     * id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer scriptId;

    /**
     * 脚本名称
     */
    @javax.persistence.Column
    @Column(comment = "脚本名称")
    private String scriptName;

    /**
     * 脚本内容
     */
    @javax.persistence.Column
    @Column(comment = "脚本内容")
    private String scriptContent;
    /**
     * 脚本内容
     */
    @javax.persistence.Column
    @Column(comment = "脚本类型")
    private ScriptType scriptType;
    /**
     * 接口
     */
    @javax.persistence.Column
    @Column(comment = "脚本接口")
    private String scriptInterface;
    /**
     * 优先级
     */
    @javax.persistence.Column
    @Column(comment = "优先级")
    private Integer scriptSort;
    /**
     * 是否暂停
     */
    @javax.persistence.Column
    @Column(comment = "是否暂停;0:暂停")
    private Integer scriptStatus;
}
