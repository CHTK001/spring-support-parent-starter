package com.chua.starter.common.support.provider;

import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.database.AutoMetadata;
import com.chua.common.support.database.orm.conditions.Wrappers;
import com.chua.common.support.database.repository.Repository;
import com.chua.common.support.lang.arrange.ArrangeHandler;
import com.chua.common.support.lang.page.Page;
import com.chua.common.support.spi.Option;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.pojo.SysArrange;
import com.chua.starter.common.support.pojo.SysArrangeEdge;
import com.chua.starter.common.support.pojo.SysArrangeNode;
import com.chua.starter.common.support.result.Result;
import org.springframework.beans.BeansException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import javax.transaction.Transactional;
import java.util.List;

import static com.chua.starter.common.support.configuration.CacheConfiguration.DEFAULT_CACHE_MANAGER;

/**
 * 编排接口
 * @author CH
 */
@RestController
@RequestMapping("${plugin.arrange.context-path:/arrange}")
public class ArrangeProvider implements ApplicationContextAware {

    private Repository<SysArrange> repository;
    private Repository<SysArrangeNode> nodeRepository;
    private Repository<SysArrangeEdge> edgeRepository;

    /**
     * 所有任务
     * @return 所有任务
     */
    @GetMapping("/option")
    public Result<List<Option<String>>> option() {
        return Result.success(ServiceProvider.of(ArrangeHandler.class).options());
    }
    /**
     * 分页查询
     * @param page 页码
     * @return 结果
     */
    @GetMapping("/page")
    @Cacheable(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'arrange'")
    public Result<Page<SysArrange>> page(Page<SysArrange> page) {
        return Result.success(repository.page(page));
    }
    /**
     * 保存/更新
     * @param json 保存
     * @return 结果
     */
    @PostMapping("/saveOrUpdateNode")
    @Transactional
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "#arrangeId")
    public Result<Integer> saveOrUpdateNode(@RequestBody JSONObject json) {
        String arrangeId = json.getString("arrangeId");
        if(!repository.exist(Wrappers.<SysArrange>lambdaQuery().eq(SysArrange::getArrangeId, arrangeId))) {
            return Result.failed("任务名称不存在");
        }
        List<SysArrangeNode> nodes = BeanUtils.copyPropertiesList(json.getJSONArray("nodes"), SysArrangeNode.class);
        nodeRepository.delete(Wrappers.<SysArrangeNode>lambdaQuery().eq(SysArrangeNode::getArrangeId, arrangeId));
        nodeRepository.saveBatch(nodes);

        List<SysArrangeEdge> edges = BeanUtils.copyPropertiesList(json.getJSONArray("edges"), SysArrangeEdge.class);
        edgeRepository.delete(Wrappers.<SysArrangeEdge>lambdaQuery().eq(SysArrangeEdge::getArrangeId, arrangeId));
        edgeRepository.saveBatch(edges);

        return Result.success();
    }

    /**
     * 保存/更新
     *
     * @param arrangeId arrangeId
     * @return 结果
     */
    @GetMapping("/nodeAndEdge")
    @Transactional
    @Cacheable(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "#arrangeId")
    public Result<JSONObject> nodeAndEdge(@RequestParam("arrangeId") String arrangeId) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("nodes", nodeRepository.list(Wrappers.<SysArrangeNode>lambdaQuery().eq(SysArrangeNode::getArrangeId, arrangeId)));
        jsonObject.put("edges", edgeRepository.list(Wrappers.<SysArrangeEdge>lambdaQuery().eq(SysArrangeEdge::getArrangeId, arrangeId)));
        return Result.success(jsonObject);
    }

    /**
     * 保存/更新
     *
     * @param sysArrange 保存
     * @return 结果
     */
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'arrange'")
    @PostMapping("/saveOrUpdate")
    public Result<Integer> saveOrUpdate(@RequestBody SysArrange sysArrange) {
        if (repository.exist(Wrappers.<SysArrange>lambdaQuery()
                .eq(SysArrange::getArrangeName, sysArrange.getArrangeName())
                .ne(null != sysArrange.getArrangeId(), SysArrange::getArrangeId, sysArrange.getArrangeId()))
        ) {
            return Result.failed("任务名称不能重复");
        }
        return Result.success(repository.saveOrUpdate(sysArrange));
    }

    /**
     * 删除
     * @param id id
     * @return 结果
     */
    @GetMapping("/delete")
    @Transactional
    @CacheEvict(cacheManager = DEFAULT_CACHE_MANAGER, cacheNames = "'arrange'")
    public Result<Boolean> delete(@RequestParam("id") String id) {
        if (repository.deleteById(id) > 0) {
            edgeRepository.delete(Wrappers.<SysArrangeEdge>lambdaQuery().eq(SysArrangeEdge::getArrangeId, id));
            nodeRepository.delete(Wrappers.<SysArrangeNode>lambdaQuery().eq(SysArrangeNode::getArrangeId, id));
        }
        return Result.success(true);
    }
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        AutoMetadata autoMetadata = AutoMetadata.builder().build();
        this.repository = autoMetadata.createRepository(applicationContext.getBeansOfType(DataSource.class), SysArrange.class);
        this.nodeRepository = autoMetadata.createRepository(applicationContext.getBeansOfType(DataSource.class), SysArrangeNode.class);
        this.edgeRepository = autoMetadata.createRepository(applicationContext.getBeansOfType(DataSource.class), SysArrangeEdge.class);
    }
}
