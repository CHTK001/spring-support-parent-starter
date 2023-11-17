package com.chua.starter.unified.server.support.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.unified.server.support.entity.UnifiedExecuter;
import com.chua.starter.unified.server.support.entity.UnifiedProfile;
import com.chua.starter.unified.server.support.service.UnifiedExecuterService;
import com.chua.starter.unified.server.support.service.UnifiedProfileService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author CH
 */
@RestController
@RequestMapping("v1/profile")
@AllArgsConstructor
public class UnifiedProfileController {


    private final UnifiedProfileService unifiedProfileService;
    private final UnifiedExecuterService unifiedExecuterService;


    /**
     * 配置
     *
     * @return {@link ReturnResult}<{@link List}<{@link UnifiedProfile}>>
     */
    @GetMapping("profile")
    public ReturnResult<List<UnifiedProfile>> config() {

        return ReturnResult.ok(unifiedProfileService.list(Wrappers
                .<UnifiedProfile>lambdaQuery()
                .eq(UnifiedProfile::getUnifiedProfileType, "ENV")
        ));
    }

    /**
     * 应用程序
     *
     * @return {@link ReturnResult}<{@link List}<{@link UnifiedExecuter}>>
     */
    @GetMapping("applications")
    public ReturnResult<List<UnifiedExecuter>> applications() {
        return ReturnResult.ok(unifiedExecuterService.list(Wrappers
                .<UnifiedExecuter>lambdaQuery()
        ));
    }
}
