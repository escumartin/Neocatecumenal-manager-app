# Matriz de herramientas

Único fichero del harness que nombra herramientas concretas. El resto es agnóstico: si una herramienta no tiene una
capacidad, se usa la alternativa manual de la última columna. Lo no comprobado se marca «sin verificar».

| Capacidad | Claude Code (verificado en este Mac) | Otras herramientas (sin verificar) | Alternativa manual |
|---|---|---|---|
| Instrucciones de entrada | `CLAUDE.md` es un symlink a `AGENTS.md`, que importa este fichero | leer `AGENTS.md` | leer `AGENTS.md` |
| Spec-kit | comandos `/speckit.*` en `.claude/commands/` y plantillas en `.specify/` | sin verificar | leer `.specify/` y seguir el flujo a mano |
| Memoria entre sesiones | `autoMemoryDirectory` en `.claude/settings.local.json` → `.agents/memory/` (índice `MEMORY.md`, primeras 200 líneas o 25 KB al arrancar) | leer `.agents/memory/MEMORY.md` al empezar | `.agents/context/` |
| Nombre de la sesión | lo escribe el usuario con `/rename <tema>`; el asistente solo lo propone | según la herramienta | — |
| Hooks | ninguno | — | — |

## Añadir una herramienta

1. Comprobar cómo lee las instrucciones del proyecto y apuntarla a `AGENTS.md`, sin copiar su contenido.
2. Apuntar su carpeta de skills del proyecto a `.agents/skills/` con un enlace, nunca una copia.
3. Añadir su columna aquí marcando qué está verificado y qué no.
