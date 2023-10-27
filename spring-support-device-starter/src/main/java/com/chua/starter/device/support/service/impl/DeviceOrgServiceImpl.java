package com.chua.starter.device.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.treenode.TreeNode;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.device.support.entity.DeviceCloudPlatformConnector;
import com.chua.starter.device.support.entity.DeviceLog;
import com.chua.starter.device.support.entity.DeviceOrg;
import com.chua.starter.device.support.mapper.DeviceOrgMapper;
import com.chua.starter.device.support.pojo.StaticResult;
import com.chua.starter.device.support.service.DeviceLogService;
import com.chua.starter.device.support.service.DeviceOrgService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 *    
 * @author CH
 */     
@Service
public class DeviceOrgServiceImpl extends ServiceImpl<DeviceOrgMapper, DeviceOrg> implements DeviceOrgService{
    public static final ExecutorService STATIC_EXECUTOR_SERVICE = ThreadUtils.newFixedThreadExecutor(100);
    @Resource
    private DeviceLogService deviceLogService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Override
    public void registerOrg(List<DeviceOrg> deviceOrgs, DeviceCloudPlatformConnector platformConnector, StaticResult result) {
        if(CollectionUtils.isEmpty(deviceOrgs)) {
            return;
        }

        result.addTotal(deviceOrgs.size());
        transactionTemplate.execute(status -> {
            remove(Wrappers.<DeviceOrg>lambdaQuery().eq(DeviceOrg::getDeviceConnectorId, platformConnector.getDeviceConnectorId()));
            TreeNode<DeviceOrg> treeNode = TreeNode.transfer(deviceOrgs, deviceOrg -> {
                deviceOrg.setDeviceConnectorId(platformConnector.getDeviceConnectorId() + "");
                TreeNode<DeviceOrg>  item = new TreeNode<>();
                item.setId(deviceOrg.getDeviceOrgTreeId());
                item.setPid(deviceOrg.getDeviceOrgPid());
                item.setValue(deviceOrg.getDeviceOrgName());
                item.setExt(deviceOrg);
                return item;
            });
            List<TreeNode<DeviceOrg>> children = treeNode.getChildren();
            for (TreeNode<DeviceOrg> child : children) {
                DeviceLog deviceLog = new DeviceLog();
                deviceLog.setDeviceLogFrom("同步组织接口(页面)");
                deviceLog.setCreateTime(new Date());
                deviceLog.setDeviceLogType("SYNC("+ platformConnector.getDeviceConnectorId() +")");
                try {
                    DeviceOrg childExt = child.getExt();
//                            save(childExt);
                    result.addSuccessTotal(1);
                } catch (Exception e) {
                    result.addFailureTotal(1);
                    deviceLog.setDeviceLogError(e.getLocalizedMessage());
                }
//                        deviceLogService.save(deviceLog);
            }
            return true;
        });

    }
}
