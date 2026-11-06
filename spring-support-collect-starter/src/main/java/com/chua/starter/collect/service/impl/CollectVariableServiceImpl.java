package com.chua.starter.collect.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.collect.dto.CollectParamDTO;
import com.chua.starter.collect.entity.CollectVariable;
import com.chua.starter.collect.mapper.CollectVariableMapper;
import com.chua.starter.collect.service.CollectVariableService;
import com.chua.starter.collect.support.CollectSecrets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 采集全局变量服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class CollectVariableServiceImpl extends ServiceImpl<CollectVariableMapper, CollectVariable>
        implements CollectVariableService {

    /**
     * 变量名规则：字母或下划线开头，仅字母数字下划线，最长 64。
     */
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    /**
     * 明文存储。
     */
    private static final int PLAIN = 0;

    /**
     * 密文存储。
     */
    private static final int CIPHER = 1;

    @Override
    public List<CollectVariable> listMasked() {
        List<CollectVariable> list = list(Wrappers.<CollectVariable>lambdaQuery()
                .orderByAsc(CollectVariable::getCollectVariableName));
        for (CollectVariable variable : list) {
            if (isCipher(variable)) {
                variable.setCollectVariableValue(CollectSecrets.MASK);
            }
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectVariable addVariable(CollectVariable variable) {
        String name = requireName(variable.getCollectVariableName());
        checkNameUnique(name, null);
        variable.setCollectVariableId(null);
        variable.setCollectVariableName(name);
        boolean cipher = isCipher(variable);
        variable.setCollectVariableEncrypted(cipher ? CIPHER : PLAIN);
        variable.setCollectVariableValue(cipher
                ? CollectSecrets.encode(blankToEmpty(variable.getCollectVariableValue()))
                : blankToEmpty(variable.getCollectVariableValue()));
        save(variable);
        return maskOne(variable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean editVariable(CollectVariable variable) {
        CollectVariable exists = requireVariable(variable.getCollectVariableId());
        String name = requireName(variable.getCollectVariableName() == null
                ? exists.getCollectVariableName() : variable.getCollectVariableName());
        checkNameUnique(name, exists.getCollectVariableId());
        variable.setCollectVariableName(name);
        boolean cipher = variable.getCollectVariableEncrypted() == null
                ? isCipher(exists) : isCipher(variable);
        variable.setCollectVariableEncrypted(cipher ? CIPHER : PLAIN);
        String value = variable.getCollectVariableValue();
        if (CollectSecrets.isMasked(value) || value == null) {
            // 前端回显的是占位符，说明这次没有改值
            variable.setCollectVariableValue(exists.getCollectVariableValue());
        } else {
            variable.setCollectVariableValue(cipher ? CollectSecrets.encode(value) : value);
        }
        return updateById(variable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteVariable(Integer variableId) {
        requireVariable(variableId);
        return removeById(variableId);
    }

    @Override
    public List<CollectParamDTO> runtimeVariables() {
        List<CollectParamDTO> list = new ArrayList<>();
        for (CollectVariable variable : list()) {
            String name = variable.getCollectVariableName();
            if (name == null || name.isBlank()) {
                continue;
            }
            String value = isCipher(variable)
                    ? CollectSecrets.decode(variable.getCollectVariableValue())
                    : blankToEmpty(variable.getCollectVariableValue());
            list.add(new CollectParamDTO(name.trim(), value, null));
        }
        return list;
    }

    /**
     * 按主键取变量。
     *
     * @param variableId 变量ID
     * @return 变量
     */
    private CollectVariable requireVariable(Integer variableId) {
        if (variableId == null) {
            throw new IllegalArgumentException("变量ID不能为空");
        }
        CollectVariable variable = getById(variableId);
        if (variable == null) {
            throw new IllegalArgumentException("变量不存在: " + variableId);
        }
        return variable;
    }

    /**
     * 校验变量名。
     *
     * @param name 变量名
     * @return 规整后的变量名
     */
    private String requireName(String name) {
        String text = name == null ? "" : name.trim();
        if (!NAME.matcher(text).matches()) {
            throw new IllegalArgumentException("变量名只能是字母、数字或下划线，且不能以数字开头: " + name);
        }
        return text;
    }

    /**
     * 校验变量名唯一。
     *
     * @param name      变量名
     * @param exceptId  排除的变量ID
     */
    private void checkNameUnique(String name, Integer exceptId) {
        long same = count(Wrappers.<CollectVariable>lambdaQuery()
                .eq(CollectVariable::getCollectVariableName, name)
                .ne(exceptId != null, CollectVariable::getCollectVariableId, exceptId));
        if (same > 0) {
            throw new IllegalArgumentException("变量名已存在: " + name);
        }
    }

    /**
     * 是否密文存储。
     *
     * @param variable 变量
     * @return 密文返回 true
     */
    private boolean isCipher(CollectVariable variable) {
        return variable.getCollectVariableEncrypted() != null && variable.getCollectVariableEncrypted() == CIPHER;
    }

    /**
     * 单个变量脱敏。
     *
     * @param variable 变量
     * @return 同一个变量
     */
    private CollectVariable maskOne(CollectVariable variable) {
        if (isCipher(variable)) {
            variable.setCollectVariableValue(CollectSecrets.MASK);
        }
        return variable;
    }

    /**
     * 空值转空串。
     *
     * @param value 原值
     * @return 非空字符串
     */
    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
