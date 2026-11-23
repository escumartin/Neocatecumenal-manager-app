---
name: hexagonal-ddd
description: Hexagonal architecture and DDD conventions for the Java 21 Spring Boot 3 Spring Modulith backend. Use when creating or changing a bounded context, domain model, use case, port, REST or JPA adapter, domain event or architecture test.
---

# Hexagonal + DDD with Spring Modulith

One Spring Modulith module per bounded context, one top-level package each.

```
<base>.<context>/
  <Context>Api.java        public API and events (module root package)
  domain/                  entities, value objects, aggregates, domain services, domain events
  application/
    port/in/               use case interfaces (one per command or query)
    port/out/              repository and external service interfaces
    service/               use case implementations, transactional boundary
  infrastructure/
    in/rest/               controllers implementing generated OpenAPI interfaces, request/response mappers
    out/persistence/       JPA entities, Spring Data repositories, adapters implementing port/out, mappers
    config/                Spring wiring for the module
```

## Rules

- `domain` is plain Java: no Spring, no JPA, no Jackson. Invariants live in aggregates and value objects; constructors and factories validate.
- `application` depends only on `domain`. Ports are interfaces in `application`; implementations are in `infrastructure`.
- `infrastructure` depends on `application` and `domain`, never the reverse.
- REST adapters implement interfaces generated from `api/openapi.yaml`; DTOs never reach `application`. Map explicitly (no entity exposure).
- JPA entities are persistence models, separate from domain objects; the adapter maps in both directions.
- Other modules use only the module root package (`<Context>Api`, published events). Nothing in `domain`, `application` or `infrastructure` is visible across modules; mark internals non-exported.
- Cross-context reactions go through domain events published with `ApplicationEventPublisher` and consumed with `@ApplicationModuleListener`; no direct calls into another context's internals.
- Identifiers of other contexts are referenced by id value objects, not by entity.

## Tests

- Domain and application: plain JUnit, no Spring, ports faked in memory. Write the test first.
- Adapters: Testcontainers PostgreSQL (`@DataJpaTest` or `@SpringBootTest`), Flyway migrations applied.
- Architecture:

```java
class ArchitectureTest {
    @Test
    void modulesAreValid() {
        ApplicationModules.of(Application.class).verify();
    }

    @Test
    void domainIsFrameworkFree() {
        JavaClasses classes = new ClassFileImporter().importPackages("io.github.escumartin.neocatecumenal");
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.fasterxml..")
            .check(classes);
    }

    @Test
    void applicationDoesNotDependOnInfrastructure() {
        JavaClasses classes = new ClassFileImporter().importPackages("io.github.escumartin.neocatecumenal");
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .check(classes);
    }
}
```

## Do not

- Put Spring or JPA annotations in `domain`.
- Return JPA entities or generated DTOs from use cases.
- Call another module's repository, service or entity.
- Share a database table between contexts; share ids and events.
- Add comments to code; names carry the meaning.
