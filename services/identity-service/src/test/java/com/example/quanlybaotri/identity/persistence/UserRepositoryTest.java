package com.example.quanlybaotri.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

class UserRepositoryTest {

    @Test
    void apiLookupGraphsLoadRolesAndSiteIds() throws NoSuchMethodException {
        List<Method> apiLookups = List.of(
            UserRepository.class.getMethod("findAll", Specification.class, Pageable.class),
            UserRepository.class.getMethod("findWithRolesByUsername", String.class),
            UserRepository.class.getMethod("findWithRolesById", UUID.class),
            UserRepository.class.getMethod("findByIdIn", Collection.class),
            UserRepository.class.getMethod("findEnabledByRoleNames", Collection.class),
            UserRepository.class.getMethod("findByRoleNames", Collection.class)
        );

        apiLookups.forEach(method ->
            assertThat(method.getAnnotation(EntityGraph.class).attributePaths())
                .as(method.getName())
                .contains("roles", "siteIds", "regionIds")
        );
    }
}
