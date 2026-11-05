package com.example.quanlybaotri.organization.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quanlybaotri.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class DepartmentPersistenceIntegrationTest extends BaseIntegrationTest {

    @Autowired DepartmentRepository departments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void should_run_flyway_and_query_departments_with_hibernate_validation_enabled() {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_schema = 'public'
                  and constraint_type in ('PRIMARY KEY', 'UNIQUE', 'FOREIGN KEY', 'CHECK')
                """, Integer.class)).isPositive();

        Long aggregateCount = new TransactionTemplate(transactions).execute(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return departments.count();
        });
        assertThat(aggregateCount).isNotNull().isGreaterThanOrEqualTo(0);
    }
}
