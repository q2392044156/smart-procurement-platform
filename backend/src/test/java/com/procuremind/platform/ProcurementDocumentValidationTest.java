package com.procuremind.platform;

import com.procuremind.platform.service.BusinessAttachmentService;
import com.procuremind.platform.service.PurchaseOrderService;
import com.procuremind.platform.service.RequisitionService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import com.procuremind.platform.pojo.request.AttachmentRequest;
import com.procuremind.platform.pojo.request.PurchaseOrderUpdateRequest;
import com.procuremind.platform.pojo.request.PurchaseOrderUpdateItem;
import com.procuremind.platform.pojo.request.ReasonRequest;

class ProcurementDocumentValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll static void closeFactory(){FACTORY.close();}

    @Test
    void cancellationRequiresAReason(){
        assertThat(VALIDATOR.validate(new ReasonRequest(" "))).isNotEmpty();
        assertThat(VALIDATOR.validate(new ReasonRequest("业务计划调整"))).isEmpty();
    }

    @Test
    void purchaseOrderUpdateValidatesCommercialTermsAndAllItems(){
        var valid = new PurchaseOrderUpdateRequest(1L, LocalDate.now().plusDays(1), new BigDecimal("0.13"),
                "验收合格后付款", List.of(new PurchaseOrderUpdateItem(1L, new BigDecimal("1200.00"), "测试")));
        assertThat(VALIDATOR.validate(valid)).isEmpty();

        var invalid = new PurchaseOrderUpdateRequest(null, LocalDate.now().minusDays(1), new BigDecimal("1.10"),
                "x".repeat(1001), List.of());
        assertThat(VALIDATOR.validate(invalid)).extracting(v -> v.getPropertyPath().toString())
                .contains("supplierId", "deliveryDate", "taxRate", "terms", "items");
    }

    @Test
    void attachmentRequiresExistingPositiveFileAndSupportedCategory(){
        var invalid = new AttachmentRequest(0L, "EXECUTABLE", "x".repeat(301));
        assertThat(VALIDATOR.validate(invalid)).extracting(v -> v.getPropertyPath().toString())
                .contains("fileId", "category", "note");
    }
}
