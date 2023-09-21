package com.chua.starter.gen.support.controller;

import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.service.SysGenService;
import com.chua.starter.gen.support.vo.DataSourceResult;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 生成器控制器
 *
 * @author CH
 */
@RestController
@RequestMapping("v1/db")
public class DatabaseController {

    @Resource
    private SysGenService sysGenService;


    @Resource
    private ApplicationContext applicationContext;


    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @GetMapping("list")
    public ReturnResult<List<DataSourceResult>> list() {
        List<DataSourceResult> rs = new LinkedList<>();
        Map<String, DataSource> beansOfType = applicationContext.getBeansOfType(DataSource.class);
        for (Map.Entry<String, DataSource> entry : beansOfType.entrySet()) {
            DataSourceResult dataSourceResult = new DataSourceResult();
            dataSourceResult.setName(entry.getKey());

            rs.add(dataSourceResult);
        }

        List<SysGen> list = sysGenService.list();
        for (SysGen sysGen : list) {
            DataSourceResult dataSourceResult = new DataSourceResult();
            dataSourceResult.setName(sysGen.getGenId() + "");
            dataSourceResult.setName(sysGen.getGenName());

            rs.add(dataSourceResult);
        }

        return ReturnResult.ok(rs);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("save")
    public ReturnResult<SysGen> save(@RequestBody SysGen sysGen) {
        sysGenService.save(sysGen);
        return ReturnResult.ok(sysGen);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("update")
    public ReturnResult<SysGen> update(@RequestBody SysGen sysGen) {
        sysGenService.updateById(sysGen);
        return ReturnResult.ok(sysGen);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @GetMapping("delete")
    public ReturnResult<Boolean> delete(String id) {
        sysGenService.removeById(id);
        return ReturnResult.ok(true);
    }

}
