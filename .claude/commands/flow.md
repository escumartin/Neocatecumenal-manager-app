---
description: Orquesta el flujo completo de una feature, de la especificación a la PR mergeada, con resúmenes tras la spec y tras el plan.
argument-hint: <descripción o feature>
---

## Entrada

`$ARGUMENTS` es la descripción de la feature o su identificador. Si está vacío, pregunta qué se quiere construir y para.

## Reglas

- Commit, push, PR y merge los haces tú, sin pedir OK, solo con los gates y la revisión en verde.
- Delega cada paso especializado en su agente, uno nuevo por invocación. Los agentes de revisión son de solo lectura: los arreglos los hace `tdd` o `build-resolver`.
- Tras la spec y tras el plan no se espera OK: se resume lo decidido y se sigue. Las decisiones de sí o no se toman con la recomendación propia; solo se pregunta lo que no es binario (Clarify o un texto que debe redactar el usuario).
- Responde en castellano. Código, tests y mensajes de commit en inglés, sin comentarios.

## Pasos

1. **Specify.** Ejecuta `/speckit.specify $ARGUMENTS`.
2. **Clarify.** Ejecuta `/speckit.clarify` y recoge las respuestas del usuario en la spec.
3. **Critic sobre la spec.** Lanza `critic` con la ruta de `spec.md` y la tarea. Si devuelve FAIL, corrige la spec y lanza un `critic` nuevo.
4. **Resumen 1.** Resume la spec y los hallazgos del critic y sigue.
5. **Plan.** Lanza `architect` para producir `plan.md` y sus artefactos de diseño con `/speckit.plan`.
6. **Critic sobre el plan.** Lanza `critic` con `plan.md` y su `spec.md`. Con FAIL, corrige y repite con un agente nuevo.
7. **Resumen 2.** Resume el plan, el Constitution Check y los hallazgos del critic y sigue.
8. **Tasks.** Ejecuta `/speckit.tasks`.
9. **Analyze.** Ejecuta `/speckit.analyze`. Resuelve las inconsistencias críticas antes de implementar.
10. **Implement.** Ejecuta `/speckit.implement` lanzando `tdd` para cada unidad: el test que falla va primero, capa a capa. Si un gate falla por compilación o dependencias, lanza `build-resolver`.
11. **Gates.**
    - Backend: `cd backend && ./mvnw -B verify` con `JAVA_HOME` en Java 25. Con Rancher Desktop, exporta antes `TESTCONTAINERS_RYUK_DISABLED=true`.
    - App: `cd app && npm run lint && npm run typecheck && npx expo export -p web`.
    - Ejecuta solo los de la parte tocada. Con un gate en rojo, corrige y repite; si no se resuelve, para y dilo.
12. **Revisión.** Mira el diff (`git diff --stat` y `git status`) y lanza en paralelo, nuevos y de solo lectura:
    - `self-reviewer`, siempre;
    - `backend-reviewer`, si toca `backend/`;
    - `frontend-reviewer`, si toca `app/`;
    - `security-reviewer`, si toca endpoints, persistencia, validación, autenticación, configuración o datos personales;
    - `observability-reviewer`, si toca logs, métricas, health o gestión de errores;
    - `docs-reviewer`, si toca documentación, `AGENTS.md` o `.agents/`;
    - `qa-backend`, si hay endpoints nuevos o modificados.
    Corrige los hallazgos con `tdd` y repite gates y revisión hasta que no queden hallazgos bloqueantes.
13. **Commit, PR y merge.** Con los gates y la revisión en verde: commit con mensaje en inglés, push, PR y automerge según la regla 10 de `AGENTS.md`. Si algo queda parcial o en rojo, no hagas commit: para y dilo. Al terminar, resume qué ha cambiado, el resultado real de los gates, la URL de la PR y los pendientes.
