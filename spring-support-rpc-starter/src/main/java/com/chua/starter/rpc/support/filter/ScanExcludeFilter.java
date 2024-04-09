package com.chua.starter.rpc.support.filter;

import com.chua.starter.rpc.support.holder.ServicePackagesHolder;
import lombok.Getter;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

import java.io.IOException;

/**
 * 扫描排除筛选器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/04/08
 */
@Getter
public class ScanExcludeFilter implements TypeFilter {

    private int excludedCount;

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
        String className = metadataReader.getClassMetadata().getClassName();
        boolean excluded = ServicePackagesHolder.getInstance().isClassScanned(className);
        if (excluded) {
            excludedCount++;
        }
        return excluded;
    }

}
