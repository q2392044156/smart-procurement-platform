package com.procuremind.platform.pojo.request;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record KnowledgeUpdateRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Pattern(regexp = "POLICY|PROCEDURE|CONTRACT|SUPPLIER|MANUAL|TEMPLATE|OTHER") String category,
            @Size(max = 500) String summary,
            @Size(max = 300) String tags,
            Long ownerDepartmentId,
            @NotBlank @Pattern(regexp = "ALL|DEPARTMENT|DEPARTMENT_AND_CHILDREN|SELF|CUSTOM") String visibility,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            List<KnowledgeAclEntry> acl) {}
