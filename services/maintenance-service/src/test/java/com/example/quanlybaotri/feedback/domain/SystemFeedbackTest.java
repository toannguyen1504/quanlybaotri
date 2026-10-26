package com.example.quanlybaotri.feedback.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SystemFeedbackTest {

    @Test
    void trimsSubmissionAndStartsAsNew() {
        UUID requester = UUID.randomUUID();
        SystemFeedback feedback = new SystemFeedback(
            requester,
            SystemFeedbackCategory.SUGGESTION,
            "  Cải thiện tìm kiếm  ",
            "  Mong muốn tìm kiếm nhanh hơn  "
        );

        assertThat(feedback.getRequesterId()).isEqualTo(requester);
        assertThat(feedback.getTitle()).isEqualTo("Cải thiện tìm kiếm");
        assertThat(feedback.getContent()).isEqualTo("Mong muốn tìm kiếm nhanh hơn");
        assertThat(feedback.getStatus()).isEqualTo(SystemFeedbackStatus.NEW);
    }

    @Test
    void validatesSubmissionLimits() {
        assertThatThrownBy(() -> new SystemFeedback(
            UUID.randomUUID(), SystemFeedbackCategory.BUG_REPORT, "   ", "Chi tiết"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SystemFeedback(
            UUID.randomUUID(), SystemFeedbackCategory.BUG_REPORT, "Tiêu đề", "x".repeat(5001)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void followsReviewTransitionsAndRequiresTerminalResponse() {
        SystemFeedback feedback = feedback();
        UUID manager = UUID.randomUUID();

        feedback.review(SystemFeedbackStatus.IN_PROGRESS, null, manager);
        assertThat(feedback.getStatus()).isEqualTo(SystemFeedbackStatus.IN_PROGRESS);
        assertThat(feedback.getReviewedById()).isEqualTo(manager);
        assertThat(feedback.getReviewedAt()).isNotNull();

        assertThatThrownBy(() -> feedback.review(SystemFeedbackStatus.RESOLVED, " ", manager))
            .isInstanceOf(IllegalArgumentException.class);

        feedback.review(SystemFeedbackStatus.RESOLVED, "  Đã cập nhật hệ thống  ", manager);
        assertThat(feedback.getResponse()).isEqualTo("Đã cập nhật hệ thống");
        assertThatThrownBy(() -> feedback.review(SystemFeedbackStatus.REJECTED, "Đổi ý", manager))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidAndRepeatedInProgressTransitions() {
        SystemFeedback feedback = feedback();
        UUID manager = UUID.randomUUID();

        assertThatThrownBy(() -> feedback.review(SystemFeedbackStatus.NEW, null, manager))
            .isInstanceOf(IllegalArgumentException.class);
        feedback.review(SystemFeedbackStatus.IN_PROGRESS, "Đang kiểm tra", manager);
        assertThatThrownBy(() -> feedback.review(SystemFeedbackStatus.IN_PROGRESS, null, manager))
            .isInstanceOf(IllegalStateException.class);
    }

    private SystemFeedback feedback() {
        return new SystemFeedback(
            UUID.randomUUID(),
            SystemFeedbackCategory.BUG_REPORT,
            "Không mở được báo cáo",
            "Màn hình báo cáo không tải dữ liệu"
        );
    }
}
