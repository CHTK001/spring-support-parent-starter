package com.chua.starter.soft.support.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chua.starter.ai.support.chat.ChatClient;
import com.chua.starter.soft.support.model.SoftPackageAiDraftRequest;
import com.chua.starter.soft.support.model.SoftPackageAiDraftResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class SoftPackageAiDraftAdvisorTest {

    @Test
    void shouldFallbackWhenChatClientMissing() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ObjectProvider<ChatClient> provider = beanFactory.getBeanProvider(ChatClient.class);
        SoftPackageAiDraftAdvisor advisor = new SoftPackageAiDraftAdvisor(provider, new ObjectMapper());

        SoftPackageAiDraftRequest request = new SoftPackageAiDraftRequest();
        request.setPrompt("请生成 mysql 软件安装草稿");
        request.setPackageName("MySQL Community");
        request.setOsType("LINUX");
        request.setArchitecture("AMD64");
        request.setIntegrateServerService(Boolean.TRUE);

        SoftPackageAiDraftResponse response = advisor.generate(request);

        assertNotNull(response);
        assertFalse(Boolean.TRUE.equals(response.getAiGenerated()));
        assertTrue(response.getPackageCode() != null && !response.getPackageCode().isBlank());
        assertTrue(response.getPackageName() != null && !response.getPackageName().isBlank());
        assertTrue(response.getVersionCode() != null && !response.getVersionCode().isBlank());
        assertTrue(response.getInstallScript() != null && !response.getInstallScript().isBlank());
        assertTrue(response.getStartScript() != null && !response.getStartScript().isBlank());
        assertTrue(response.getStopScript() != null && !response.getStopScript().isBlank());
        assertTrue(response.getUninstallScript() != null && !response.getUninstallScript().isBlank());
    }
}
