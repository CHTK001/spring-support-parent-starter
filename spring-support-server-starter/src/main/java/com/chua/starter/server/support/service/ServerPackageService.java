package com.chua.starter.server.support.service;

import com.chua.starter.server.support.model.InstalledPackage;
import com.chua.starter.server.support.model.PackageManagerInfo;
import com.chua.starter.server.support.model.PackageUpgradeResult;
import com.chua.starter.server.support.model.UpgradablePackage;
import java.util.List;

public interface ServerPackageService {

    PackageManagerInfo checkPackageManager(Integer serverId) throws Exception;

    List<UpgradablePackage> listUpgradable(Integer serverId) throws Exception;

    PackageUpgradeResult upgrade(Integer serverId, String packageName) throws Exception;

    List<PackageUpgradeResult> upgradeAll(Integer serverId) throws Exception;

    List<InstalledPackage> listInstalled(Integer serverId, String keyword) throws Exception;

    List<InstalledPackage> search(Integer serverId, String keyword) throws Exception;

    PackageUpgradeResult install(Integer serverId, String packageName) throws Exception;
}
