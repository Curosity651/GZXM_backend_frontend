package com.gzxm.server.modules.report.application;

import com.gzxm.server.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ReportSubmissionPolicyTest {
    @Test
    void acceptsExactlyThreeHundredChineseCharactersAndNoProgressException() {
        assertThatCode(() -> ReportSubmissionPolicy.requireDemonstrationProgress("进".repeat(300)))
                .doesNotThrowAnyException();
        assertThatCode(() -> ReportSubmissionPolicy.requireDemonstrationProgress(" 无 "))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsShortDescriptionOtherThanNoProgressException() {
        assertThatThrownBy(() -> ReportSubmissionPolicy.requireDemonstrationProgress("进".repeat(299)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code()).isEqualTo("REPORT_DEMONSTRATION_TOO_SHORT");
        assertThatThrownBy(() -> ReportSubmissionPolicy.requireDemonstrationProgress("暂无"))
                .isInstanceOf(BusinessException.class);
    }
}
