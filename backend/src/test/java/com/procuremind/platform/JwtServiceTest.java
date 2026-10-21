package com.procuremind.platform;

import com.procuremind.platform.system.CurrentUser;
import com.procuremind.platform.system.JwtService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    @Test
    void accessTokenCarriesServerOwnedIdentity() {
        JwtService jwt = new JwtService("a-test-secret-with-more-than-32-bytes-long", 30, 7);
        CurrentUser user = new CurrentUser(42L, "requester", "研发申请人", 2L, Set.of("APPLICANT"), Set.of("requisition:view"));
        var claims = jwt.parse(jwt.accessToken(user));
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("username", String.class)).isEqualTo("requester");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
    }
}
