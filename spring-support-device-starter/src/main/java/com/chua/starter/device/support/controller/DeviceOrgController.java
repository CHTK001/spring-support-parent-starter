package com.chua.starter.device.support.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.treenode.TreeNode;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.device.support.entity.DeviceDict;
import com.chua.starter.device.support.entity.DeviceOrg;
import com.chua.starter.device.support.service.DeviceOrgService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import lombok.AllArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * 设备组织控制器
 * @author CH
 */
@RestController
@AllArgsConstructor
@RequestMapping("v1/device/org")
public class DeviceOrgController {

    private final DeviceOrgService deviceOrgService;

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("list")
    public ReturnResult<List<DeviceOrg>> list() {
        return ReturnResult.ok(deviceOrgService.list());
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("tree")
    public ReturnResult<TreeNode<DeviceOrg>> tree() {

        TreeNode<DeviceOrg> treeNodes = TreeNode.transfer(deviceOrgService.list(), new Function<DeviceOrg, TreeNode>() {
            @Override
            public TreeNode<DeviceOrg> apply(DeviceOrg deviceOrg) {
                TreeNode<DeviceOrg> objectTreeNode = new TreeNode<>();
                objectTreeNode.setPid(deviceOrg.getDeviceOrgPid());
                objectTreeNode.setId(deviceOrg.getDeviceOrgTreeId());
                objectTreeNode.setValue(deviceOrg.getDeviceOrgName());
                objectTreeNode.setExt(deviceOrg);
                return objectTreeNode;
            }
        });
        return ReturnResult.ok(treeNodes);
    }
    /**
     * 分页
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @GetMapping("page")
    public ReturnPageResult<DeviceOrg> page(
                                                   @RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                                   @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return PageResultUtils.ok(deviceOrgService.page(new Page<DeviceOrg>(pageNum, pageSize), Wrappers.<DeviceOrg>lambdaQuery()));
    }
    /**
     * 保存
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PostMapping("save")
    public ReturnResult<DeviceOrg> save(@RequestBody @Validated({AddGroup.class}) DeviceOrg deviceOrg, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        deviceOrg.setCreateTime(new Date());
        deviceOrgService.save(deviceOrg);
        return ReturnResult.ok(deviceOrg);
    }
    /**
     * 更新
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @PutMapping("update")
    public ReturnResult<DeviceOrg> update(@RequestBody @Validated({UpdateGroup.class}) DeviceOrg deviceOrg, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        deviceOrgService.updateById(deviceOrg);
        return ReturnResult.ok(deviceOrg);
    }
    /**
     * 删除
     *
     * @return {@link ReturnResult}<{@link DeviceDict}>
     */
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(String id) {
        if(StringUtils.isBlank(id)) {
            return ReturnResult.illegal("删除信息不存在");
        }

        deviceOrgService.removeBatchByIds(Splitter.on(",").trimResults().omitEmptyStrings().splitToSet(id));
        return ReturnResult.ok(true);
    }
}
