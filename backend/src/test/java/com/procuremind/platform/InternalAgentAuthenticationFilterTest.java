package com.procuremind.platform;

import com.procuremind.platform.system.CurrentUser;
import com.procuremind.platform.system.InternalAgentAuthenticationFilter;
import com.procuremind.platform.system.UserDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
}
