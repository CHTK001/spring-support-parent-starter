package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.entity.SysAiTavernGroup;
import com.chua.starter.ai.support.entity.SysAiTavernMember;
import com.chua.starter.ai.support.entity.SysAiTavernMessage;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.mapper.SysAiRoleMapper;
import com.chua.starter.ai.support.mapper.SysAiTavernGroupMapper;
import com.chua.starter.ai.support.mapper.SysAiTavernMemberMapper;
import com.chua.starter.ai.support.mapper.SysAiTavernMessageMapper;
import com.chua.starter.ai.support.service.TavernService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 酒馆服务实现。按当前登录用户隔离群/成员/消息。
 *
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class TavernServiceImpl implements TavernService {

    private final SysAiTavernGroupMapper groupMapper;
    private final SysAiTavernMemberMapper memberMapper;
    private final SysAiTavernMessageMapper messageMapper;
    private final SysAiRoleMapper roleMapper;

    @Override
    public List<Map<String, Object>> listGroups() {
        LambdaQueryWrapper<SysAiTavernGroup> w = new LambdaQueryWrapper<>();
        w.eq(SysAiTavernGroup::getUserId, AiUserContext.requireUserId());
        w.orderByAsc(SysAiTavernGroup::getId);
        List<SysAiTavernGroup> groups = groupMapper.selectList(w);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiTavernGroup g : groups) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", g.getId());
            row.put("name", g.getName());
            row.put("avatar", g.getAvatar());
            row.put("defaultMemberId", g.getDefaultMemberId());
            row.put("autoRoute", g.getAutoRoute());
            Long cnt = memberMapper.selectCount(new LambdaQueryWrapper<SysAiTavernMember>()
                    .eq(SysAiTavernMember::getGroupId, g.getId()));
            row.put("memberCount", cnt == null ? 0 : cnt);
            row.put("createTime", g.getCreateTime());
            result.add(row);
        }
        return result;
    }

    @Override
    public SysAiTavernGroup createGroup(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "群名称不能为空");
        }
        SysAiTavernGroup g = new SysAiTavernGroup();
        g.setName(name.trim());
        g.setAutoRoute(0);
        g.setUserId(AiUserContext.requireUserId());
        g.setCreateTime(LocalDateTime.now());
        g.setUpdateTime(LocalDateTime.now());
        groupMapper.insert(g);
        return g;
    }

    @Override
    public SysAiTavernGroup updateGroup(Long id, Map<String, Object> body) {
        SysAiTavernGroup g = requireOwned(id);
        if (body.get("name") != null) {
            g.setName(String.valueOf(body.get("name")).trim());
        }
        if (body.get("defaultMemberId") != null) {
            Object v = body.get("defaultMemberId");
            g.setDefaultMemberId(v == null || "null".equals(String.valueOf(v)) ? null : Long.valueOf(String.valueOf(v)));
        }
        if (body.get("autoRoute") != null) {
            g.setAutoRoute(parseInt(body.get("autoRoute"), g.getAutoRoute()));
        }
        g.setUpdateTime(LocalDateTime.now());
        groupMapper.updateById(g);
        return g;
    }

    @Override
    @Transactional
    /**
     * delete Group
     * @param id 长整数
     * @return 返回值
    */
    public void deleteGroup(Long id) {
        SysAiTavernGroup g = requireOwned(id);
        memberMapper.delete(new LambdaQueryWrapper<SysAiTavernMember>()
                .eq(SysAiTavernMember::getGroupId, g.getId()));
        messageMapper.delete(new LambdaQueryWrapper<SysAiTavernMessage>()
                .eq(SysAiTavernMessage::getGroupId, g.getId()));
        groupMapper.deleteById(g.getId());
    }

    @Override
    public List<Map<String, Object>> listMembers(Long groupId) {
        requireOwned(groupId);
        LambdaQueryWrapper<SysAiTavernMember> w = new LambdaQueryWrapper<>();
        w.eq(SysAiTavernMember::getGroupId, groupId).orderByAsc(SysAiTavernMember::getSort);
        List<SysAiTavernMember> members = memberMapper.selectList(w);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiTavernMember m : members) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("groupId", m.getGroupId());
            row.put("roleId", m.getRoleId());
            row.put("modelOverride", m.getModelOverride());
            row.put("randomReplyEnabled", Boolean.TRUE.equals(m.getRandomReplyEnabled()));
            row.put("randomProbability", m.getRandomProbability() == null ? 0.0 : m.getRandomProbability());
            row.put("randomContextCount", m.getRandomContextCount() == null ? 20 : m.getRandomContextCount());
            row.put("sort", m.getSort());
            SysAiRole role = roleMapper.selectById(m.getRoleId());
            if (role != null) {
                row.put("roleName", role.getName());
                row.put("roleDescription", role.getDescription());
                row.put("roleAvatar", role.getAvatar());
                row.put("roleModel", role.getModel());
                row.put("systemPrompt", role.getSystemPrompt());
                row.put("temperature", role.getTemperature());
            }
            result.add(row);
        }
        return result;
    }

    @Override
    public SysAiTavernMember addMember(Long groupId, Map<String, Object> body) {
        requireOwned(groupId);
        Integer roleId = body.get("roleId") == null ? null : Integer.valueOf(String.valueOf(body.get("roleId")));
        if (roleId == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "请选择要加入的角色");
        }
        SysAiRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "角色不存在");
        }
        Long cnt = memberMapper.selectCount(new LambdaQueryWrapper<SysAiTavernMember>()
                .eq(SysAiTavernMember::getGroupId, groupId).eq(SysAiTavernMember::getRoleId, roleId));
        if (cnt != null && cnt > 0) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该角色已在群里");
        }
        SysAiTavernMember m = new SysAiTavernMember();
        m.setGroupId(groupId);
        m.setRoleId(roleId);
        m.setModelOverride(body.get("modelOverride") == null ? null : String.valueOf(body.get("modelOverride")));
        applyMemberRandom(m, body);
        m.setSort(Math.toIntExact(memberMapper.selectCount(new LambdaQueryWrapper<SysAiTavernMember>()
                .eq(SysAiTavernMember::getGroupId, groupId))));
        m.setUserId(AiUserContext.requireUserId());
        m.setCreateTime(LocalDateTime.now());
        m.setUpdateTime(LocalDateTime.now());
        memberMapper.insert(m);
        return m;
    }

    @Override
    public void removeMember(Long id) {
        SysAiTavernMember m = memberMapper.selectById(id);
        if (m == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "成员不存在");
        }
        requireOwned(m.getGroupId());
        memberMapper.deleteById(id);
    }

    @Override
    public SysAiTavernMember updateMember(Long id, Map<String, Object> body) {
        SysAiTavernMember m = memberMapper.selectById(id);
        if (m == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "成员不存在");
        }
        requireOwned(m.getGroupId());
        // 用 containsKey 区分「未传该字段」与「显式置空」；null 或空串都表示清空（跟随默认）。
        // 置空时 updateById 默认会忽略 null，故先按非空值更新，再用 UpdateWrapper 显式 set null。
        Object mo = body.get("modelOverride");
        boolean clearingModel = body.containsKey("modelOverride") && (mo == null || String.valueOf(mo).isBlank());
        if (body.containsKey("modelOverride") && !clearingModel) {
            m.setModelOverride(String.valueOf(mo));
        }
        applyMemberRandom(m, body);
        m.setUpdateTime(LocalDateTime.now());
        memberMapper.updateById(m);
        if (clearingModel) {
            // 用原生 SQL 显式置 NULL，避免参数绑定/字段策略把 null 写成空串
            memberMapper.update(null, new LambdaUpdateWrapper<SysAiTavernMember>()
                    .eq(SysAiTavernMember::getId, id)
                    .setSql("model_override = null"));
        }
        return memberMapper.selectById(id);
    }

    /**
     * apply Member Random
     * @param Map<String 参数
     * @param body 参数
     * @param m 参数
     * @return 返回值
    */
    private void applyMemberRandom(SysAiTavernMember m, Map<String, Object> body) {
        if (body.get("randomReplyEnabled") != null) {
            m.setRandomReplyEnabled(Boolean.parseBoolean(String.valueOf(body.get("randomReplyEnabled"))));
        } else if (m.getRandomReplyEnabled() == null) {
            m.setRandomReplyEnabled(Boolean.FALSE);
        }
        if (body.get("randomProbability") != null) {
            double p = Double.parseDouble(String.valueOf(body.get("randomProbability")));
            m.setRandomProbability(Math.max(0, Math.min(100, p)));
        } else if (m.getRandomProbability() == null) {
            m.setRandomProbability(0.0);
        }
        if (body.get("randomContextCount") != null) {
            m.setRandomContextCount(parseInt(body.get("randomContextCount"), 20));
        } else if (m.getRandomContextCount() == null) {
            m.setRandomContextCount(20);
        }
    }

    @Override
    public List<SysAiTavernMessage> listMessages(Long groupId) {
        requireOwned(groupId);
        LambdaQueryWrapper<SysAiTavernMessage> w = new LambdaQueryWrapper<>();
        w.eq(SysAiTavernMessage::getGroupId, groupId).orderByAsc(SysAiTavernMessage::getId);
        return messageMapper.selectList(w);
    }

    /* ---------------- 内部 ---------------- */
    /**
     * require Owned
     * @param id 长整数
     * @return 返回值
    */
    private SysAiTavernGroup requireOwned(Long id) {
        SysAiTavernGroup g = groupMapper.selectById(id);
        if (g == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "群不存在");
        }
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        if (!AiUserContext.requireUserId().equals(g.getUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "不可操作他人的群");
        }
        return g;
    }

    /**
     * parse Int
     * @param fallback 整数
     * @param v 对象
     * @return 返回值
    */
    private static int parseInt(Object v, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }
}
