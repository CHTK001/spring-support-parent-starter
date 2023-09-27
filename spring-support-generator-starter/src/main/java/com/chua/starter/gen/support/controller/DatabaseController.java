package com.chua.starter.gen.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.service.SysGenService;
import com.chua.starter.gen.support.vo.DataSourceResult;
import com.chua.starter.mybatis.utils.PageResultUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 生成器控制器
 *
 * @author CH
 */
@RestController
@RequestMapping("gen/v1/db")
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
    public ReturnPageResult<SysGen> list(@RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                         @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {

        return PageResultUtils.<SysGen>ok(sysGenService.page(new Page<>(pageNum, pageSize)));
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
        sysGen.setCreateTime(new Date());
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
