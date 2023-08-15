package com.chua.starter.config.server.support.repository;

import com.chua.common.support.bean.BeanMap;
import com.chua.common.support.json.Json;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.server.support.base.ConfigurationRepository;
import com.chua.starter.config.server.support.protocol.ProtocolServer;
import com.chua.starter.config.server.support.query.DetailUpdate;
import com.google.common.base.Splitter;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * TConfigurationCenterInfo
 *
 * @author CH
 * @since 2022/8/1 13:06
 */
@SuppressWarnings("ALL")
@Repository("bean")
public interface ConfigurationBeanInfoRepository extends ConfigurationRepository<ConfigurationBeanInfo> {


    @Override
    default void analysis(String applicationName, Map<String, Object> data) {
        ConfigurationRepository configurationRepository = ServiceProvider.of(ConfigurationRepository.class).getExtension(ConfigConstant.CONFIG);
        if(null != configurationRepository) {
            data.put(ConfigConstant.APPLICATION_DATA_TYPE, ConfigConstant.BEAN);
            configurationRepository.analysis(applicationName, data);
        }
    }


    @Override
    default String getSubscribe(String subscribe, String profile) {
        Specification<ConfigurationBeanInfo> specification = new Specification<ConfigurationBeanInfo>() {
            @Override
            public Predicate toPredicate(Root<ConfigurationBeanInfo> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                List<Predicate> predicateList = new ArrayList<>();
                CriteriaBuilder.In<String> configApplicationName = criteriaBuilder.in(
                        root.get("beanApplicationName").as(String.class));
                for (String value : Splitter.on(',').omitEmptyStrings().trimResults().splitToList(subscribe)) {
                    configApplicationName.value(value);
                }

                predicateList.add(configApplicationName);
                predicateList.add(criteriaBuilder.equal(root.get("beanProfile"), profile));
                predicateList.add(criteriaBuilder.equal(root.get("disable"), 0));
                return query.where(predicateList.toArray(new Predicate[0])).getRestriction();
            }
        };
        List<ConfigurationBeanInfo> list = findAll(specification);
        Map<String, List<ConfigurationBeanInfo>> valueMapping = new HashMap<>();
        for (ConfigurationBeanInfo info : list) {
            valueMapping.computeIfAbsent(info.getBeanApplicationName(), it -> new ArrayList<>()).add(info);
        }
        Map<String, Map<String, Object>> rs = new HashMap<>();

        for (Map.Entry<String, List<ConfigurationBeanInfo>> entry : valueMapping.entrySet()) {
            String key = entry.getKey();
            List<ConfigurationBeanInfo> entryValue = entry.getValue();
            rs.put(key + ".data", render(key, entryValue));
        }

        return Json.toJson(rs);
    }
    /**
     * 渲染数据
     *
     * @param key        标识
     * @param entryValue 权限
     * @return 数据
     */
    default Map<String, Object> render(String key, List<ConfigurationBeanInfo> entryValue) {
        Map<String, Object> rs1 = new HashMap<>(1);

        List<Map<String, Object>> rs = new LinkedList<>();
        rs1.put(key, rs);

        for (ConfigurationBeanInfo tConfigurationCenterInfo : entryValue) {
            Map<String, Object> item = new HashMap<>(entryValue.size());
            try {
                String beanFilePath = tConfigurationCenterInfo.getBeanFilePath();
                try {
                    item.put(ConfigConstant.FILE_TYPE_CONTENT, IoUtils.toByteArray(new FileInputStream(beanFilePath)));
                } catch (IOException e) {
                }
                item.put(ConfigConstant.FILE_TYPE, FileUtils.getExtension(beanFilePath));
                item.putAll(BeanMap.create(tConfigurationCenterInfo));

                rs.add(item);
            } catch (Exception e) {
            }
        }
        return rs1;
    }
    @Override
    default void unregister(String applicationName) {

    }

    @Override
    default Page<?> findAll(String profile, Pageable pageable) {
        ConfigurationBeanInfo configurationBeanInfo = new ConfigurationBeanInfo();
        configurationBeanInfo.setBeanProfile(profile);
        configurationBeanInfo.setDisable(null);
        return findAll(Example.of(configurationBeanInfo), pageable);
    }


    /**
     * 环境
     *
     * @return 环境
     */
    @Query(value = "SELECT bean_profile FROM CONFIGURATION_BEAN_INFO GROUP BY bean_profile ", nativeQuery = true)
    Set<String> profile();

    /**
     * 环境
     *
     * @return 环境
     */
    @Query(value = "SELECT bean_application_name FROM CONFIGURATION_BEAN_INFO GROUP BY bean_application_name ", nativeQuery = true)
    Set<String> applications();

    @Override
    default void notifyConfig(ProtocolServer protocolServer, ConfigurationSubscribeInfo subscribeInfo, Object configValue) {

    }

    @Override
    default Object getDetail(String configId) {
        ConfigurationBeanInfo configurationBeanInfo = getById(Integer.valueOf(configId));
        if(null == configurationBeanInfo) {
            return null;
        }

        try {
            return IoUtils.toString(new File(configurationBeanInfo.getBeanFilePath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    default Object detailUpdate(DetailUpdate detailUpdate) {
        ConfigurationBeanInfo configurationBeanInfo = getById(Integer.valueOf(detailUpdate.getConfigId()));
        if(null == configurationBeanInfo) {
            return null;
        }

        try (FileOutputStream fos = new FileOutputStream(configurationBeanInfo.getBeanFilePath())){
            IoUtils.write(detailUpdate.getContent(), fos, StandardCharsets.UTF_8);
            return configurationBeanInfo.getBeanProfile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
