package com.procuremind.platform;

import com.procuremind.platform.system.CurrentUser;
import com.procuremind.platform.system.InternalAgentAuthenticationFilter;
import com.procuremind.platform.system.UserDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalAgentAuthenticationFilterTest {
    private static final String TOKEN = "a-secure-internal-token-with-32-characters";

    @Test
    void rejectsInsecureTokenWhenAgentIsEnabled() {
        assertThatThrownBy(() -> new InternalAgentAuthenticationFilter(mock(UserDirectory.class), "change-me", true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hidesLegacyApiWhenAgentIsDisabled() throws Exception {
        var filter = new InternalAgentAuthenticationFilter(mock(UserDirectory.class), "change-me", false);
        var request = new MockHttpServletRequest("GET", "/api/orders/page");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void enforcesBusinessPermissionAfterInternalAuthentication() throws Exception {
        UserDirectory directory = mock(UserDirectory.class);
        when(directory.current(2L)).thenReturn(new CurrentUser(2L, "requester", "申请人", 2L,
                Set.of("APPLICANT"), Set.of("requisition:view")));
        var filter = new InternalAgentAuthenticationFilter(directory, TOKEN, true);
        var request = new MockHttpServletRequest("GET", "/api/orders/page");
        request.addHeader("X-Internal-Token", TOKEN);
        request.addHeader("X-User-Id", "2");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void newAgentFacingPathsUseDedicatedPermissions() throws Exception {
        UserDirectory directory = mock(UserDirectory.class);
        when(directory.current(3L)).thenReturn(new CurrentUser(3L, "purchaser", "采购员", 4L,
                Set.of("PURCHASER"), Set.of("requisition:view", "kb:view")));
        var filter = new InternalAgentAuthenticationFilter(directory, TOKEN, true);

        var workflow = new MockHttpServletRequest("GET", "/api/workflow/todo");
        workflow.addHeader("X-Internal-Token", TOKEN);
        workflow.addHeader("X-User-Id", "3");
        var workflowResponse = new MockHttpServletResponse();
        filter.doFilter(workflow, workflowResponse, new MockFilterChain());
        assertThat(workflowResponse.getStatus()).isEqualTo(403);

        var knowledge = new MockHttpServletRequest("GET", "/api/kb/visible-doc-ids");
        knowledge.addHeader("X-Internal-Token", TOKEN);
        knowledge.addHeader("X-User-Id", "3");
        var knowledgeResponse = new MockHttpServletResponse();
        filter.doFilter(knowledge, knowledgeResponse, new MockFilterChain());
        assertThat(knowledgeResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void operationsReadPathsRequireDedicatedPermissions() throws Exception {
        UserDirectory directory = mock(UserDirectory.class);
        when(directory.current(5L)).thenReturn(new CurrentUser(5L, "finance", "财务", 5L,
                Set.of("FINANCE"), Set.of("budget:view", "contract:view", "masterdata:view", "inventory:view")));
        var filter = new InternalAgentAuthenticationFilter(directory, TOKEN, true);

        for (String path : List.of("/api/warehouses", "/api/budgets", "/api/contracts/page",
                "/api/inventory/issues", "/api/parts/1/price-history")) {
            var request = new MockHttpServletRequest("GET", path);
            request.addHeader("X-Internal-Token", TOKEN);
            request.addHeader("X-User-Id", "5");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).as(path).isEqualTo(200);
        }

        UserDirectory applicantDirectory = mock(UserDirectory.class);
        when(applicantDirectory.current(2L)).thenReturn(new CurrentUser(2L, "requester", "申请人", 2L,
                Set.of("APPLICANT"), Set.of("requisition:view")));
        var applicantFilter = new InternalAgentAuthenticationFilter(applicantDirectory, TOKEN, true);
        var budgets = new MockHttpServletRequest("GET", "/api/budgets");
        budgets.addHeader("X-Internal-Token", TOKEN);
        budgets.addHeader("X-User-Id", "2");
        var budgetsResponse = new MockHttpServletResponse();
        applicantFilter.doFilter(budgets, budgetsResponse, new MockFilterChain());
        assertThat(budgetsResponse.getStatus()).isEqualTo(403);
    }
}
