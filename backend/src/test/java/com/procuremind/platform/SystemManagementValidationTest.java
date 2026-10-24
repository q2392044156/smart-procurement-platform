package com.procuremind.platform;

import com.procuremind.platform.system.SystemManagementService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SystemManagementValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll static void closeFactory() { FACTORY.close(); }

    @Test
    void userRequiresStrongPasswordDepartmentAndRole() {
        var invalid = new SystemManagementService.UserCreateRequest("1x", "password", "", null, Set.of());
        assertThat(VALIDATOR.validate(invalid)).extracting(v -> v.getPropertyPath().toString())
                .contains("username", "password", "displayName", "departmentId", "roleIds");

        var valid = new SystemManagementService.UserCreateRequest("quality.user", "Quality@Test123", "质量专员", 7L, Set.of(10L));
        assertThat(VALIDATOR.validate(valid)).isEmpty();
    }

    @Test
    void departmentAndRoleCodesUseStableMachineFormat() {
        var department = new SystemManagementService.DepartmentRequest(1L, "bad code", "测试部门", 10);
        var role = new SystemManagementService.RoleRequest("bad-role", "测试角色", "SELF", Set.of(1L));
        assertThat(VALIDATOR.validate(department)).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
        assertThat(VALIDATOR.validate(role)).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
    }
}
