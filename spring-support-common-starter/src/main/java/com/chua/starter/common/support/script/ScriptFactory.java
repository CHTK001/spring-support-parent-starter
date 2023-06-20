package com.chua.starter.common.support.script;

import com.chua.starter.common.support.annotations.EnableAutoTable;
import com.chua.starter.common.support.script.jpa.SysScriptRepository;
import com.chua.starter.common.support.script.pojo.SysScript;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 脚本工厂
 * @author CH
 */
@EnableJpaRepositories(basePackageClasses = SysScriptRepository.class)
@EnableAutoTable(packageType = SysScript.class)
public class ScriptFactory {
}
