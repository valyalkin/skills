package com.example.invoices.repository

import com.example.invoices.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Proof that Flyway owns the schema and that the entities match what it produced.
 *
 * The context starting at all is most of the assertion: `ddl-auto: validate` means Hibernate
 * compares every entity against the migrated tables and refuses to start on a mismatch. So a
 * migration that forgets a column the entity declares fails the build here rather than on the
 * first request in production.
 */
class SchemaValidationIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `every migration applied cleanly and none was modified after the fact`() {
        val applied =
            jdbc.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank",
            )

        assertThat(applied).hasSize(2)
        assertThat(applied.map { it["version"] }).containsExactly("1", "2")
        assertThat(applied).allSatisfy({ assertThat(it["success"]).isEqualTo(true) })
    }

    @Test
    fun `the invoices table has the columns the entity declares`() {
        val columns =
            jdbc
                .queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_name = 'invoices'",
                ).map { it["column_name"] as String }

        assertThat(columns)
            .containsExactlyInAnyOrder(
                "id",
                "customer_id",
                "amount_cents",
                "created_at",
                "version",
                "status",
            )
    }

    @Test
    fun `the partial unique index from V2 is what enforces one pending invoice per customer`() {
        val indexes =
            jdbc
                .queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'invoices'")
                .map { it["indexname"] as String }

        assertThat(indexes).contains("ux_invoices_customer_amount_pending", "ix_invoices_customer_id")
    }

    @Test
    fun `the amount check constraint rejects a non-positive amount at the database level`() {
        assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM information_schema.check_constraints
                WHERE constraint_name IN ('ck_invoices_amount_positive', 'ck_invoices_status')
                """.trimIndent(),
                Int::class.java,
            ),
        ).isEqualTo(2)
    }
}
