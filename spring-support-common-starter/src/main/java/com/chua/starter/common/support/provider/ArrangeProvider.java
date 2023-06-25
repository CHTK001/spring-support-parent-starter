package com.chua.starter.common.support.provider;

import com.chua.common.support.database.AutoMetadata;
import com.chua.common.support.database.orm.conditions.Wrapper;
import com.chua.common.support.database.orm.conditions.Wrappers;
import com.chua.common.support.database.repository.Repository;
import com.chua.common.support.lang.arrange.ArrangeHandler;
import com.chua.common.support.lang.page.Page;
import com.chua.common.support.spi.Option;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.annotations.EnableAutoTable;
import com.chua.starter.common.support.pojo.SysArrange;
import com.chua.starter.common.support.result.Result;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.util.List;

/**
 * 编排接口
 * @author CH
 */
@RestController
@RequestMapping("${plugin.arrange.context-path:/arrange}")
@EnableAutoTable(packageType = SysArrange.class)
public class ArrangeProvider implements ApplicationContextAware {

    private Repository<SysArrange> repository;
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
    public Result<Page<SysArrange>> page(Page<SysArrange> page) {
        return Result.success(repository.page(page));
    }

    /**
     * 保存/更新
     * @param sysArrange 保存
     * @return 结果
     */
    @PostMapping("/saveOrUpdate")
    public Result<Integer> saveOrUpdate(@RequestBody SysArrange sysArrange) {
        if(repository.exist(Wrappers.<SysArrange>lambdaQuery()
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
    public Result<Boolean> delete(@RequestParam("id") String id) {
        return Result.success(repository.deleteById(id) > 0);
    }
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        AutoMetadata autoMetadata = AutoMetadata.builder().build();
        this.repository = autoMetadata.createRepository(applicationContext.getBeansOfType(DataSource.class), SysArrange.class);
    }
}
