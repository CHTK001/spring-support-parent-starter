package com.chua.starter.spider.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.spider.support.domain.SpiderWorkbenchTab;
import com.chua.starter.spider.support.repository.SpiderWorkbenchTabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Workbench Tab 接口。
 *
 * @author CH
 */
@RestController
@RequestMapping("/v1/spider/workbench/tabs")
@RequiredArgsConstructor
public class SpiderWorkbenchController {

    private final SpiderWorkbenchTabRepository tabRepository;

    /** GET /v1/spider/workbench/tabs */
    @GetMapping
    public ReturnResult<?> listTabs() {
        return ReturnResult.ok(tabRepository.findAllOrdered());
    }

    /** POST /v1/spider/workbench/tabs */
    @PostMapping
    public ReturnResult<?> createTab(@RequestBody SpiderWorkbenchTab tab) {
        tabRepository.save(tab);
        return ReturnResult.ok(tab);
    }

    /** PUT /v1/spider/workbench/tabs/{tabId} */
    @PutMapping("/{tabId}")
    public ReturnResult<?> updateTab(@PathVariable Long tabId,
                                     @RequestBody SpiderWorkbenchTab tab) {
        tab.setId(tabId);
        boolean updated = tabRepository.updateById(tab);
        if (!updated) {
            return ReturnResult.illegal("Tab [" + tabId + "] 不存在");
        }
        return ReturnResult.ok(tab);
    }

    /** DELETE /v1/spider/workbench/tabs/{tabId} */
    @DeleteMapping("/{tabId}")
    public ReturnResult<?> deleteTab(@PathVariable Long tabId) {
        boolean removed = tabRepository.removeById(tabId);
        if (!removed) {
            return ReturnResult.illegal("Tab [" + tabId + "] 不存在");
        }
        return ReturnResult.ok();
    }
}
