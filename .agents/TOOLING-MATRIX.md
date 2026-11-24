# Matriz de herramientas

Único fichero del harness que nombra herramientas concretas. El resto es agnóstico: si una herramienta no tiene una
capacidad, se usa la alternativa manual de la última columna. Lo no comprobado se marca «sin verificar».

| Capacidad | Claude Code (verificado en este Mac) | Otras herramientas (sin verificar) | Alternativa manual |
|---|---|---|---|
| Instrucciones de entrada | `CLAUDE.md` es un symlink a `AGENTS.md`, que importa este fichero | leer `AGENTS.md` | leer `AGENTS.md` |
| Spec-kit | comandos `/speckit.*` en `.claude/commands/` y plantillas en `.specify/` | sin verificar | leer `.specify/` y seguir el flujo a mano |
| Agentes | `.agents/agents/*.md` enlazados en `.claude/agents/` | sin verificar | leer `.agents/agents/<agente>.md` y seguir su rol a mano |
| Comandos | `.claude/commands/flow.md` y `speckit.*` (ficheros reales) | sin verificar | seguir `flow.md` paso a paso |
| Memoria entre sesiones | `autoMemoryDirectory` en `.claude/settings.local.json` → `.agents/memory/` (índice `MEMORY.md`, primeras 200 líneas o 25 KB al arrancar) | leer `.agents/memory/MEMORY.md` al empezar | `.agents/context/` |
| Nombre de la sesión | lo escribe el usuario con `/rename <tema>`; el asistente solo lo propone | según la herramienta | — |
| Hooks | `PreToolUse` `Bash` en `.claude/settings.json` → `.agents/scripts/require-preview.py`: deniega `git commit\|push` y `gh pr create\|merge` salvo `# preview: <texto>` al final, y entonces lo aprueba solo (la línea queda en el chat como vista previa). `Stop` → `.agents/scripts/run-gates.py`: si hay cambios sin commitear en `backend/` o `app/`+`api/`, ejecuta `./mvnw -B -q verify` o `npm run lint && npm run typecheck` y bloquea el cierre del turno si fallan (caché por estado en `.git`, no reintenta con `stop_hook_active`, timeout 900 s) | — | revisar el diff y el mensaje antes de commitear; ejecutar los gates locales a mano |

## Añadir una herramienta

1. Comprobar cómo lee las instrucciones del proyecto y apuntarla a `AGENTS.md`, sin copiar su contenido.
2. Apuntar su carpeta de skills del proyecto a `.agents/skills/` con un enlace, nunca una copia.
3. Añadir su columna aquí marcando qué está verificado y qué no.
