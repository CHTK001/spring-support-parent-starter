package com.chua.payment.support;

import com.chua.payment.support.dto.MerchantDTO;
import com.chua.payment.support.service.MerchantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = PaymentTestApplication.class,
        properties = {
                "plugin.payment.scheduler.enabled=false",
                "plugin.payment.ops.enabled=false"
        })
class MerchantServiceDiagnosticTest {

    @Autowired
    private MerchantService merchantService;

    @Test
    void createMerchant() {
        MerchantDTO dto = new MerchantDTO();
        dto.setMerchantName("diag-merchant");
        dto.setContactName("diag");
        dto.setContactPhone("13800138000");
        merchantService.createMerchant(dto);
    }
}
