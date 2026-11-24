# Neocatecumenal Manager App

Instrucciones para cualquier asistente de IA que trabaje aquí (convención `AGENTS.md`). Aquí van solo las reglas y los
punteros; los hechos se leen al necesitarlos desde `.agents/context/`. Lo propio de cada herramienta, solo en
`.agents/TOOLING-MATRIX.md`.

App web y móvil para gestionar comunidades, salones y eventos del Camino Neocatecumenal en una parroquia (reescritura de un
TFG de 2019 en PHP). Backend Java 21 + Spring Boot 3 + Spring Modulith, hexagonal/DDD, PostgreSQL, OpenAPI contract-first.
Cliente Expo + Expo Router + TypeScript + NativeWind + React Native Reusables. Proyecto personal, repo git propio.

## Mapa

| Ruta | Qué es |
|---|---|
| `.agents/context/dominio.md` | glosario, roles, bounded contexts, reglas de negocio y casos de uso |
| `backend/` | API en Java (Maven), un módulo Modulith por bounded context |
| `api/` | contrato OpenAPI, fuente de verdad del backend y del cliente |
| `app/` | cliente Expo web y móvil |
| `.specify/` | plantillas y constitución de spec-kit |
| `.agents/agents/` | agentes especializados: diseño, crítica, tests, build y revisores |
| `.agents/scripts/` | scripts de los hooks del harness |
| `.agents/skills/` | skills del proyecto (convenciones, hexagonal, diseño, Expo) |
| `.claude/commands/` | comandos de flujo: `flow` y `speckit.*` |
| `.agents/memory/` | memoria entre sesiones: solo preferencias y estado vivo |

## Reglas

1. **Hexagonal y DDD.** El dominio no depende de Spring ni de infraestructura; los puertos viven en el dominio y los adaptadores fuera.
2. **Contrato primero.** Se cambia `api/` antes que el backend o el cliente; ambos se generan o validan contra él.
3. **Tests primero.** Cada regla de negocio nace de un test que falla.
4. **Cero comentarios** en código y configuración; el código se explica solo.
5. **Inglés** en código, tests y commits; castellano en documentación y logs.
6. **Datos personales.** RGPD: minimizar, consentimiento y borrado; ni datos reales ni secretos en el repo.
7. **Flujo spec-kit:** `/speckit.specify` → `/speckit.plan` → `/speckit.tasks` → `/speckit.implement`.
8. **Flujo `/flow`.** Orquesta spec → plan → tasks → implementación → gates → revisión con agentes. Para a pedir OK tras la spec, tras el plan y antes del commit; nunca hace commit, push ni PR sin ese OK, que se da en el chat; el hook de vista previa solo deja constancia y no lo sustituye. El merge de la PR sigue la regla 10.
9. **Nada de terceros sin revisar.** Rige `../AGENTS.md` § Reglas comunes.
10. **PRs con automerge.** Tras el OK al commit, el asistente crea la PR y la mergea él mismo en cuanto sus checks están en verde, incluida la revisión automática, que falla con hallazgos bloqueantes o altos (el merge pasa por el hook de vista previa como el commit), y después actualiza `master` en local. Sustituye aquí a la regla global «las PRs las mergeo yo».

## Al empezar

- Dominio, roles o reglas de negocio: `.agents/context/dominio.md`.
- Automejora: rige `../AGENTS.md` § Automejora.

## Gates locales

- Backend: `cd backend && ./mvnw -B verify` con `JAVA_HOME` en Java 21. Con Rancher Desktop, exportar antes
  `TESTCONTAINERS_RYUK_DISABLED=true` (Ryuk no arranca); en CI no hace falta.
- App: `cd app && npm run lint && npm run typecheck && npx expo export -p web`.

@.agents/TOOLING-MATRIX.md
