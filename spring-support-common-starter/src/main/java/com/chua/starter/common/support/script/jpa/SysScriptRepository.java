package com.chua.starter.common.support.script.jpa;

import com.chua.starter.common.support.script.pojo.SysScript;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

/**
 * 脚本
 * @author CH
 */
@Repository
public interface SysScriptRepository extends CrudRepository<SysScript, Integer>, PagingAndSortingRepository<SysScript, Integer> {
}
