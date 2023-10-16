package com.chua.starter.gen.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.backup.Backup;
import com.chua.common.support.database.sqldialect.Dialect;
import com.chua.common.support.session.Session;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.common.support.utils.MultipartFileUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.entity.SysGenConfig;
import com.chua.starter.gen.support.entity.SysGenTemplate;
import com.chua.starter.gen.support.service.SysGenTemplateService;
import com.chua.starter.gen.support.vo.DataSourceResult;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 生成器控制器
 *
 * @author CH
 * @since 2023/10/15
 */
@RestController
@RequestMapping("v1/template")
public class TemplateController {

    @Resource
    private SysGenTemplateService sysGenTemplateService;


    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @GetMapping("page")
    public ReturnPageResult<SysGenTemplate> page(@RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                         @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                         @RequestParam(value = "genId") String genId
                                         ) {

        Page<SysGenTemplate> genType = sysGenTemplateService.page(
                new Page<>(pageNum, pageSize),
                new MPJLambdaWrapper<SysGenTemplate>()
                        .selectAll(SysGenTemplate.class)
                        .eq(SysGenTemplate::getGenId, genId).or().isNull(SysGenTemplate::getGenId)
        );
        return PageResultUtils.<SysGenTemplate>ok(genType);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("save")
    public ReturnResult<SysGenTemplate> save(@RequestBody SysGenTemplate sysGenTemplate) {
        sysGenTemplate.setCreateTime(new Date());
        sysGenTemplateService.save(sysGenTemplate);
        return ReturnResult.ok(sysGenTemplate);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PutMapping("update")
    public ReturnResult<SysGenTemplate> update(@RequestBody SysGenTemplate sysGenTemplate) {
        sysGenTemplate.setCreateBy(RequestUtils.getUsername());
        ServiceProvider.of(Session.class).closeKeepExtension(sysGenTemplate.getGenId() + "");

        sysGenTemplateService.updateById(sysGenTemplate);
        return ReturnResult.ok(sysGenTemplate);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(String id) {
        sysGenTemplateService.removeById(id);
        return ReturnResult.ok(true);
    }
}
