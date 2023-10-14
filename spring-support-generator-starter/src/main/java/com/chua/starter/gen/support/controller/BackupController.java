package com.chua.starter.gen.support.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.backup.Backup;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.common.support.result.Result;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.entity.SysGenBackup;
import com.chua.starter.gen.support.service.SysGenBackupService;
import com.chua.starter.gen.support.service.SysGenService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 备份控制器
 *
 * @author CH
 */
@RestController
@RequestMapping("v1/backup")
public class BackupController {


    @Resource
    private SysGenBackupService sysGenBackupService;

    /**
     * 开始
     *
     * @param genId gen id
     * @return {@link ReturnResult}<{@link SysGenBackup}>
     */
    @GetMapping("start")
    public ReturnResult<SysGenBackup> start(Integer genId) {
        return sysGenBackupService.start(genId);
    }

    /**
     * 开始
     *
     * @param genId gen id
     * @return {@link ReturnResult}<{@link SysGenBackup}>
     */
    @GetMapping("stop")
    public ReturnResult<Boolean> stop(Integer genId) {
        return sysGenBackupService.stop(genId);
    }

    /**
     * 查询页面
     * 查询【请填写功能名称】列表
     *
     * @param pageNum  书籍页码
     * @param pageSize 页面大小
     * @return {@link ReturnPageResult}<{@link SysGenBackup}>
     */
    @GetMapping("/page")
    public ReturnPageResult<SysGenBackup> queryPage(@RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                                    @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return PageResultUtils.ok(sysGenBackupService.page(new Page<SysGenBackup>(pageNum, pageSize)));
    }

    /**
     * 获取【请填写功能名称】详细信息
     *
     * @param genId 主键
     */
    @GetMapping(value = "/info")
    public ReturnResult<SysGenBackup> getInfo(Integer genId) {
        return ReturnResult.ok(sysGenBackupService.getOne(Wrappers.<SysGenBackup>lambdaQuery().eq(SysGenBackup::getGenId, genId)));
    }

    /**
     * 新增【请填写功能名称】
     */
    @PostMapping("/save")
    public ReturnResult<SysGenBackup> save(@Validated(AddGroup.class) @RequestBody SysGenBackup sysGenBackup) {
        if(null == sysGenBackup.getGenId()) {
            return ReturnResult.illegal("配置不存在");
        }
        SysGenBackup genBackup = sysGenBackupService.getOne(Wrappers.<SysGenBackup>lambdaQuery().eq(SysGenBackup::getGenId, sysGenBackup.getGenId()));
        if(null != genBackup) {
            return ReturnResult.illegal("备份配置已存在");
        }

        sysGenBackup.setCreateTime(new Date());
        sysGenBackupService.save(sysGenBackup);
        return Result.success(sysGenBackup);
    }

    /**
     * 修改【请填写功能名称】
     */
    @PutMapping("/update")
    public ReturnResult<Boolean> update(@RequestBody SysGenBackup sysGenBackup) {
        if(null == sysGenBackup.getGenId()) {
            return ReturnResult.illegal("配置不存在");
        }
        SysGenBackup genBackup = sysGenBackupService.getOne(Wrappers.<SysGenBackup>lambdaQuery().eq(SysGenBackup::getGenId, sysGenBackup.getGenId()));
        if(null == genBackup) {
            return ReturnResult.illegal("备份配置不存在");
        }
        BeanUtils.copyProperties(sysGenBackup, genBackup);

        return Result.success(sysGenBackupService.updateById(genBackup));
    }

    /**
     * 删除【请填写功能名称】
     *
     * @param backupIds 主键串
     */
    @DeleteMapping("/delete")
    public ReturnResult<Boolean> delete(String backupIds) {
        if(null == backupIds) {
            return Result.illegal(false, "数据不存在");
        }
        return Result.success(sysGenBackupService.removeByIds(Arrays.asList(backupIds.split(","))));
    }
}
