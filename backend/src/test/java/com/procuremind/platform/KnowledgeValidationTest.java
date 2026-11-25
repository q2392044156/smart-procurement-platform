package com.procuremind.platform;

import com.procuremind.platform.service.KnowledgeService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import com.procuremind.platform.pojo.request.KnowledgeCreateRequest;
import com.procuremind.platform.pojo.request.KnowledgeUpdateRequest;
import com.procuremind.platform.pojo.request.KnowledgeAclEntry;
import com.procuremind.platform.pojo.request.KnowledgeVersionRequest;
import com.procuremind.platform.pojo.request.KnowledgeStatusRequest;
import com.procuremind.platform.pojo.request.KnowledgeUploadRequest;

class KnowledgeValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll static void closeFactory(){FACTORY.close();}

    @Test
    void createRequestRequiresTitleCategoryVisibilityAndFile(){
        var invalid = new KnowledgeCreateRequest(null, " ", "NOT_A_CATEGORY",
                null, null, null, "EVERYONE", null, null, null, null, null);
        assertThat(VALIDATOR.validate(invalid)).extracting(v -> v.getPropertyPath().toString())
                .contains("title", "category", "visibility", "fileId");

        var valid = new KnowledgeCreateRequest("KB-20260101-0001", "采购管理制度", "POLICY",
                "公司采购活动的基本规则", "制度,采购", 1L, "ALL", 10L, null, null, null, "首版");
        assertThat(VALIDATOR.validate(valid)).isEmpty();
    }

    @Test
    void updateRequestDoesNotRequireAFileBecauseVersioningIsSeparate(){
        var valid = new KnowledgeUpdateRequest("采购管理制度", "POLICY", "修订摘要",
                "制度", 1L, "DEPARTMENT", null, null, null);
        assertThat(VALIDATOR.validate(valid)).isEmpty();
        assertThat(VALIDATOR.validate(new KnowledgeUpdateRequest("", "POLICY", null, null, null, "ALL", null, null, null)))
                .extracting(v -> v.getPropertyPath().toString()).contains("title");
    }

    @Test
    void customVisibilityRequiresWellFormedAclEntries(){
        assertThat(VALIDATOR.validate(new KnowledgeAclEntry("USER", "1"))).isEmpty();
        assertThat(VALIDATOR.validate(new KnowledgeAclEntry("GROUP", "1")))
                .extracting(v -> v.getPropertyPath().toString()).contains("subjectType");
        assertThat(VALIDATOR.validate(new KnowledgeAclEntry("ROLE", " ")))
                .extracting(v -> v.getPropertyPath().toString()).contains("subjectKey");
    }

    @Test
    void uploadOnlyAcceptsDocumentContentTypes(){
        var docx = new KnowledgeUploadRequest("采购管理制度.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 1024L);
        assertThat(VALIDATOR.validate(docx)).isEmpty();

        var pdf = new KnowledgeUploadRequest("采购管理制度.pdf", "application/pdf", 1024L);
        assertThat(VALIDATOR.validate(pdf)).isEmpty();

        var executable = new KnowledgeUploadRequest("setup.exe", "application/x-msdownload", 1024L);
        assertThat(VALIDATOR.validate(executable)).extracting(v -> v.getPropertyPath().toString()).contains("contentType");
    }

    @Test
    void statusChangeOnlyAcceptsKnownStates(){
        assertThat(VALIDATOR.validate(new KnowledgeStatusRequest("PUBLISHED"))).isEmpty();
        assertThat(VALIDATOR.validate(new KnowledgeStatusRequest("DELETED")))
                .extracting(v -> v.getPropertyPath().toString()).contains("status");
    }

    @Test
    void versionRequestRequiresTargetFile(){
        assertThat(VALIDATOR.validate(new KnowledgeVersionRequest(null, "换版")))
                .extracting(v -> v.getPropertyPath().toString()).contains("fileId");
        assertThat(VALIDATOR.validate(new KnowledgeVersionRequest(2L, "换版"))).isEmpty();
    }

    @Test
    void createRequestAcceptsAllSupportedCategories(){
        List<String> categories = List.of("POLICY", "PROCEDURE", "CONTRACT", "SUPPLIER", "MANUAL", "TEMPLATE", "OTHER");
        for (String category : categories) {
            var request = new KnowledgeCreateRequest(null, "标题", category, null, null, null, "ALL", 1L, null, null, null, null);
            assertThat(VALIDATOR.validate(request)).as(category).isEmpty();
        }
    }
}
