package com.chua.starter.soft.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.soft.support.entity.SoftPackage;
import com.chua.starter.soft.support.model.SoftPackageAiDraftRequest;
import com.chua.starter.soft.support.model.SoftPackageAiDraftResponse;
import com.chua.starter.soft.support.model.SoftPackageCreateRequest;
import com.chua.starter.soft.support.model.SoftGuidePreviewRequest;
import com.chua.starter.soft.support.model.SoftGuidePreviewResponse;
import com.chua.starter.soft.support.model.SoftPackageGuide;
import com.chua.starter.soft.support.model.SoftPackageVersionCopyInstallProfileRequest;
import com.chua.starter.soft.support.model.SoftPackageUpdateRequest;
import com.chua.starter.soft.support.model.SoftPackageVersionUpdateRequest;
import com.chua.starter.soft.support.service.SoftPackageAiDraftAdvisor;
import com.chua.starter.soft.support.service.SoftGuideDefinitionService;
import com.chua.starter.soft.support.service.SoftManagementService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/soft/packages")
public class SoftPackageController {

    private final SoftManagementService softManagementService;
    private final SoftGuideDefinitionService softGuideDefinitionService;
    private final SoftPackageAiDraftAdvisor softPackageAiDraftAdvisor;

    @RequestMapping(method = RequestMethod.HEAD)
    public void head() {
    }

    @GetMapping
    public ReturnResult<List<SoftPackage>> list() {
        return ReturnResult.ok(softManagementService.listPackages());
    }

    @PostMapping
    public ReturnResult<Map<String, Object>> create(@RequestBody SoftPackageCreateRequest request) {
        return ReturnResult.ok(softManagementService.createPackage(request));
    }

    @PostMapping("/ai-draft")
    public ReturnResult<SoftPackageAiDraftResponse> aiDraft(
            @RequestBody(required = false) SoftPackageAiDraftRequest request
    ) {
        return ReturnResult.ok(softPackageAiDraftAdvisor.generate(request));
    }

    @GetMapping("/{id}")
    public ReturnResult<Map<String, Object>> detail(@PathVariable Integer id) {
        return ReturnResult.ok(softManagementService.getPackageDetail(id));
    }

    @PutMapping("/{id}")
    public ReturnResult<SoftPackage> update(@PathVariable Integer id,
                                            @RequestBody SoftPackageUpdateRequest request) {
        return ReturnResult.ok(softManagementService.updatePackage(id, request));
    }

    @PutMapping("/{packageId}/versions/{versionId}")
    public ReturnResult<Map<String, Object>> updateVersion(@PathVariable Integer packageId,
                                                           @PathVariable Integer versionId,
                                                           @RequestBody SoftPackageVersionUpdateRequest request) {
        return ReturnResult.ok(Map.of(
                "package", softManagementService.requiredPackageView(packageId),
                "version", softManagementService.updatePackageVersion(packageId, versionId, request)
        ));
    }

    @PostMapping("/{packageId}/versions/{versionId}/copy-install-profile")
    public ReturnResult<Map<String, Object>> copyInstallProfile(@PathVariable Integer packageId,
                                                                @PathVariable Integer versionId,
                                                                @RequestBody SoftPackageVersionCopyInstallProfileRequest request) {
        return ReturnResult.ok(Map.of(
                "package", softManagementService.requiredPackageView(packageId),
                "version", softManagementService.copyVersionInstallProfile(packageId, versionId, request)
        ));
    }

    @GetMapping("/{id}/guide")
    public ReturnResult<SoftPackageGuide> guide(@PathVariable Integer id,
                                                Integer versionId,
                                                Integer targetId) {
        return ReturnResult.ok(softGuideDefinitionService.getGuide(id, versionId, targetId));
    }

    @GetMapping("/{id}/versions/{versionId}/guide")
    public ReturnResult<SoftPackageGuide> versionGuide(@PathVariable Integer id,
                                                       @PathVariable Integer versionId,
                                                       Integer targetId) {
        return ReturnResult.ok(softGuideDefinitionService.getGuide(id, versionId, targetId));
    }

    @PostMapping("/{id}/guide/preview")
    public ReturnResult<SoftGuidePreviewResponse> preview(@PathVariable Integer id,
                                                          @RequestBody SoftGuidePreviewRequest request) {
        return ReturnResult.ok(softGuideDefinitionService.preview(id, request == null ? null : request.getSoftPackageVersionId(), request));
    }
}
