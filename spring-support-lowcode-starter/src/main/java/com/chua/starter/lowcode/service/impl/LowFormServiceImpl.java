package com.chua.starter.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.lowcode.entity.LowForm;
import com.chua.starter.lowcode.entity.LowFormDirectory;
import com.chua.starter.lowcode.entity.LowFormVersion;
import com.chua.starter.lowcode.mapper.LowFormDirectoryMapper;
import com.chua.starter.lowcode.mapper.LowFormMapper;
import com.chua.starter.lowcode.mapper.LowFormVersionMapper;
import com.chua.starter.lowcode.service.LowFormService;
import com.chua.starter.lowcode.vo.LowFormVO;
import com.chua.starter.lowcode.vo.LowFormVersionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 低代码-表单管理服务实现。
 *
 * <p>以 formCode 为对外标识，running 期查询（/code）与渲染（/api）都依赖该编码，
 * 因此所有返回页面的接口以 formId 为参数，详情页/provider 查询以 formCode 为参数，
 * 保证了唯一性。</p>
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowFormServiceImpl extends ServiceImpl<LowFormMapper, LowForm> implements LowFormService {

    /**
     * 停用。
     */
    private static final int DISABLED = 0;

    /**
     * 启用。
     */
    private static final int ENABLED = 1;

    /**
     * 未发布。
     */
    private static final int PUBLISH_UNPUBLISHED = 0;

    /**
     * 已发布。
     */
    private static final int PUBLISH_PUBLISHED = 1;

    /**
     * 日期格式。
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 默认目录ID（根目录）。
     */
    private static final int ROOT_DIRECTORY_ID = 1;

    private final LowFormMapper formMapper;
    private final LowFormVersionMapper formVersionMapper;
    private final LowFormDirectoryMapper directoryMapper;

    @Override
    public Page<LowFormVO> page(Page<LowForm> page, LowForm condition) {
        Page<LowForm> result = this.page(page, condition(condition));
        List<LowFormVO> vos = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        fillDirectoryName(vos);
        Page<LowFormVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public LowFormVO detail(Integer formId) {
        return toVO(requireForm(formId));
    }

    @Override
    public LowFormVO detailByCode(String formCode) {
        return toVO(requireFormByCode(formCode));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowForm saveForm(LowForm form) {
        requireName(form);
        // 新增只接受业务字段：主键由数据库生成。
        // 若沿用调用方带来的 id，查重会把自己排除掉（认为是在改自己），随后撞主键。
        form.setLowFormId(null);
        requireCode(form, null);
        fillDefault(form);
        save(form);
        return form;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateForm(LowForm form) {
        LowForm exists = requireForm(form.getLowFormId());
        // 局部更新（例如设计器只提交 Schema）不应强制回传编码/名称，缺失时沿用库中原值。
        if (form.getLowFormCode() == null || form.getLowFormCode().isBlank()) {
            form.setLowFormCode(exists.getLowFormCode());
        }
        if (form.getLowFormName() == null || form.getLowFormName().isBlank()) {
            form.setLowFormName(exists.getLowFormName());
        }
        requireName(form);
        requireCode(form, form.getLowFormId());
        return updateById(form);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteForm(Integer formId) {
        requireForm(formId);
        formVersionMapper.delete(Wrappers.<LowFormVersion>lambdaQuery()
                .eq(LowFormVersion::getLowFormId, formId));
        return removeById(formId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publishForm(Integer formId) {
        LowForm form = requireForm(formId);
        requirePublishable(form);
        // 生成版本快照：版本号按历史最大值递增，回滚后重发不会撞号。
        int versionNo = nextVersionNo(formId);
        LowFormVersion version = new LowFormVersion();
        version.setLowFormId(form.getLowFormId());
        version.setLowFormCode(form.getLowFormCode());
        version.setLowFormVersionNo(versionNo);
        version.setLowFormVersionSchema(form.getLowFormSchema());
        version.setLowFormVersionStatus(form.getLowFormStatus());
        version.setLowFormVersionPublishStatus(PUBLISH_PUBLISHED);
        formVersionMapper.insert(version);
        // 更新当前记录。
        LowForm update = new LowForm();
        update.setLowFormId(formId);
        update.setLowFormPublishStatus(PUBLISH_PUBLISHED);
        update.setLowFormStatus(ENABLED);
        update.setLowFormVersion(versionNo);
        return updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackToVersion(Integer versionId) {
        LowFormVersion version = requireVersion(versionId);
        LowForm form = requireForm(version.getLowFormId());
        LowForm update = new LowForm();
        update.setLowFormId(form.getLowFormId());
        update.setLowFormSchema(version.getLowFormVersionSchema());
        update.setLowFormVersion(version.getLowFormVersionNo());
        update.setLowFormPublishStatus(PUBLISH_PUBLISHED);
        update.setLowFormStatus(ENABLED);
        return updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Integer formId, Integer status) {
        requireForm(formId);
        if (status == null || (status != ENABLED && status != DISABLED)) {
            throw new IllegalArgumentException("状态只能是 0(停用) 或 1(启用)");
        }
        LowForm update = new LowForm();
        update.setLowFormId(formId);
        update.setLowFormStatus(status);
        return updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unpublish(Integer formId) {
        requireForm(formId);
        LowForm update = new LowForm();
        update.setLowFormId(formId);
        update.setLowFormPublishStatus(PUBLISH_UNPUBLISHED);
        return updateById(update);
    }

    @Override
    public List<LowFormVersionVO> versions(Integer formId) {
        List<LowFormVersion> versions = formVersionMapper.selectList(Wrappers.<LowFormVersion>lambdaQuery()
                .eq(LowFormVersion::getLowFormId, formId)
                .orderByDesc(LowFormVersion::getLowFormVersionNo));
        return versions.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public String publishedSchema(String formCode) {
        LowForm form = formMapper.selectOne(Wrappers.<LowForm>lambdaQuery()
                .eq(LowForm::getLowFormCode, formCode)
                .eq(LowForm::getLowFormPublishStatus, PUBLISH_PUBLISHED));
        if (form == null) {
            return null;
        }
        // 停用的表单对外不可见：停用语义必须同时约束运行期取数，否则「停用」只是列表上的一个标记。
        if (form.getLowFormStatus() == null || form.getLowFormStatus() != ENABLED) {
            return null;
        }
        // 运行期只认发布快照：设计器改了已发布表单的 Schema 但没重新发布时，
        // 草稿不能透给运行期。回滚会把当前版本号写回旧值，按版本号取快照正好对齐。
        if (form.getLowFormVersion() != null) {
            LowFormVersion snapshot = formVersionMapper.selectOne(Wrappers.<LowFormVersion>lambdaQuery()
                    .eq(LowFormVersion::getLowFormId, form.getLowFormId())
                    .eq(LowFormVersion::getLowFormVersionNo, form.getLowFormVersion())
                    .select(LowFormVersion::getLowFormVersionSchema)
                    .last("LIMIT 1"));
            if (snapshot != null && snapshot.getLowFormVersionSchema() != null) {
                return snapshot.getLowFormVersionSchema();
            }
        }
        return form.getLowFormSchema();
    }

    /**
     * 构建查询条件。
     *
     * @param condition 条件
     * @return 查询条件
     */
    private LambdaQueryWrapper<LowForm> condition(LowForm condition) {
        LambdaQueryWrapper<LowForm> wrapper = Wrappers.lambdaQuery();
        if (condition == null) {
            return wrapper;
        }
        wrapper.like(condition.getLowFormCode() != null, LowForm::getLowFormCode, condition.getLowFormCode());
        wrapper.like(condition.getLowFormName() != null, LowForm::getLowFormName, condition.getLowFormName());
        wrapper.eq(condition.getLowFormDirectoryId() != null,
                LowForm::getLowFormDirectoryId, condition.getLowFormDirectoryId());
        wrapper.eq(condition.getLowFormStatus() != null,
                LowForm::getLowFormStatus, condition.getLowFormStatus());
        wrapper.eq(condition.getLowFormPublishStatus() != null,
                LowForm::getLowFormPublishStatus, condition.getLowFormPublishStatus());
        wrapper.orderByDesc(LowForm::getLowFormId);
        return wrapper;
    }

    /**
     * 按本页面回显目录名称。
     *
     * @param vos 出参
     */
    private void fillDirectoryName(List<LowFormVO> vos) {
        Set<Integer> ids = vos.stream().map(LowFormVO::getLowFormDirectoryId)
                .distinct().filter(id -> id != null).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        List<LowFormDirectory> dirs = directoryMapper.selectBatchIds(ids);
        java.util.Map<Integer, String> nameMap = dirs.stream()
                .collect(Collectors.toMap(LowFormDirectory::getLowFormDirectoryId,
                        LowFormDirectory::getLowFormDirectoryName, (a, b) -> a));
        for (LowFormVO vo : vos) {
            vo.setLowFormDirectoryName(nameMap.getOrDefault(vo.getLowFormDirectoryId(), ""));
        }
    }

    /**
     * 填充默认值。
     *
     * @param form 表单
     */
    private void fillDefault(LowForm form) {
        if (form.getLowFormStatus() == null) {
            form.setLowFormStatus(DISABLED);
        }
        if (form.getLowFormPublishStatus() == null) {
            form.setLowFormPublishStatus(PUBLISH_UNPUBLISHED);
        }
        if (form.getLowFormVersion() == null) {
            form.setLowFormVersion(0);
        }
        if (form.getLowFormDirectoryId() == null) {
            form.setLowFormDirectoryId(ROOT_DIRECTORY_ID);
        }
    }

    /**
     * 按主键要表单，不存在直接拒绝。
     *
     * @param formId 表单ID
     * @return 表单
     */
    private LowForm requireForm(Integer formId) {
        if (formId == null) {
            throw new IllegalArgumentException("表单ID不能为空");
        }
        LowForm form = getById(formId);
        if (form == null) {
            throw new IllegalArgumentException("表单不存在: " + formId);
        }
        return form;
    }

    /**
     * 按编码取表单，不存在直接拒绝。
     *
     * @param formCode 表单编码
     * @return 表单
     */
    private LowForm requireFormByCode(String formCode) {
        if (formCode == null || formCode.isBlank()) {
            throw new IllegalArgumentException("表单编码不能为空");
        }
        LowForm form = formMapper.selectOne(Wrappers.<LowForm>lambdaQuery()
                .eq(LowForm::getLowFormCode, formCode)
                .select(LowForm::getLowFormId, LowForm::getLowFormCode, LowForm::getLowFormName,
                        LowForm::getLowFormDesc, LowForm::getLowFormDirectoryId, LowForm::getLowFormSchema,
                        LowForm::getLowFormStatus, LowForm::getLowFormPublishStatus, LowForm::getLowFormVersion,
                        LowForm::getLowFormRemark, LowForm::getCreateTime, LowForm::getUpdateTime));
        if (form == null) {
            throw new IllegalArgumentException("表单不存在: " + formCode);
        }
        return form;
    }

    /**
     * 校验名称必填。
     *
     * @param form 表单
     */
    private void requireName(LowForm form) {
        if (form.getLowFormName() == null || form.getLowFormName().isBlank()) {
            throw new IllegalArgumentException("表单名称不能为空");
        }
    }

    /**
     * 校验表单编码的唯一性。
     *
     * @param form      表单
     * @param excludeId 需要排除的主键（更新时传自身主键，新增传 null）
     */
    private void requireCode(LowForm form, Integer excludeId) {
        if (form.getLowFormCode() == null || form.getLowFormCode().isBlank()) {
            throw new IllegalArgumentException("表单编码不能为空");
        }
        LambdaQueryWrapper<LowForm> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(LowForm::getLowFormCode, form.getLowFormCode());
        if (excludeId != null) {
            wrapper.ne(LowForm::getLowFormId, excludeId);
        }
        long count = formMapper.selectCount(wrapper);
        if (count > 0) {
            throw new IllegalArgumentException("该表单编码已存在");
        }
    }

    /**
     * 校验发布条件。
     *
     * <p>只校验「有没有可发布的 Schema」。启用状态由发布动作本身负责置位，
     * 重复发布用于产生新版本，因此这里不拦截已发布记录。</p>
     *
     * @param form 表单
     */
    private void requirePublishable(LowForm form) {
        if (form.getLowFormSchema() == null || form.getLowFormSchema().isBlank()) {
            throw new IllegalArgumentException("没有表单 Schema 不能发布");
        }
    }

    /**
     * 下一个版本号：取该表单已有历史版本的最大版本号 +1。
     *
     * <p>不用当前记录的 version +1：回滚会把当前 version 写回旧值，
     * 直接用它会插出重复版本号，历史里出现两个同号版本。</p>
     *
     * @param formId 表单ID
     * @return 版本号
     */
    private int nextVersionNo(Integer formId) {
        LowFormVersion latest = formVersionMapper.selectOne(Wrappers.<LowFormVersion>lambdaQuery()
                .eq(LowFormVersion::getLowFormId, formId)
                .orderByDesc(LowFormVersion::getLowFormVersionNo)
                .last("LIMIT 1"));
        return latest == null || latest.getLowFormVersionNo() == null
                ? 1 : latest.getLowFormVersionNo() + 1;
    }

    /**
     * 按主键要版本，不存在直接拒绝。
     *
     * @param versionId 版本ID
     * @return 版本
     */
    private LowFormVersion requireVersion(Integer versionId) {
        if (versionId == null) {
            throw new IllegalArgumentException("版本ID不能为空");
        }
        LowFormVersion version = formVersionMapper.selectById(versionId);
        if (version == null) {
            throw new IllegalArgumentException("版本不存在: " + versionId);
        }
        return version;
    }

    /**
     * 实体转出参。
     *
     * @param form 表单
     * @return 出参
     */
    private LowFormVO toVO(LowForm form) {
        LowFormVO vo = new LowFormVO();
        vo.setLowFormId(form.getLowFormId());
        vo.setLowFormCode(form.getLowFormCode());
        vo.setLowFormName(form.getLowFormName());
        vo.setLowFormDesc(form.getLowFormDesc());
        vo.setLowFormDirectoryId(form.getLowFormDirectoryId());
        vo.setLowFormDirectoryName("");
        vo.setLowFormStatus(form.getLowFormStatus());
        vo.setLowFormPublishStatus(form.getLowFormPublishStatus());
        vo.setLowFormVersion(form.getLowFormVersion());
        vo.setLowFormRemark(form.getLowFormRemark());
        vo.setCreateTime(format(form.getCreateTime()));
        vo.setUpdateTime(format(form.getUpdateTime()));
        return vo;
    }

    /**
     * 版本转出参。
     *
     * @param version 版本
     * @return 出参
     */
    private LowFormVersionVO toVO(LowFormVersion version) {
        LowFormVersionVO vo = new LowFormVersionVO();
        vo.setLowFormVersionId(version.getLowFormVersionId());
        vo.setLowFormId(version.getLowFormId());
        vo.setLowFormCode(version.getLowFormCode());
        vo.setLowFormVersionNo(version.getLowFormVersionNo());
        vo.setLowFormVersionSchema(version.getLowFormVersionSchema());
        vo.setLowFormVersionStatus(version.getLowFormVersionStatus());
        vo.setLowFormVersionPublishStatus(version.getLowFormVersionPublishStatus());
        vo.setLowFormVersionRemark(version.getLowFormVersionRemark());
        vo.setCreateTime(format(version.getCreateTime()));
        return vo;
    }

    /**
     * 格式化为字符串。
     *
     * @param time 时间
     * @return 字符串
     */
    private String format(LocalDateTime time) {
        return time == null ? null : time.format(FORMATTER);
    }
}
