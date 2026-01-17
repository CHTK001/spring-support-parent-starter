package com.chua.starter.mybatis.marker;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;

import java.util.Collections;
import java.util.List;

/**
 * 方法构建器
 *
 * @author CH
 */
public class MapperSqlMethodMarker implements SqlMethodMarker {

    private int timeout;

    public MapperSqlMethodMarker(int timeout, boolean watchdog) {
        this.timeout = timeout;
    }

    @Override
    public List<AbstractMethod> analysis(String source) {
//        File file = new File(source);
//        if(!file.exists())  {
//            return Collections.emptyList();
//        }
//        FileSystemResource fileSystemResource = new FileSystemResource(file);
//        try {
//            XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(fileSystemResource.getInputStream(),
//                    targetConfiguration, resource.toString(), targetConfiguration.getSqlFragments());
//            xmlMapperBuilder.parse();
//        } catch (Exception e) {
//            log.error(e.getMessage(), e);
//        }
        return Collections.emptyList();
    }

    @Override
    public void refresh(String name) {

    }

    @Override
    public void destroy() throws Exception {

    }
}
