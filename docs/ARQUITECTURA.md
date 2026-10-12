# ÁvilaOS — Contrato de arquitectura (Fase 2)

Este documento es el acuerdo de diseño entre los integrantes. Todo el código debe respetarlo.
Si algo aquí cambia, se cambia primero este archivo (en un PR) y luego el código.

## 1. Paquetes

| Paquete | Responsabilidad |
|---|---|
| `com.avilaos.datastructures` | Estructuras propias (`Node`, `CustomLinkedList`, `CustomQueue`, `CustomStack`). **Sin sincronización interna.** |
| `com.avilaos.enums` | Estados, tipos, políticas, modos y motivos de bloqueo. |
| `com.avilaos.model` | `ProcessControlBlock`, `ThreadControlBlock`, `ProcessIdGenerator`. |
| `com.avilaos.hardware` | `RAM`, `CPU`, `ClockListener` (interfaz), `GlobalClock`. |
| `com.avilaos.kernel` | `Kernel` (colas, admisión, despacho, terminación). |
| `com.avilaos.scheduling` | `SchedulingAlgorithm` (interfaz) + FCFS, EDF, RoundRobin, PreemptivePriority. |
| `com.avilaos.sync` | `SimSemaphore` (semáforo **simulado**) y `BoundedBuffer`. |
| `com.avilaos.system` | `Computer`, `Cluster`, `SimulationConfig`. |
| `com.avilaos.metrics` | Métricas por computador y globales. |
| `com.avilaos.gui` | Swing. Solo observa y envía órdenes; **no contiene lógica del SO**. |

## 2. Dos tipos de semáforo (pregunta segura en la defensa)

| | Semáforo simulado (`SimSemaphore`) | `java.util.concurrent.Semaphore` |
|---|---|---|
| Qué es | Parte del SO que simulamos: valor + cola propia de PCB | Herramienta real de Java |
| Bloquea a | Procesos simulados (pasan a BLOCKED y sueltan la CPU) | Hilos reales de Java |
| Uso | `s` (mutex), `n` (llenos), `e` (vacíos) de cada buffer | Proteger colas del kernel, buffers y la barrera del reloj |

No se usa `synchronized`: la exclusión mutua real se hace **solo** con `java.util.concurrent.Semaphore`.

## 3. Hilos y reloj global

- `GlobalClock` es un hilo. Cada `Computer` es otro hilo.
- Barrera por tick (con Semaphores de Java):
  1. El reloj hace `release()` del semáforo `tickStart` de cada computador.
  2. Cada computador ejecuta **exactamente un ciclo** y hace `release()` de `tickDone`.
  3. El reloj hace `acquire(N)` sobre `tickDone`, duerme la duración del ciclo (ms, configurable) y repite.
- Los milisegundos solo definen cuánto dura un tick en tiempo real. **Toda la lógica cuenta ciclos.**

## 4. Qué ocurre en un ciclo de un computador (orden fijo)

1. **Temporizadores de bloqueo**: E/S y latencia de red descuentan 1 ciclo; los que llegan a 0 pasan a READY.
2. **Admisión (largo plazo)**: de NEW a READY, en orden de llegada, mientras la RAM alcance.
3. **Planificación (corto plazo)**: si la CPU está libre, o la política decide expropiar, se despacha.
4. **Ejecución**: la CPU ejecuta **1 instrucción** del proceso en ejecución (PC y MAR +1).
5. **Post-ejecución**: termina (libera RAM), pide E/S (BLOCKED), agota quantum (READY) o se bloquea en semáforo.
6. **Deadlines**: todo proceso no terminado del computador descuenta 1; si llega a 0 se termina y libera RAM.
7. **Métricas** del ciclo (CPU ocupada/ociosa, tiempos de espera).

## 5. Modelo de instrucciones (decisiones D4 y D5)

- Toda instrucción tarda 1 ciclo.
- CPU bound: `totalInstructions` instrucciones de usuario.
- I/O bound: cada `ioEveryCycles` instrucciones ejecutadas pide E/S y queda BLOCKED (`IO_WAIT`) durante `ioDurationCycles` ciclos globales.
- Productor: por elemento `produceCycles` (usuario) + 5 de SO: `semWait(e)`, `semWait(s)`, añadir, `semSignal(s)`, `semSignal(n)`.
- Consumidor: por elemento 5 de SO: `semWait(n)`, `semWait(s)`, extraer, `semSignal(s)`, `semSignal(e)` + `consumeCycles` (usuario).
- **Una operación de semáforo/buffer = 1 instrucción = 1 ciclo.** Nunca se hacen las 5 en una sola llamada.
- Acceso a buffer remoto: cada operación bloquea al proceso `networkLatency` ciclos (`NETWORK_LATENCY`) antes de ejecutarse.

## 6. Planificación

- Una sola cola de listos por CPU (`CustomLinkedList<ProcessControlBlock>`).
- La cola **no se mantiene ordenada**: la política **recorre** la cola y elige el índice del siguiente proceso.
  Justificación: permite cambiar de política en ejecución sin reordenar nada.
- Interfaz `SchedulingAlgorithm`: `selectNextIndex(readyQueue)` y `shouldPreempt(running, readyQueue, quantumUsed)`.

## 7. Memoria

- `RAM` por computador con capacidad configurable (unidades de memoria).
- `allocate(n)` solo si hay espacio; `free(n)` al terminar.
- Los buffers ocupan RAM en su computador anfitrión.
- Sin disco, sin memoria virtual, sin particiones (extensible en el Proyecto 2).

## 8. Convenciones

- Código, clases y métodos en inglés; comentarios y documentación en español.
- Ramas `feat/*`, `fix/*`, `docs/*` desde `develop`; integración solo por PR.
- Commits: `feat:`, `fix:`, `chore:`, `docs:`, `test:`.
