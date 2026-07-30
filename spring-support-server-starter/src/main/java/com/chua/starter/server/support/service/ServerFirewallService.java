package com.chua.starter.server.support.service;

import com.chua.starter.server.support.model.FirewallRule;
import com.chua.starter.server.support.model.FirewallStatus;
import java.util.List;

public interface ServerFirewallService {

    FirewallStatus getStatus(Integer serverId) throws Exception;

    List<FirewallRule> listRules(Integer serverId) throws Exception;

    void addRule(Integer serverId, FirewallRule rule) throws Exception;

    void updateRule(Integer serverId, Integer ruleIndex, FirewallRule rule) throws Exception;

    void deleteRule(Integer serverId, Integer ruleIndex) throws Exception;

    void toggleRule(Integer serverId, Integer ruleIndex, boolean enabled) throws Exception;
}
