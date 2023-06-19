package com.chua.starter.task.support.configuration;

import com.chua.starter.common.support.annotations.EnableAutoTable;
import com.chua.starter.task.support.pojo.SystemTask;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author CH
 */
@ComponentScan("com.chua.starter.task.support")
@MapperScan("com.chua.starter.task.support.mapper")
@EnableAutoTable(packageType = SystemTask.class)
public class TaskConfiguration {
}
