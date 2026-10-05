package com.example.quanlybaotri.organization.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;

class DepartmentRepositoryTest {

    @Test
    void siteQueriesFetchAssociationsUsedByApiViews() throws Exception {
        assertFetchesBranchAndRegion(DepartmentRepository.class.getMethod("findAll"));
        assertFetchesBranchAndRegion(DepartmentRepository.class.getMethod("findById", UUID.class));
        assertFetchesBranchAndRegion(
            DepartmentRepository.class.getMethod("findByIdIn", Collection.class)
        );
        assertFetchesBranchAndRegion(
            DepartmentRepository.class.getMethod("findByBranchId", UUID.class)
        );
    }

    private void assertFetchesBranchAndRegion(Method method) {
        EntityGraph graph = method.getAnnotation(EntityGraph.class);
        assertTrue(graph != null, method.getName() + " must define an entity graph");
        Set<String> paths = Set.of(graph.attributePaths());
        assertTrue(paths.contains("branch"), method.getName() + " must fetch branch");
        assertTrue(paths.contains("region"), method.getName() + " must fetch region");
    }
}
