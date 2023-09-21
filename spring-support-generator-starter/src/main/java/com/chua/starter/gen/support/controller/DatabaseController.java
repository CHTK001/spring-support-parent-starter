package com.chua.starter.gen.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.common.support.result.PageResult;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.service.SysGenService;
import com.chua.starter.gen.support.vo.DataSourceResult;
import io.swagger.annotations.ApiParam;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private static final String MYSQL = "mysql";


    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @GetMapping("list")
    public ReturnPageResult<SysGen> list(@ApiParam("页码") @RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                         @ApiParam("每页数量") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        List<SysGen> rs = new LinkedList<>();
        Map<String, DataSource> beansOfType = applicationContext.getBeansOfType(DataSource.class);
        for (Map.Entry<String, DataSource> entry : beansOfType.entrySet()) {
            SysGen dataSourceResult = new SysGen();
            dataSourceResult.setGenName(entry.getKey());
            dataSourceResult.setGenType("SYSTEM");

            rs.add(dataSourceResult);
        }

        rs.addAll(sysGenService.page(new Page<>(pageNum, pageSize)).getRecords());
        PageResult<SysGen> build = PageResult.<SysGen>builder()
                .data(CollectionUtils.page(pageNum, pageSize, rs))
                .total(rs.size())
                .build();
        return ReturnPageResult.<SysGen>ok(build);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("save")
    public ReturnResult<SysGen> save(SysGen sysGen, @RequestParam(value = "file", required = false) MultipartFile multipartFile) {
        if (sysGen.getGenUrl().contains(MYSQL) && !sysGen.getGenUrl().contains("?")) {
            sysGen.setGenUrl(sysGen.getGenUrl() + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true");
        }
        sysGenService.save(sysGen);
        return ReturnResult.ok(sysGen);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("update")
    public ReturnResult<SysGen> update(SysGen sysGen, @RequestParam(value = "file", required = false) MultipartFile multipartFile) {
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
