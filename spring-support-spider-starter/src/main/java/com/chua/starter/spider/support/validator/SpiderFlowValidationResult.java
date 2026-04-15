package com.chua.starter.spider.support.validator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 编排校验结果。
 *
 * @author CH
 */
public class SpiderFlowValidationResult {

    private final List<String> errors = new ArrayList<>();

    public void addError(String nodeId, String message) {
        errors.add("[" + nodeId + "] " + message);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    @Override
    public String toString() {
        return isValid() ? "VALID" : "INVALID: " + errors;
    }
}
