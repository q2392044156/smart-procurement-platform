package com.procuremind.platform;

import com.procuremind.platform.masterdata.MasterDataService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MasterDataValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll static void closeFactory() { FACTORY.close(); }

    @Test
    void warehouseUnitAndBrandRejectInvalidInput() {
        var warehouse = new MasterDataService.WarehouseRequest("bad code", "", "x".repeat(301), "x".repeat(101));
        var unit = new MasterDataService.UnitRequest("bad-code", "", 7);
        var brand = new MasterDataService.BrandRequest("1BAD", "");

        assertThat(VALIDATOR.validate(warehouse)).extracting(v -> v.getPropertyPath().toString())
                .contains("code", "name", "address", "manager");
        assertThat(VALIDATOR.validate(unit)).extracting(v -> v.getPropertyPath().toString())
                .contains("code", "name", "precisionScale");
        assertThat(VALIDATOR.validate(brand)).extracting(v -> v.getPropertyPath().toString())
                .contains("code", "name");
    }

    @Test
    void categoryAndMaterialValidateStableCodesAndRanges() {
        var attribute = new MasterDataService.AttributeRequest("voltage", "额定电压", "NUMBER", true, null, 1);
        var category = new MasterDataService.CategoryRequest(null, "MOTOR_PARTS", "电机配件", "测试分类", 10, List.of(attribute));
        var material = new MasterDataService.MaterialRequest(
                "MOTOR.001", "测试电机", 1L, 1L, null, "380V", "SERIAL", new BigDecimal("1.00"), Map.of("voltage", 380));

        assertThat(VALIDATOR.validate(category)).isEmpty();
        assertThat(VALIDATOR.validate(material)).isEmpty();

        var invalidMaterial = new MasterDataService.MaterialRequest(
                "bad code", "", 0L, 0L, -1L, "x".repeat(501), "INVALID", new BigDecimal("-1"), Map.of());
        assertThat(VALIDATOR.validate(invalidMaterial)).extracting(v -> v.getPropertyPath().toString())
                .contains("code", "name", "categoryId", "unitId", "brandId", "specification", "trackingMode", "minStock");
    }
}
