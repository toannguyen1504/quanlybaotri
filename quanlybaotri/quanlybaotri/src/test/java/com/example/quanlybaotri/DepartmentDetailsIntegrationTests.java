package com.example.quanlybaotri;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quanlybaotri.organization.api.DepartmentController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DepartmentDetailsIntegrationTests {
    @Autowired
    DepartmentController departments;

    @Test
    @WithMockUser(roles = "ADMIN")
    void createsAndUpdatesDepartmentContactDetails() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        var created = departments.create(new DepartmentController.Request("dept" + suffix, "Phòng kiểm thử",
                "Phụ trách vận hành", "Tầng 5", "DEPT@EXAMPLE.TEST", "024-1234", true));

        assertThat(created.code()).isEqualTo(("dept" + suffix).toUpperCase());
        assertThat(created.description()).isEqualTo("Phụ trách vận hành");
        assertThat(created.location()).isEqualTo("Tầng 5");
        assertThat(created.contactEmail()).isEqualTo("dept@example.test");
        assertThat(created.contactPhone()).isEqualTo("024-1234");

        var updated = departments.update(created.id(), new DepartmentController.Request(created.code(),
                "Phòng kiểm thử mới", "Mô tả mới", "Tầng 6", "new-dept@example.test", "024-5678", false));

        assertThat(updated.name()).isEqualTo("Phòng kiểm thử mới");
        assertThat(updated.description()).isEqualTo("Mô tả mới");
        assertThat(updated.location()).isEqualTo("Tầng 6");
        assertThat(updated.contactEmail()).isEqualTo("new-dept@example.test");
        assertThat(updated.contactPhone()).isEqualTo("024-5678");
        assertThat(updated.active()).isFalse();
    }
}
