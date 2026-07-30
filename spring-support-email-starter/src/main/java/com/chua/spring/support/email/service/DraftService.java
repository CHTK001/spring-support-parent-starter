package com.chua.spring.support.email.service;

import com.chua.spring.support.email.entity.EmailDraft;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 草稿管理服务
 * 
 * @author CH
 */
@Slf4j
@Service
public class DraftService {

    // 使用内存存储（生产环境应使用数据库）
    private final Map<String, EmailDraft> draftStore = new ConcurrentHashMap<>();

    /**
     * 保存草稿
     */
    public EmailDraft saveDraft(EmailDraft draft) {
        if (draft.getId() == null || draft.getId().isEmpty()) {
            draft.setId(UUID.randomUUID().toString());
            draft.setCreatedAt(new Date());
        }
        draft.setUpdatedAt(new Date());

        draftStore.put(draft.getId(), draft);
        log.info("保存草稿: {}", draft.getId());
        return draft;
    }

    /**
     * 获取草稿列表
     */
    public List<EmailDraft> getDraftList(String accountId) {
        if (accountId == null || accountId.isEmpty()) {
            return new ArrayList<>(draftStore.values());
        }

        List<EmailDraft> drafts = new ArrayList<>();
        for (EmailDraft draft : draftStore.values()) {
            if (accountId.equals(draft.getAccountId())) {
                drafts.add(draft);
            }
        }

        // 按更新时间倒序排序
        drafts.sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()));
        return drafts;
    }

    /**
     * 获取草稿详情
     */
    public EmailDraft getDraft(String id) {
        return draftStore.get(id);
    }

    /**
     * 删除草稿
     */
    public boolean deleteDraft(String id) {
        EmailDraft removed = draftStore.remove(id);
        if (removed != null) {
            log.info("删除草稿: {}", id);
            return true;
        }
        return false;
    }

    /**
     * 批量删除草稿
     */
    public int deleteDrafts(List<String> ids) {
        int count = 0;
        for (String id : ids) {
            if (deleteDraft(id)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 清空账户的所有草稿
     */
    public int clearDrafts(String accountId) {
        List<String> idsToDelete = new ArrayList<>();
        for (EmailDraft draft : draftStore.values()) {
            if (accountId.equals(draft.getAccountId())) {
                idsToDelete.add(draft.getId());
            }
        }
        return deleteDrafts(idsToDelete);
    }
}
