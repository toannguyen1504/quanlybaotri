package com.example.quanlybaotri.identity.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quanlybaotri.identity.domain.RoleName;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityClientTest {

    private final UUID north = UUID.randomUUID();
    private final UUID south = UUID.randomUUID();

    @Test
    void allRegionsMatchesAssignedAndUnassignedSites() {
        var technician = technician("ALL_REGIONS", Set.of());

        assertThat(technician.matchesRegion(north)).isTrue();
        assertThat(technician.matchesRegion(null)).isTrue();
    }

    @Test
    void selectedRegionsOnlyMatchesASelectedAssignedRegion() {
        var technician = technician("SELECTED_REGIONS", Set.of(north));

        assertThat(technician.matchesRegion(north)).isTrue();
        assertThat(technician.matchesRegion(south)).isFalse();
        assertThat(technician.matchesRegion(null)).isFalse();
    }

    @Test
    void unassignedTechnicianNeverMatches() {
        assertThat(technician("UNASSIGNED", Set.of()).matchesRegion(north)).isFalse();
    }

    private IdentityClient.UserRef technician(String scope, Set<UUID> regions) {
        return new IdentityClient.UserRef(
            UUID.randomUUID(), "tech", "Kỹ thuật viên", "tech@example.test", "INTERNAL",
            null, "ALL_SITES", Set.of(), scope, regions, true, Set.of(RoleName.TECHNICIAN)
        );
    }
}
