# Persistence and migrations

JPA/Hibernate reads and writes; **Flyway owns the schema**. Those two facts are enforced by
configuration, not convention: `ddl-auto: validate` means Hibernate compares every entity against
the migrated schema at startup and refuses to boot on a mismatch.

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate      # never update, never create
    open-in-view: false
    properties:
      hibernate:
        jdbc.time_zone: UTC
        query.fail_on_pagination_over_collection_fetch: true
  flyway:
    enabled: true
    locations: classpath:db/migration
    validate-on-migrate: true
```

`open-in-view: false` matters: left on (the default), the persistence session stays open for the
whole request and lazy loading silently works in the controller, hiding N+1s until production.

## Entities

They live in `repository/`, beside the repository interface, because that package is the only one
allowed to know Hibernate exists.

```kotlin
@Entity
@Table(name = "invoices")
class InvoiceEntity(
    @Id @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,
    @Column(name = "amount_cents", nullable = false)
    var amountCents: Long,
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16)
    var status: InvoiceStatus,
    @Version @Column(name = "version", nullable = false)
    var version: Long = 0,
)
```

Rules, each of which is a bug if broken:

- **`class`, not `data class`.** A generated `equals`/`hashCode` over a mutable id breaks the moment
  an entity enters a `Set` before being flushed, and its identity changes after the insert.
- **`var`, not `val`.** Hibernate writes fields on load. The `kotlin("plugin.jpa")` plugin supplies
  the no-arg constructor; `allOpen` (configured for `@Entity` in `build.gradle.kts`) makes the class
  non-final so Hibernate can build lazy proxies.
- **Name every column and table explicitly.** The implicit naming strategy turns a Kotlin rename
  into a production schema change.
- **`@Enumerated(EnumType.STRING)`, always.** `ORDINAL` makes reordering an enum silently corrupt
  data.
- **Fetch types explicit, `LAZY` by default**, and no bidirectional relations unless something
  genuinely needs both sides.
- **`@Version`** on anything two requests can update — a lost write becomes a 409 instead
  (`OptimisticLockingFailureException`, mapped in the handler).
- No `@Transactional` on a repository, and no business rules in one: a repository answers questions
  about rows.

Entities never leave `repository`/`service` — `toModel()` converts at the boundary.

## Migrations

`src/main/resources/db/migration/V<n>__<snake_case_description>.sql`.

- **An applied migration is immutable.** `validate-on-migrate` turns an edit into a startup
  failure, which is the desired outcome: the environments that already ran it cannot be fixed by
  changing the file.
- **Additive and backwards compatible.** A new column gets a default; a rename is an add, a
  backfill and a later drop. That way the currently-deployed build keeps working against the new
  schema and the order of "migrate" versus "deploy" stops mattering — which is what makes a rolling
  deployment safe.
- **Update the entity in the same commit.** `ddl-auto: validate` plus `SchemaValidationIT` catch
  the mismatch at build time.
- Put constraints in the database, not only in the entity. A `CHECK` and a `UNIQUE` index are
  enforced regardless of which application writes.

A partial unique index is often the honest expression of a business rule:

```sql
CREATE UNIQUE INDEX ux_invoices_customer_amount_pending
    ON invoices (customer_id, amount_cents)
    WHERE status = 'PENDING';
```

## Races the pre-check cannot win

A service that checks "does one already exist?" and then inserts is racing. Both requests can pass
the check. The database decides, and the loser must get the same answer as if it had lost the check:

```kotlin
if (invoices.existsByCustomerIdAndAmountCentsAndStatus(...)) {
    throw DuplicateException(ErrorCode.DUPLICATE_INVOICE, "...")
}
return try {
    invoices.save(entity).toModel()
} catch (exception: DataIntegrityViolationException) {
    throw DuplicateException(ErrorCode.DUPLICATE_INVOICE, "...", exception)
}
```

The pre-check exists for the good error message; the constraint exists for correctness.

## Transactions

- `@Transactional(readOnly = true)` on the service class, `@Transactional` on the methods that
  write. `readOnly` lets the driver and Hibernate skip dirty checking and makes an accidental write
  fail.
- The service owns the boundary. Not the controller (it would hold a transaction open across
  serialization) and not the repository (too fine-grained to express a unit of work).
- **Never call an external service inside a transaction** you care about. The example charges the
  payment gateway and only then mutates and saves — a slow upstream must not hold a database
  connection, and a transaction must not span a call that cannot be rolled back.
- Pagination through `Pageable`. Return the service's own `PageResponse`, never Spring Data's
  `Page`, whose JSON shape is a framework implementation detail.

## Never H2

Tests run Postgres through Testcontainers, on the same major version as production. H2 accepts SQL
Postgres rejects, lacks partial indexes, differs on casing, `jsonb`, upserts and sequences, and
cannot faithfully run the migrations. A green suite on H2 tells you nothing about the schema you
actually deploy. See `testing.md`.
