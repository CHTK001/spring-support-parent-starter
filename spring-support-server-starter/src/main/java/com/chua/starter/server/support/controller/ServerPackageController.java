package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.model.InstalledPackage;
import com.chua.starter.server.support.model.PackageManagerInfo;
import com.chua.starter.server.support.model.PackageUpgradeResult;
import com.chua.starter.server.support.model.UpgradablePackage;
import com.chua.starter.server.support.service.ServerPackageService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/server/hosts/{id}/packages")
public class ServerPackageController {

    private final ServerPackageService serverPackageService;

    @GetMapping("/manager")
    public ReturnResult<PackageManagerInfo> checkPackageManager(@PathVariable("id") Integer id) throws Exception {
        return ReturnResult.ok(serverPackageService.checkPackageManager(id));
    }

    @GetMapping("/upgradable")
    public ReturnResult<List<UpgradablePackage>> listUpgradable(@PathVariable("id") Integer id) throws Exception {
        return ReturnResult.ok(serverPackageService.listUpgradable(id));
    }

    @PostMapping("/upgrade")
    public ReturnResult<PackageUpgradeResult> upgrade(@PathVariable("id") Integer id,
                                                       @RequestBody Map<String, String> body) throws Exception {
        String packageName = body.get("name");
        return ReturnResult.ok(serverPackageService.upgrade(id, packageName));
    }

    @PostMapping("/upgrade-all")
    public ReturnResult<List<PackageUpgradeResult>> upgradeAll(@PathVariable("id") Integer id) throws Exception {
        return ReturnResult.ok(serverPackageService.upgradeAll(id));
    }

    @GetMapping("/installed")
    public ReturnResult<List<InstalledPackage>> listInstalled(
            @PathVariable("id") Integer id,
            @RequestParam(value = "keyword", required = false) String keyword) throws Exception {
        return ReturnResult.ok(serverPackageService.listInstalled(id, keyword));
    }

    @GetMapping("/search")
    public ReturnResult<List<InstalledPackage>> search(
            @PathVariable("id") Integer id,
            @RequestParam("keyword") String keyword) throws Exception {
        return ReturnResult.ok(serverPackageService.search(id, keyword));
    }

    @PostMapping("/install")
    public ReturnResult<PackageUpgradeResult> install(@PathVariable("id") Integer id,
                                                       @RequestBody Map<String, String> body) throws Exception {
        String packageName = body.get("name");
        return ReturnResult.ok(serverPackageService.install(id, packageName));
    }
}
