package com.procuremind.platform;

import com.procuremind.platform.agentintegration.LegacyStatus;
import com.procuremind.platform.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyStatusTest {
    @Test
    void acceptsLegacyNumericCodesAndPlatformStatusNames() {
        assertThat(LegacyStatus.filter(null)).isNull();
        assertThat(LegacyStatus.filter(" ")).isNull();
        assertThat(LegacyStatus.filter("0")).isEqualTo("DRAFT");
        assertThat(LegacyStatus.filter("2")).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(LegacyStatus.filter("4")).isEqualTo("CLOSED");
        assertThat(LegacyStatus.filter("pending_approval")).isEqualTo("PENDING_APPROVAL");
        assertThat(LegacyStatus.filter("CANCELLED")).isEqualTo("CANCELLED");
    }

    @Test
    void rejectsUnknownFilterValuesInsteadOfReturningEmptyPages() {
        assertThatThrownBy(() -> LegacyStatus.filter("9"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的订单状态");
    }

    @Test
    void onlyAllowsSubmitAndCloseDecisions() {
        assertThat(LegacyStatus.decision("1")).isEqualTo(LegacyStatus.Decision.SUBMIT);
        assertThat(LegacyStatus.decision("submit")).isEqualTo(LegacyStatus.Decision.SUBMIT);
        assertThat(LegacyStatus.decision("4")).isEqualTo(LegacyStatus.Decision.CLOSE);
        assertThat(LegacyStatus.decision("CLOSED")).isEqualTo(LegacyStatus.Decision.CLOSE);
        assertThatThrownBy(() -> LegacyStatus.decision("2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能绕过审批或收货流程");
        assertThatThrownBy(() -> LegacyStatus.decision(null)).isInstanceOf(BusinessException.class);
    }
}
