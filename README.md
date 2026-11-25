# Neocatecumenal Manager App

App web y móvil de gestión de comunidades, salones y eventos.

## Stack

- Backend: Java 25, Spring Boot 4, Spring Modulith, Spring Data JPA, Spring Security, Flyway, PostgreSQL 18, Maven.
- Contrato: OpenAPI (`api/openapi.yaml`), contract-first; el backend y el cliente se generan o validan contra él.
- Cliente: Expo (React Native y web), Expo Router, TypeScript, NativeWind, React Native Reusables.
- Tests: JUnit 5, ArchUnit, Testcontainers (backend); Jest y Testing Library (cliente).
- CI: GitHub Actions (`.github/workflows/ci.yml`).

## Arquitectura

Backend hexagonal con DDD: el dominio no depende de Spring ni de la infraestructura, y los puertos viven en el dominio. Cada bounded context es un módulo de Spring Modulith (`agenda`, `identity`, `spaces`), verificado en los tests junto con las reglas de ArchUnit.

## Cómo se trabaja

Desarrollo guiado por specs con spec-kit y agentes de IA: las specs están en `specs/` y el flujo y los agentes, en `.agents/` y `.specify/`.

## Arranque en local

Requisitos: JDK 25, Node 24 o superior y Docker.

```bash
cp .env.example .env
docker compose up -d --wait postgres
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd app && npm ci && npm run web
```

`.env.example` define las variables de la base de datos (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`); ajusta sus valores en tu `.env`.

`scripts/local-run.sh` hace los tres pasos a la vez (`--device` para probar en un móvil en la misma red). También hay `npm run android` y `npm run ios`.

## Tests

```bash
cd backend && ./mvnw verify
cd app && npm run lint && npm run typecheck && npm test
```

`npm run api:check` comprueba que los tipos del cliente coinciden con el contrato.

## Estado

En desarrollo.

## Licencia

[MIT](LICENSE)
