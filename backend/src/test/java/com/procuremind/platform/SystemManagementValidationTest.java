package com.procuremind.platform;

import com.procuremind.platform.service.SystemManagementService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import com.procuremind.platform.pojo.request.UserCreateRequest;
import com.procuremind.platform.pojo.request.DepartmentRequest;
import com.procuremind.platform.pojo.request.RoleRequest;

class SystemManagementValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll static void closeFactory() { FACTORY.close(); }

    @Test
    void userRequiresStrongPasswordDepartmentAndRole() {
        var invalid = new UserCreateRequest("1x", "password", "", null, Set.of());
        assertThat(VALIDATOR.validate(invalid)).extracting(v -> v.getPropertyPath().toString())
                .contains("username", "password", "displayName", "departmentId", "roleIds");

        var valid = new UserCreateRequest("quality.user", "Quality@Test123", "质量专员", 7L, Set.of(10L));
        assertThat(VALIDATOR.validate(valid)).isEmpty();
    }

    @Test
    void departmentAndRoleCodesUseStableMachineFormat() {
        var department = new DepartmentRequest(1L, "bad code", "测试部门", 10);
        var role = new RoleRequest("bad-role", "测试角色", "SELF", Set.of(1L));
        assertThat(VALIDATOR.validate(department)).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
        assertThat(VALIDATOR.validate(role)).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
    }
}
