package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.model.FirewallRule;
import com.chua.starter.server.support.model.FirewallStatus;
import com.chua.starter.server.support.service.ServerFirewallService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/server/hosts/{id}/firewall")
public class ServerFirewallController {

    private final ServerFirewallService serverFirewallService;

    @GetMapping("/status")
    public ReturnResult<FirewallStatus> getStatus(@PathVariable Integer id) throws Exception {
        return ReturnResult.ok(serverFirewallService.getStatus(id));
    }

    @GetMapping("/rules")
    public ReturnResult<List<FirewallRule>> listRules(@PathVariable Integer id) throws Exception {
        return ReturnResult.ok(serverFirewallService.listRules(id));
    }

    @PostMapping("/rules")
    public ReturnResult<Void> addRule(@PathVariable Integer id, @RequestBody FirewallRule rule) throws Exception {
        serverFirewallService.addRule(id, rule);
        return ReturnResult.ok();
    }

    @PutMapping("/rules/{ruleIndex}")
    public ReturnResult<Void> updateRule(@PathVariable Integer id,
                                          @PathVariable Integer ruleIndex,
                                          @RequestBody FirewallRule rule) throws Exception {
        serverFirewallService.updateRule(id, ruleIndex, rule);
        return ReturnResult.ok();
    }

    @DeleteMapping("/rules/{ruleIndex}")
    public ReturnResult<Void> deleteRule(@PathVariable Integer id,
                                         @PathVariable Integer ruleIndex) throws Exception {
        serverFirewallService.deleteRule(id, ruleIndex);
        return ReturnResult.ok();
    }

    @PatchMapping("/rules/{ruleIndex}")
    public ReturnResult<Void> toggleRule(@PathVariable Integer id,
                                          @PathVariable Integer ruleIndex,
                                          @RequestParam boolean enabled) throws Exception {
        serverFirewallService.toggleRule(id, ruleIndex, enabled);
        return ReturnResult.ok();
    }
}
