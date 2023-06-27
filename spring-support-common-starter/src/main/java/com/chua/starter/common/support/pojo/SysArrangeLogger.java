package com.chua.starter.common.support.pojo;

import com.chua.common.support.database.entity.JdbcType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.Date;

/**
 * 编排
 *
 * @author CH
 */
@Data
@Table
@NoArgsConstructor
@AllArgsConstructor
@com.chua.common.support.database.annotation.Table(comment = "编排")
@Entity
public class SysArrangeLogger {

    @Id
    @com.chua.common.support.database.annotation.Id
    @GeneratedValue
    private Integer arrangeLoggerId;

    /**
     * 编排任务ID
     */
    @Column
    @com.chua.common.support.database.annotation.Column(comment = "编排任务ID")
    private String arrangeId;

    /**
     * 编排任务执行ID
     */
    @Column
    @com.chua.common.support.database.annotation.Column(comment = "编排任务执行ID")
    private String arrangeLoggerCode;
    /**
     * 日志内容
     */
    @Column
    @com.chua.common.support.database.annotation.Column(comment = "日志内容", jdbcType = JdbcType.LONGTEXT)
    private String arrangeLoggerContent;

    /**
     * 日志时间
     */
    @Column
    @com.chua.common.support.database.annotation.Column(comment = "日志时间")
    private Date arrangeLoggerDate;

    /**
     * 执行耗时
     */
    @Column
    @com.chua.common.support.database.annotation.Column(comment = "执行耗时")
    private Long arrangeLoggerCost;

    /**
     * 执行的任务名称
     */
    @Column
    @com.chua.common.support.database.annotation.Column(comment = "执行的任务名称")
    private String arrangeLoggerName;
}
