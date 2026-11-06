package com.chua.starter.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.lowcode.entity.LowFormDirectory;
import com.chua.starter.lowcode.entity.LowFormPage;
import com.chua.starter.lowcode.entity.LowFormPageVersion;
import com.chua.starter.lowcode.mapper.LowFormDirectoryMapper;
import com.chua.starter.lowcode.mapper.LowFormPageMapper;
import com.chua.starter.lowcode.mapper.LowFormPageVersionMapper;
import com.chua.starter.lowcode.service.LowPageService;
import com.chua.starter.lowcode.vo.LowFormPageVO;
import com.chua.starter.lowcode.vo.LowFormPageVersionVO;
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
 * 低代码-页面管理服务实现。
 *
 * <p>页面以 pageCode 为对外标识，running 期查询（/code）与渲染（/api）都依赖该编码，
 * 因此所有返回页面的接口以 pageId 为参数，详情页/provider 查询以 pageCode 为参数，
 * 保证了唯一性。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowPageServiceImpl extends ServiceImpl<LowFormPageMapper, LowFormPage> implements LowPageService {

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

    private final LowFormPageMapper pageMapper;
    private final LowFormPageVersionMapper pageVersionMapper;
    private final LowFormDirectoryMapper directoryMapper;

    @Override
    public Page<LowFormPageVO> page(Page<LowFormPage> page, LowFormPage condition) {
        Page<LowFormPage> result = this.page(page, condition(condition));
        List<LowFormPageVO> vos = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        fillDirectoryName(vos);
        Page<LowFormPageVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public LowFormPageVO detail(Integer pageId) {
        return toVO(requirePage(pageId));
    }

    @Override
    public LowFormPageVO detailByCode(String pageCode) {
        return toVO(requirePageByCode(pageCode));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowFormPage savePage(LowFormPage page) {
        requireName(page);
        // 新增只接受业务字段：主键由数据库生成。
        // 若沿用调用方带来的 id，查重会把自己排除掉（认为是在改自己），随后撞主键。
        page.setLowPageId(null);
        requireCode(page, null);
        fillDefault(page);
        save(page);
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePage(LowFormPage page) {
        LowFormPage exists = requirePage(page.getLowPageId());
        // 局部更新（例如设计器只提交 Schema）不应强制回传编码/名称，缺失时沿用库中原值。
        if (page.getLowPageCode() == null || page.getLowPageCode().isBlank()) {
            page.setLowPageCode(exists.getLowPageCode());
        }
        if (page.getLowPageName() == null || page.getLowPageName().isBlank()) {
            page.setLowPageName(exists.getLowPageName());
        }
        requireName(page);
        requireCode(page, page.getLowPageId());
        return updateById(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePage(Integer pageId) {
        requirePage(pageId);
        pageVersionMapper.delete(Wrappers.<LowFormPageVersion>lambdaQuery()
                .eq(LowFormPageVersion::getLowPageId, pageId));
        return removeById(pageId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publishPage(Integer pageId) {
        LowFormPage page = requirePage(pageId);
        requirePublishable(page);
        // 生成版本快照：版本号按历史最大值递增，回滚后重发不会撞号。
        int versionNo = nextVersionNo(pageId);
        LowFormPageVersion version = new LowFormPageVersion();
        version.setLowPageId(page.getLowPageId());
        version.setLowPageCode(page.getLowPageCode());
        version.setLowPageVersionNo(versionNo);
        version.setLowPageVersionSchema(page.getLowPageSchema());
        version.setLowPageVersionStatus(page.getLowPageStatus());
        version.setLowPageVersionPublishStatus(PUBLISH_PUBLISHED);
        pageVersionMapper.insert(version);
        // 更新当前记录。
        LowFormPage update = new LowFormPage();
        update.setLowPageId(pageId);
        update.setLowPagePublishStatus(PUBLISH_PUBLISHED);
        update.setLowPageStatus(ENABLED);
        update.setLowPageVersion(versionNo);
        return updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackToVersion(Integer versionId) {
        LowFormPageVersion version = requireVersion(versionId);
        LowFormPage page = requirePage(version.getLowPageId());
        LowFormPage update = new LowFormPage();
        update.setLowPageId(page.getLowPageId());
        update.setLowPageSchema(version.getLowPageVersionSchema());
        update.setLowPageVersion(version.getLowPageVersionNo());
        update.setLowPagePublishStatus(PUBLISH_PUBLISHED);
        update.setLowPageStatus(ENABLED);
        return updateById(update);
    }

    @Override
    public List<LowFormPageVersionVO> versions(Integer pageId) {
        List<LowFormPageVersion> versions = pageVersionMapper.selectList(Wrappers.<LowFormPageVersion>lambdaQuery()
                .eq(LowFormPageVersion::getLowPageId, pageId)
                .orderByDesc(LowFormPageVersion::getLowPageVersionNo));
        return versions.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public String publishedSchema(String pageCode) {
        LowFormPage page = pageMapper.selectOne(Wrappers.<LowFormPage>lambdaQuery()
                .eq(LowFormPage::getLowPageCode, pageCode)
                .eq(LowFormPage::getLowPagePublishStatus, PUBLISH_PUBLISHED));
        if (page == null) {
            return null;
        }
        // 运行期只认发布快照：设计器改了已发布页面的 Schema 但没重新发布时，
        // 草稿不能透给运行期。回滚会把当前版本号写回旧值，按版本号取快照正好对齐。
        if (page.getLowPageVersion() != null) {
            LowFormPageVersion snapshot = pageVersionMapper.selectOne(Wrappers.<LowFormPageVersion>lambdaQuery()
                    .eq(LowFormPageVersion::getLowPageId, page.getLowPageId())
                    .eq(LowFormPageVersion::getLowPageVersionNo, page.getLowPageVersion())
                    .select(LowFormPageVersion::getLowPageVersionSchema)
                    .last("LIMIT 1"));
            if (snapshot != null && snapshot.getLowPageVersionSchema() != null) {
                return snapshot.getLowPageVersionSchema();
            }
        }
        return page.getLowPageSchema();
    }

    /**
     * 构建查询条件。
     *
     * @param condition 条件
     * @return 查询条件
     */
    private LambdaQueryWrapper<LowFormPage> condition(LowFormPage condition) {
        LambdaQueryWrapper<LowFormPage> wrapper = Wrappers.lambdaQuery();
        if (condition == null) {
            return wrapper;
        }
        wrapper.like(condition.getLowPageCode() != null, LowFormPage::getLowPageCode, condition.getLowPageCode());
        wrapper.like(condition.getLowPageName() != null, LowFormPage::getLowPageName, condition.getLowPageName());
        wrapper.eq(condition.getLowPageDirectoryId() != null,
                LowFormPage::getLowPageDirectoryId, condition.getLowPageDirectoryId());
        wrapper.eq(condition.getLowPageStatus() != null,
                LowFormPage::getLowPageStatus, condition.getLowPageStatus());
        wrapper.eq(condition.getLowPagePublishStatus() != null,
                LowFormPage::getLowPagePublishStatus, condition.getLowPagePublishStatus());
        wrapper.orderByDesc(LowFormPage::getLowPageId);
        return wrapper;
    }

    /**
     * 按本页面回显目录名称。
     *
     * @param vos 出参
     */
    private void fillDirectoryName(List<LowFormPageVO> vos) {
        Set<Integer> ids = vos.stream().map(LowFormPageVO::getLowPageDirectoryId)
                .distinct().filter(id -> id != null).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        List<LowFormDirectory> dirs = directoryMapper.selectBatchIds(ids);
        java.util.Map<Integer, String> nameMap = dirs.stream()
                .collect(Collectors.toMap(LowFormDirectory::getLowFormDirectoryId,
                        LowFormDirectory::getLowFormDirectoryName, (a, b) -> a));
        for (LowFormPageVO vo : vos) {
            vo.setLowPageDirectoryName(nameMap.getOrDefault(vo.getLowPageDirectoryId(), ""));
        }
    }

    /**
     * 填充默认值。
     *
     * @param page 页面
     */
    private void fillDefault(LowFormPage page) {
        if (page.getLowPageStatus() == null) {
            page.setLowPageStatus(DISABLED);
        }
        if (page.getLowPagePublishStatus() == null) {
            page.setLowPagePublishStatus(PUBLISH_UNPUBLISHED);
        }
        if (page.getLowPageVersion() == null) {
            page.setLowPageVersion(0);
        }
        if (page.getLowPageDirectoryId() == null) {
            page.setLowPageDirectoryId(ROOT_DIRECTORY_ID);
        }
    }

    /**
     * 按主键要页面，不存在直接拒绝。
     *
     * @param pageId 页面ID
     * @return 页面
     */
    private LowFormPage requirePage(Integer pageId) {
        if (pageId == null) {
            throw new IllegalArgumentException("页面ID不能为空");
        }
        LowFormPage page = getById(pageId);
        if (page == null) {
            throw new IllegalArgumentException("页面不存在: " + pageId);
        }
        return page;
    }

    /**
     * 按编码取页面，不存在直接拒绝。
     *
     * @param pageCode 页面编码
     * @return 页面
     */
    private LowFormPage requirePageByCode(String pageCode) {
        if (pageCode == null || pageCode.isBlank()) {
            throw new IllegalArgumentException("页面编码不能为空");
        }
        LowFormPage page = pageMapper.selectOne(Wrappers.<LowFormPage>lambdaQuery()
                .eq(LowFormPage::getLowPageCode, pageCode)
                .select(LowFormPage::getLowPageId, LowFormPage::getLowPageCode, LowFormPage::getLowPageName,
                        LowFormPage::getLowPageDesc, LowFormPage::getLowPageDirectoryId, LowFormPage::getLowPageSchema,
                        LowFormPage::getLowPageStatus, LowFormPage::getLowPagePublishStatus, LowFormPage::getLowPageVersion,
                        LowFormPage::getLowPageRemark, LowFormPage::getCreateTime, LowFormPage::getUpdateTime));
        if (page == null) {
            throw new IllegalArgumentException("页面不存在: " + pageCode);
        }
        return page;
    }

    /**
     * 校验名称必填。
     *
     * @param page 页面
     */
    private void requireName(LowFormPage page) {
        if (page.getLowPageName() == null || page.getLowPageName().isBlank()) {
            throw new IllegalArgumentException("页面名称不能为空");
        }
    }

    /**
     * 校验页面编码的唯一性。
     *
     * @param page      页面
     * @param excludeId 需要排除的主键（更新时传自身主键，新增传 null）
     */
    private void requireCode(LowFormPage page, Integer excludeId) {
        if (page.getLowPageCode() == null || page.getLowPageCode().isBlank()) {
            throw new IllegalArgumentException("页面编码不能为空");
        }
        LambdaQueryWrapper<LowFormPage> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(LowFormPage::getLowPageCode, page.getLowPageCode());
        if (excludeId != null) {
            wrapper.ne(LowFormPage::getLowPageId, excludeId);
        }
        long count = pageMapper.selectCount(wrapper);
        if (count > 0) {
            throw new IllegalArgumentException("该页面编码已存在");
        }
    }

    /**
     * 校验发布条件。
     *
     * <p>只校验「有没有可发布的 Schema」。启用状态由发布动作本身负责置位，
     * 重复发布用于产生新版本，因此这里不拦截已发布记录。</p>
     *
     * @param page 页面
     */
    private void requirePublishable(LowFormPage page) {
        if (page.getLowPageSchema() == null || page.getLowPageSchema().isBlank()) {
            throw new IllegalArgumentException("没有页面 Schema 不能发布");
        }
    }

    /**
     * 下一个版本号：取该页面已有历史版本的最大版本号 +1。
     *
     * <p>不用当前记录的 version +1：回滚会把当前 version 写回旧值，
     * 直接用它会插出重复版本号，历史里出现两个同号版本。</p>
     *
     * @param pageId 页面ID
     * @return 版本号
     */
    private int nextVersionNo(Integer pageId) {
        LowFormPageVersion latest = pageVersionMapper.selectOne(Wrappers.<LowFormPageVersion>lambdaQuery()
                .eq(LowFormPageVersion::getLowPageId, pageId)
                .orderByDesc(LowFormPageVersion::getLowPageVersionNo)
                .last("LIMIT 1"));
        return latest == null || latest.getLowPageVersionNo() == null
                ? 1 : latest.getLowPageVersionNo() + 1;
    }

    /**
     * 按主键要版本，不存在直接拒绝。
     *
     * @param versionId 版本ID
     * @return 版本
     */
    private LowFormPageVersion requireVersion(Integer versionId) {
        if (versionId == null) {
            throw new IllegalArgumentException("版本ID不能为空");
        }
        LowFormPageVersion version = pageVersionMapper.selectById(versionId);
        if (version == null) {
            throw new IllegalArgumentException("版本不存在: " + versionId);
        }
        return version;
    }

    /**
     * 实体转出参。
     *
     * @param page 页面
     * @return 出参
     */
    private LowFormPageVO toVO(LowFormPage page) {
        LowFormPageVO vo = new LowFormPageVO();
        vo.setLowPageId(page.getLowPageId());
        vo.setLowPageCode(page.getLowPageCode());
        vo.setLowPageName(page.getLowPageName());
        vo.setLowPageDesc(page.getLowPageDesc());
        vo.setLowPageDirectoryId(page.getLowPageDirectoryId());
        vo.setLowPageDirectoryName("");
        vo.setLowPageStatus(page.getLowPageStatus());
        vo.setLowPagePublishStatus(page.getLowPagePublishStatus());
        vo.setLowPageVersion(page.getLowPageVersion());
        vo.setLowPageRemark(page.getLowPageRemark());
        vo.setCreateTime(format(page.getCreateTime()));
        vo.setUpdateTime(format(page.getUpdateTime()));
        return vo;
    }

    /**
     * 版本转出参。
     *
     * @param version 版本
     * @return 出参
     */
    private LowFormPageVersionVO toVO(LowFormPageVersion version) {
        LowFormPageVersionVO vo = new LowFormPageVersionVO();
        vo.setLowPageVersionId(version.getLowPageVersionId());
        vo.setLowPageId(version.getLowPageId());
        vo.setLowPageCode(version.getLowPageCode());
        vo.setLowPageVersionNo(version.getLowPageVersionNo());
        vo.setLowPageVersionSchema(version.getLowPageVersionSchema());
        vo.setLowPageVersionStatus(version.getLowPageVersionStatus());
        vo.setLowPageVersionPublishStatus(version.getLowPageVersionPublishStatus());
        vo.setLowPageVersionRemark(version.getLowPageVersionRemark());
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
