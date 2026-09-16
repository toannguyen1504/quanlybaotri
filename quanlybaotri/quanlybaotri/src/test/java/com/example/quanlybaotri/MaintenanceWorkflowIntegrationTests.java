package com.example.quanlybaotri;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.quanlybaotri.equipment.api.EquipmentController;
import com.example.quanlybaotri.equipment.domain.Equipment;
import com.example.quanlybaotri.equipment.domain.EquipmentCategory;
import com.example.quanlybaotri.equipment.persistence.EquipmentCategoryRepository;
import com.example.quanlybaotri.equipment.persistence.EquipmentRepository;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.identity.application.AuthService;
import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.identity.persistence.RoleRepository;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import com.example.quanlybaotri.inventory.api.InventoryController.MovementRequest;
import com.example.quanlybaotri.inventory.api.InventoryController.PartRequest;
import com.example.quanlybaotri.inventory.api.InventoryController.UsePartRequest;
import com.example.quanlybaotri.inventory.application.InventoryService;
import com.example.quanlybaotri.inventory.persistence.PartRepository;
import com.example.quanlybaotri.reporting.application.DashboardService;
import com.example.quanlybaotri.ticket.api.TicketController.CreateTicketRequest;
import com.example.quanlybaotri.ticket.application.TicketService;
import com.example.quanlybaotri.ticket.domain.MaintenanceTicket;
import com.example.quanlybaotri.ticket.domain.TicketChargeType;
import com.example.quanlybaotri.ticket.domain.TicketListView;
import com.example.quanlybaotri.ticket.domain.TicketPriority;
import com.example.quanlybaotri.ticket.domain.TicketStatus;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MaintenanceWorkflowIntegrationTests {
    @Autowired
    TicketService tickets;
    @Autowired
    InventoryService inventory;
    @Autowired
    UserRepository users;
    @Autowired
    RoleRepository roles;
    @Autowired
    EquipmentCategoryRepository categories;
    @Autowired
    EquipmentRepository equipment;
    @Autowired
    EquipmentController equipmentController;
    @Autowired
    PartRepository parts;
    @Autowired
    AuthService auth;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    DashboardService dashboard;
    @Autowired
    TicketRepository ticketRepository;

    @Test
    void completesTicketLifecycleAndIssuesInventoryAtomically() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        UserAccount requester = user("req" + suffix, RoleName.REQUESTER);
        UserAccount manager = user("mgr" + suffix, RoleName.MANAGER);
        UserAccount technician = user("tech" + suffix, RoleName.TECHNICIAN);
        EquipmentCategory category = categories.save(new EquipmentCategory("CAT" + suffix, "Máy văn phòng", null));
        Equipment device = equipment.save(new Equipment("EQ" + suffix, "Máy in kiểm thử", category));

        var ticket = tickets.create(new CreateTicketRequest(device.getId(), "Không nhận giấy",
                "Thiết bị không thể kéo giấy", TicketPriority.HIGH), requester);
        assertThat(ticket.status()).isEqualTo(TicketStatus.SUBMITTED);
        assertThat(ticket.chargeType()).isEqualTo(TicketChargeType.PENDING);
        assertThat(ticket.chargeAmount()).isEqualByComparingTo("0");
        tickets.accept(ticket.id(), manager);
        tickets.assign(ticket.id(), technician.getId(), "Phân công ca trực", manager);
        tickets.start(ticket.id(), technician);
        tickets.addWorkLog(ticket.id(), "Vệ sinh bộ cuốn giấy", 20, technician);

        var part = inventory.create(new PartRequest("PART" + suffix, "Bánh xe kéo giấy", "cái", BigDecimal.ONE,
                new BigDecimal("125000"), true));
        inventory.receive(part.id(),
                new MovementRequest(new BigDecimal("5"), new BigDecimal("120000"), "Nhập kiểm thử"), manager);
        inventory.use(ticket.id(), new UsePartRequest(part.id(), new BigDecimal("2"), null), technician);
        assertThat(parts.findById(part.id()).orElseThrow().getCurrentStock()).isEqualByComparingTo("3");
        var charged = tickets.get(ticket.id(), requester);
        assertThat(charged.partsCost()).isEqualByComparingTo("250000");
        assertThat(charged.chargeAmount()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> tickets.changeChargeType(ticket.id(), TicketChargeType.PAID, technician))
                .hasMessageContaining("trạng thái");

        tickets.resolve(ticket.id(), "Đã thay linh kiện và chạy thử ổn định", technician);
        assertThatThrownBy(() -> tickets.close(ticket.id(), null, requester))
                .hasMessageContaining("xác định phiếu miễn phí hay trả phí");
        assertThat(tickets.changeChargeType(ticket.id(), TicketChargeType.FREE, technician).chargeAmount())
                .isEqualByComparingTo("0");
        assertThat(tickets.changeChargeType(ticket.id(), TicketChargeType.PAID, manager).chargeAmount())
                .isEqualByComparingTo("250000");
        var closed = tickets.close(ticket.id(), null, requester);
        assertThat(closed.status()).isEqualTo(TicketStatus.CLOSED);
        assertThat(tickets.timeline(ticket.id(), requester)).extracting("type").contains("SUBMITTED", "ACCEPTED",
                "ASSIGNED", "STARTED", "WORK_LOG_ADDED", "PART_USED", "CHARGE_TYPE_CHANGED", "RESOLVED", "CLOSED");
        assertThat(dashboard.get(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now()).byStatus())
                .containsKey("CLOSED");
    }

    @Test
    void rotatesRefreshTokenAndRejectsTheOldToken() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        String username = "auth" + suffix;
        UserAccount user = new UserAccount(username, username + "@example.test",
                passwordEncoder.encode("ValidPass@123"), username);
        user.setRoles(Set.of(roles.findByName(RoleName.REQUESTER).orElseThrow()));
        users.save(user);
        var first = auth.login(username, "ValidPass@123", "127.0.0.1");
        var rotated = auth.refresh(first.refreshToken());
        assertThat(rotated.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThatThrownBy(() -> auth.refresh(first.refreshToken())).hasMessageContaining("Refresh token");
    }

    @Test
    void listsEquipmentWithoutAQueryOrStatusFilter() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        EquipmentCategory category = categories.save(new EquipmentCategory("LIST" + suffix, "Danh mục", null));
        Equipment device = equipment.save(new Equipment("DEVICE" + suffix, "Thiết bị kiểm thử", category));

        var page = equipmentController.list(null, null, PageRequest.of(0, 100));

        assertThat(page.getContent()).anyMatch(view -> view.id().equals(device.getId()));
    }

    @Test
    void summarizesAndFiltersOperationalTicketViewsWithinActorVisibility() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        UserAccount requesterA = user("opsa" + suffix, RoleName.REQUESTER);
        UserAccount requesterB = user("opsb" + suffix, RoleName.REQUESTER);
        UserAccount technician = user("opst" + suffix, RoleName.TECHNICIAN);
        UserAccount manager = user("opsm" + suffix, RoleName.MANAGER);
        UserAccount admin = user("opsd" + suffix, RoleName.ADMIN);
        EquipmentCategory category = categories.save(new EquipmentCategory("OPS" + suffix, "Thiết bị vận hành", null));
        Equipment device = equipment.save(new Equipment("OPS-EQ" + suffix, "Thiết bị theo dõi SLA", category));
        Instant now = Instant.now();

        var managerBefore = tickets.summary(manager);
        var adminBefore = tickets.summary(admin);
        operationalTicket("OPS-A-" + suffix, device, requesterA, null, now.minus(1, ChronoUnit.HOURS),
                now.plus(9, ChronoUnit.HOURS), false);
        MaintenanceTicket dueSoon = operationalTicket("OPS-B-" + suffix, device, requesterB, technician,
                now.minus(9, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS), false);
        MaintenanceTicket overdue = operationalTicket("OPS-C-" + suffix, device, requesterB, technician,
                now.minus(11, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS), false);
        operationalTicket("OPS-D-" + suffix, device, requesterA, null, now.minus(20, ChronoUnit.HOURS),
                now.minus(10, ChronoUnit.HOURS), true);

        var requesterSummary = tickets.summary(requesterA);
        assertThat(requesterSummary.total()).isEqualTo(2);
        assertThat(requesterSummary.open()).isEqualTo(2);
        assertThat(requesterSummary.dueSoon()).isZero();
        assertThat(requesterSummary.overdue()).isZero();

        var technicianSummary = tickets.summary(technician);
        assertThat(technicianSummary.total()).isEqualTo(2);
        assertThat(technicianSummary.open()).isEqualTo(2);
        assertThat(technicianSummary.dueSoon()).isEqualTo(1);
        assertThat(technicianSummary.overdue()).isEqualTo(1);

        var managerSummary = tickets.summary(manager);
        assertThat(managerSummary.total() - managerBefore.total()).isEqualTo(4);
        assertThat(managerSummary.open() - managerBefore.open()).isEqualTo(4);
        assertThat(managerSummary.dueSoon() - managerBefore.dueSoon()).isEqualTo(1);
        assertThat(managerSummary.overdue() - managerBefore.overdue()).isEqualTo(1);

        var adminSummary = tickets.summary(admin);
        assertThat(adminSummary.total() - adminBefore.total()).isEqualTo(4);
        assertThat(adminSummary.overdue() - adminBefore.overdue()).isEqualTo(1);

        var dueSoonPage = tickets.list(requesterB, null, null, null, TicketListView.DUE_SOON,
                PageRequest.of(0, 20));
        assertThat(dueSoonPage.getContent()).extracting("id").containsExactly(dueSoon.getId());
        var overduePage = tickets.list(requesterB, null, null, null, TicketListView.OVERDUE,
                PageRequest.of(0, 20));
        assertThat(overduePage.getContent()).extracting("id").containsExactly(overdue.getId());
    }

    private MaintenanceTicket operationalTicket(String code, Equipment device, UserAccount requester,
            UserAccount assignee, Instant submittedAt, Instant resolutionDueAt, boolean resolved) {
        MaintenanceTicket ticket = new MaintenanceTicket(code, device, requester, "Kiểm tra SLA " + code,
                "Dữ liệu kiểm thử danh sách vận hành", TicketPriority.HIGH,
                submittedAt.plus(30, ChronoUnit.MINUTES), resolutionDueAt);
        ReflectionTestUtils.setField(ticket, "submittedAt", submittedAt);
        if (assignee != null)
            ticket.assign(assignee);
        if (resolved)
            ticket.resolve("Đã xử lý xong");
        return ticketRepository.save(ticket);
    }

    private UserAccount user(String username, RoleName roleName) {
        UserAccount user = new UserAccount(username, username + "@example.test", "not-used-in-service-test", username);
        user.setRoles(Set.of(roles.findByName(roleName).orElseThrow()));
        return users.save(user);
    }
}
