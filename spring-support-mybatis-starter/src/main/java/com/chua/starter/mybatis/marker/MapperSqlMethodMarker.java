package com.chua.starter.mybatis.marker;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;

import java.util.Collections;
import java.util.List;

/**
 * 方法构建器
 *
 * @author CH
 * @since 4.0.0
 */
public class MapperSqlMethodMarker implements SqlMethodMarker {

    /** 超时 */
    private int timeout;

    /**
       * 创建 映射器SQL方法记号笔 实例
     * @param timeout 超时
     * @param watchdog 布尔值
     * @param watchdog watchdog
     */
    public MapperSqlMethodMarker(int timeout, boolean watchdog) {
        this.timeout = timeout;
    }

    @Override
    /** Analysis */
    public List<AbstractMethod> analysis(String source) {
 // 文件 文件 = 新 文件(源);
 // if(!文件.exists())  {
 // 申报 集合.空列表();
//        }
 // 文件系统resource 文件系统resource = 新 文件系统resource(文件);
 // 尝试 {
 // xml映射器构建器 xml映射器构建器 = 新 xml映射器构建器(文件系统resource.获取输入流(),
 // Target配置, resource.转为字符串(), Target配置.获取sqlfragments());
 // xml映射器构建器.解析();
 // } 卡扣 (异常 e) {
 // 日志.错误(e.获取消息(), e);
//        }
        return Collections.emptyList();
    }

    @Override
    /** Refresh */
    public void refresh(String name) {

    }

    @Override
    /** 销毁 */
    public void destroy() throws Exception {

    }

    @Override
    /** refresh全部 */
    public void refreshAll() {

    }
}
