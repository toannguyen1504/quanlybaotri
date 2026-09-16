package com.example.quanlybaotri;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quanlybaotri.equipment.api.EquipmentCategoryController;
import com.example.quanlybaotri.equipment.domain.Equipment;
import com.example.quanlybaotri.equipment.persistence.EquipmentCategoryRepository;
import com.example.quanlybaotri.equipment.persistence.EquipmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EquipmentCategoryDetailsIntegrationTests {
    @Autowired
    EquipmentCategoryController categories;
    @Autowired
    EquipmentCategoryRepository categoryRepository;
    @Autowired
    EquipmentRepository equipment;

    @Test
    @WithMockUser(roles = "ADMIN")
    void managesDescriptionStatusAndEquipmentCount() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        var created = categories.create(new EquipmentCategoryController.Request("cat" + suffix, "Máy kiểm thử",
                "Thiết bị dùng trong kiểm thử", true));
        var category = categoryRepository.findById(created.id()).orElseThrow();
        equipment.save(new Equipment("EQ" + suffix, "Thiết bị số 1", category));

        var listed = categories.list().stream().filter(item -> item.id().equals(created.id())).findFirst()
                .orElseThrow();
        assertThat(listed.description()).isEqualTo("Thiết bị dùng trong kiểm thử");
        assertThat(listed.equipmentCount()).isEqualTo(1);

        var updated = categories.update(created.id(), new EquipmentCategoryController.Request(created.code(),
                "Máy kiểm thử mới", "Mô tả đã cập nhật", false));
        assertThat(updated.name()).isEqualTo("Máy kiểm thử mới");
        assertThat(updated.description()).isEqualTo("Mô tả đã cập nhật");
        assertThat(updated.active()).isFalse();
        assertThat(updated.equipmentCount()).isEqualTo(1);
    }
}
