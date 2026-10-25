package com.procuremind.platform;

import com.procuremind.platform.file.BusinessAttachmentService;
import com.procuremind.platform.purchaseorder.PurchaseOrderService;
import com.procuremind.platform.requisition.RequisitionService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcurementDocumentValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll static void closeFactory(){FACTORY.close();}

    @Test
    void cancellationRequiresAReason(){
        assertThat(VALIDATOR.validate(new RequisitionService.ReasonRequest(" "))).isNotEmpty();
        assertThat(VALIDATOR.validate(new PurchaseOrderService.ReasonRequest("业务计划调整"))).isEmpty();
    }

    @Test
    void purchaseOrderUpdateValidatesCommercialTermsAndAllItems(){
        var valid = new PurchaseOrderService.UpdateRequest(1L, LocalDate.now().plusDays(1), new BigDecimal("0.13"),
                "验收合格后付款", List.of(new PurchaseOrderService.UpdateItem(1L, new BigDecimal("1200.00"), "测试")));
        assertThat(VALIDATOR.validate(valid)).isEmpty();

        var invalid = new PurchaseOrderService.UpdateRequest(null, LocalDate.now().minusDays(1), new BigDecimal("1.10"),
                "x".repeat(1001), List.of());
        assertThat(VALIDATOR.validate(invalid)).extracting(v -> v.getPropertyPath().toString())
                .contains("supplierId", "deliveryDate", "taxRate", "terms", "items");
    }

    @Test
    void attachmentRequiresExistingPositiveFileAndSupportedCategory(){
        var invalid = new BusinessAttachmentService.AttachmentRequest(0L, "EXECUTABLE", "x".repeat(301));
        assertThat(VALIDATOR.validate(invalid)).extracting(v -> v.getPropertyPath().toString())
                .contains("fileId", "category", "note");
    }
}
