package com.procuremind.platform;

import com.procuremind.platform.service.SupplierService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import com.procuremind.platform.pojo.request.SupplierRequest;
import com.procuremind.platform.pojo.request.SupplierContactRequest;
import com.procuremind.platform.pojo.request.SupplierQualificationRequest;
import com.procuremind.platform.pojo.request.SupplierEvaluationRequest;

class SupplierManagementValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll static void closeFactory() { FACTORY.close(); }

    @Test
    void supplierAndContactRejectInvalidFields() {
        var supplier = new SupplierRequest("bad code", "", "", "E", "INVALID", "", "", "not-email", "x".repeat(301));
        var contact = new SupplierContactRequest("", "x".repeat(101), "", "not-email", null);

        assertThat(VALIDATOR.validate(supplier)).extracting(v -> v.getPropertyPath().toString())
                .contains("code", "name", "level", "status", "email", "address");
        assertThat(VALIDATOR.validate(contact)).extracting(v -> v.getPropertyPath().toString())
                .contains("name", "position", "email", "primaryContact");
    }

    @Test
    void qualificationAndEvaluationValidateIdentifiersAndScoreRange() {
        var qualification = new SupplierQualificationRequest("ISO 9001", 1L, LocalDate.parse("2026-01-01"), LocalDate.parse("2028-01-01"));
        var evaluation = new SupplierEvaluationRequest("2026-Q4", new BigDecimal("95"), new BigDecimal("90"), new BigDecimal("92"), "表现稳定");
        assertThat(VALIDATOR.validate(qualification)).isEmpty();
        assertThat(VALIDATOR.validate(evaluation)).isEmpty();

        var invalid = new SupplierEvaluationRequest("26-Q5", new BigDecimal("-1"), new BigDecimal("101"), null, "x".repeat(501));
        assertThat(VALIDATOR.validate(invalid)).extracting(v -> v.getPropertyPath().toString())
                .contains("period", "qualityScore", "deliveryScore", "serviceScore", "comment");
    }
}
