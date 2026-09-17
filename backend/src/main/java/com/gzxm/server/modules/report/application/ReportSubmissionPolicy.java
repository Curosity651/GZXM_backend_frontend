package com.gzxm.server.modules.report.application;

import com.gzxm.server.common.exception.BusinessException;

final class ReportSubmissionPolicy {
    private ReportSubmissionPolicy() {}

    static void requireDemonstrationProgress(String value) {
        String content = value == null ? "" : value.strip();
        if ("无".equals(content)) return;
        if (content.codePointCount(0, content.length()) < 300)
            throw BusinessException.validation("REPORT_DEMONSTRATION_TOO_SHORT", "示范工程进展至少填写300字；确无进展时填写“无”");
    }
}
