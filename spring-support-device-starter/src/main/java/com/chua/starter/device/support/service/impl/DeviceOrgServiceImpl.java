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
import com.chua.starter.device.support.service.DeviceLogService;
import com.chua.starter.device.support.service.DeviceOrgService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

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
    public void registerOrg(List<DeviceOrg> deviceOrgs, DeviceCloudPlatformConnector platformConnector) {
        if(CollectionUtils.isEmpty(deviceOrgs)) {
            return;
        }

        STATIC_EXECUTOR_SERVICE.execute(() -> {
            transactionTemplate.execute(new TransactionCallback<Boolean>() {
                @Override
                public Boolean doInTransaction(TransactionStatus status) {
                    remove(Wrappers.<DeviceOrg>lambdaQuery().eq(DeviceOrg::getDeviceConnectorId, platformConnector.getDeviceConnectorId()));
                    TreeNode<DeviceOrg> treeNode = TreeNode.transfer(deviceOrgs, new Function<DeviceOrg, TreeNode>() {
                        @Override
                        public TreeNode<DeviceOrg> apply(DeviceOrg deviceOrg) {
                            deviceOrg.setDeviceConnectorId(platformConnector.getDeviceConnectorId() + "");
                            TreeNode<DeviceOrg>  item = new TreeNode<>();
                            item.setId(deviceOrg.getDeviceOrgTreeId());
                            item.setPid(deviceOrg.getDeviceOrgPid());
                            item.setValue(deviceOrg.getDeviceOrgName());
                            item.setExt(deviceOrg);
                            return item;
                        }
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
                        } catch (Exception e) {
                            deviceLog.setDeviceLogError(e.getLocalizedMessage());
                        }
//                        deviceLogService.save(deviceLog);
                    }
                    return true;
                }
            });

        });
    }
}
