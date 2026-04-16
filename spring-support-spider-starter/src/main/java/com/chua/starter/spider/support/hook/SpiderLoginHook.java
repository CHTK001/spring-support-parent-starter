package com.chua.starter.spider.support.hook;

import com.alibaba.fastjson2.JSON;
import com.chua.common.support.ai.brain.Brain;
import com.chua.common.support.ai.brain.BrainRequest;
import com.chua.spider.Request;
import com.chua.spider.Task;
import com.chua.spider.support.brain.SpiderBrainHook;
import com.chua.spider.support.model.SpiderBrainDefinition;
import com.chua.spider.support.model.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.SpiderCredential;
import com.chua.starter.spider.support.domain.SpiderCredentialRef;
import com.chua.starter.spider.support.repository.SpiderCredentialRepository;
import com.chua.starter.spider.support.security.CredentialEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 登录钩子（beforeDownload）。
 *
 * <p>在 AI 接管模式（{@code takeover=true}）下，检测请求是否携带凭证引用，
 * 若有则解密凭证并通过 AI 大脑自动识别登录表单并填入凭证。</p>
 *
 * <p>若未处于接管模式，或任务定义中无凭证引用，则直接透传，不做任何处理。</p>
 *
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpiderLoginHook implements SpiderBrainHook {

    /** Request extra 键：凭证引用 JSON */
    public static final String EXTRA_CREDENTIAL_REF = "spider.credentialRef";

    private final SpiderCredentialRepository credentialRepository;
    private final CredentialEncryptionService encryptionService;

    @Override
    public void beforeDownload(Brain brain, SpiderTaskDefinition definition,
                               Request request, Task task) {
        if (brain == null || definition == null || request == null) {
            return;
        }

        // 仅在 AI 接管模式下生效
        SpiderBrainDefinition brainDef = definition.getBrain();
        if (brainDef == null || !Boolean.TRUE.equals(brainDef.getTakeover())) {
            return;
        }

        // 从 request extra 中读取凭证引用
        String credentialRefJson = request.getExtra(EXTRA_CREDENTIAL_REF);
        if (credentialRefJson == null || credentialRefJson.isBlank()) {
            return;
        }

        SpiderCredentialRef credentialRef;
        try {
            credentialRef = JSON.parseObject(credentialRefJson, SpiderCredentialRef.class);
        } catch (Exception e) {
            log.warn("[Spider][LoginHook] 解析凭证引用失败: {}", e.getMessage());
            return;
        }

        if (credentialRef == null || credentialRef.getCredentialId() == null) {
            return;
        }

        // 查询并解密凭证
        Long credentialId;
        try {
            credentialId = Long.parseLong(credentialRef.getCredentialId());
        } catch (NumberFormatException e) {
            log.warn("[Spider][LoginHook] 凭证 ID 格式非法: {}", credentialRef.getCredentialId());
            return;
        }

        Optional<SpiderCredential> credentialOpt = credentialRepository.findById(credentialId);
        if (credentialOpt.isEmpty()) {
            log.warn("[Spider][LoginHook] 凭证 [{}] 不存在，跳过登录处理", credentialId);
            return;
        }

        SpiderCredential credential = credentialOpt.get();
        String decryptedJson;
        try {
            decryptedJson = encryptionService.decrypt(credential.getEncryptedData());
        } catch (Exception e) {
            log.error("[Spider][LoginHook] 凭证 [{}] 解密失败: {}", credentialId, e.getMessage());
            return;
        }

        // 通过 AI 大脑识别登录表单并填入凭证
        String url = request.getUrl();
        log.info("[Spider][LoginHook] AI 接管模式，准备自动登录 url={}", url);

        try {
            Map<?, ?> credentialData = JSON.parseObject(decryptedJson, Map.class);
            String prompt = buildLoginPrompt(url, credentialData);
            brain.ask(BrainRequest.builder()
                    .prompt(prompt)
                    .build());
            log.info("[Spider][LoginHook] AI 登录指令已发送 url={}", url);
        } catch (Exception e) {
            log.warn("[Spider][LoginHook] AI 登录处理失败 url={}: {}", url, e.getMessage());
        }
    }

    private String buildLoginPrompt(String url, Map<?, ?> credentialData) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下登录页面并自动填写凭证完成登录。\n");
        sb.append("目标 URL: ").append(url).append("\n");
        sb.append("凭证信息（请勿在日志中输出）:\n");
        if (credentialData.containsKey("username")) {
            sb.append("  用户名: ").append(credentialData.get("username")).append("\n");
        }
        if (credentialData.containsKey("password")) {
            sb.append("  密码: [已提供，请直接填入表单]\n");
        }
        if (credentialData.containsKey("token")) {
            sb.append("  Token: [已提供，请直接填入表单]\n");
        }
        sb.append("请识别登录表单，填入上述凭证，并提交表单完成登录。");
        return sb.toString();
    }
}
