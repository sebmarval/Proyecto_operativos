# ÁvilaOS — Validación final (fases 17 y 18)

Fecha: 24/10/2026 · Rama: `develop` · Java 25 / NetBeans 25 (Maven)

## 1. Resumen

| Tipo de prueba | Clases | Verificaciones | Resultado |
|---|---|---|---|
| Unitarias (por componente) | 10 | 196 | ✅ 0 fallidas |
| Aceptación T01–T10 (sistema integrado, API del `Cluster`) | 1 (`AcceptanceTest`) | 41 | ✅ 10/10 escenarios aprobados |
| GUI (ejecución real con `config/prueba_completa.json`, ~290 ciclos) | — | revisión visual | ✅ sin excepciones |
| Auditoría de colecciones de Java | todo `src/` | — | ✅ solo `java.util.concurrent.Semaphore` (exigido) · ⚠️ ver pendiente 1 |

Cómo reproducir: en NetBeans, clic derecho sobre cada clase de `com.avilaos.tests` → *Run File*.
Las pruebas con hilos (`GlobalClockTest`, `RemoteTest`, `AcceptanceTest`) se ejecutaron 3 veces seguidas con el mismo resultado.

## 2. Pruebas unitarias

| Clase | Componente | OK |
|---|---|---|
| `DataStructuresTest` | `CustomLinkedList`, `CustomQueue` | 12 |
| `ProcessModelTest` | PCB/TCB, IDs únicos con hilos, deadline, E/S | 16 |
| `HardwareTest` | RAM y CPU | 14 |
| `KernelTest` | colas, admisión por RAM, E/S, deadlines | 17 |
| `GlobalClockTest` | reloj global, barrera de semáforos, pausa y velocidad | 12 |
| `SyncTest` | `SimSemaphore` y `BoundedBuffer` (incluye 2 hilos reales) | 21 |
| `ProducerConsumerTest` | micro-programa, mutex con RR, liberación al vencer, políticas en el kernel | 22 |
| `RemoteTest` | `Cluster`, latencia, despertares entre computadores, determinismo | 17 |
| `MetricsTest` | fórmulas (Jain, porcentajes) y métricas sobre simulación real | 36 |
| `ConfigTest` | JSON ida y vuelta, validaciones, `ClusterFactory` | 29 |

## 3. Escenarios de aceptación (Plan de Acción)

| ID | Escenario | Evidencia obtenida | Resultado |
|---|---|---|---|
| T01 | Ciclo de vida | `NEW → READY → RUNNING → BLOCKED(IO_WAIT) → READY → RUNNING → TERMINATED`; PC = instrucciones; RAM liberada | ✅ |
| T02 | Memoria insuficiente | RAM 64: B (40) espera en NEW hasta que A (40) termina (A fin ciclo 5, B entra ciclo 6); proceso > RAM total rechazado | ✅ |
| T03 | FCFS | fin P1=4, P2=7, P3=9: orden de llegada, sin expropiación, aunque P2 tenía mayor prioridad y deadline más cercano | ✅ |
| T04 | EDF y deadlines | fin por deadline (20 → 40 → 60); expropiación por deadline más cercano; deadline en 0 → terminado, contado como incumplido, RAM liberada | ✅ |
| T05 | Round Robin | quantum 2 → ráfagas `A2 B2 A2 B2`; cambio a 3 en ejecución → `B1 A3 B3 A3` (B completa su ráfaga de 3) | ✅ |
| T06 | Prioridades preventivas | igual prioridad no expropia; mayor prioridad expropia en el ciclo en que llega; el expropiado vuelve al final de la cola | ✅ |
| T07 | Semáforos | consumidor con buffer vacío → `BLOCKED(BUFFER_EMPTY)`, en la cola de `n` (n = −1), otro proceso toma la CPU, despierta tras `semSignal(n)` | ✅ |
| T08 | Buffer local | 2 productores + 1 consumidor, RR quantum 1: 10/10, nunca 0 > elementos > capacidad, se ejercitaron lleno/vacío/mutex, s=1 n=0 e=2 al final | ✅ |
| T09 | Buffer remoto | latencia 3: primer acceso completo en el ciclo 4; 15 bloqueos por latencia = 5 operaciones × 3 elementos; productor local 0; 27 vs 65 ciclos | ✅ |
| T10 | Dos computadoras | hilos reales: cada computadora ejecutó exactamente 1 ciclo por tick, ambos kernels en el ciclo del reloj, ambas empiezan en el ciclo 1, políticas independientes (RR / EDF), buffer compartido 6/6 | ✅ (+ checklist visual) |

### Comportamientos que conviene saber explicar en la defensa
- **T01:** si la CPU está libre, un proceso que vuelve de E/S pasa por READY y se despacha en el mismo ciclo.
- **T05:** al cambiar el quantum en ejecución, el proceso en CPU conserva lo que ya consumió de su ráfaga.
- **T06:** el proceso expropiado vuelve al **final** de la cola de listos; entre iguales manda el orden de la cola.
- **T09:** cada una de las 5 llamadas al sistema sobre un buffer remoto paga su latencia (decisión D6).

## 4. Requisitos de la GUI → dónde se ven

| Requisito del enunciado (secciones 6 y 7) | Dónde está en ÁvilaOS |
|---|---|
| Proceso en ejecución, PC, prioridad y deadline | Pestaña *Computador N* → recuadro "CPU – proceso en ejecución" |
| SO o programa de usuario | Mismo recuadro → campo **Modo** |
| Colas de nuevos, listos, bloqueados (con motivo) y terminados | Pestaña *Computador N* → 4 tablas; Bloqueados con columna **Motivo** |
| Elementos del PCB en colas y CPU | Columnas de las tablas + recuadro CPU (ID, nombre, tipo, estado, prioridad, memoria, deadline, restante, PC, MAR, TCB, computador) |
| Ocupación de memoria usada/libre | Pestaña *Computador N* → barra "Memoria principal (RAM)" |
| Política en uso y selector | Pestaña *Computador N* → "Planificación" (política + quantum + Aplicar) |
| Ciclo de reloj global | Panel inferior → "Ciclo actual" |
| Estado de cada buffer (elementos, capacidad, s/n/e, bloqueados) | Panel inferior → tabla "Buffers" |
| Log de eventos | Panel inferior → "Log de eventos" |
| En ejecución: política, duración del ciclo, quantum, nuevos procesos y buffers | Selector por computador · "Duración del ciclo (ms) + Aplicar" · botones **Crear proceso…** / **Crear buffer…** |
| Parámetros a archivo (ciclo, nº de computadores y RAM, política y quantum, latencia, procesos y buffers) | Ventana inicial de configuración → **Guardar JSON / Cargar JSON** |
| Métricas por computador y globales | Pestaña *Métricas* |
| Gráfica de utilización de CPU en el tiempo | Pestaña *Gráfica de utilización* (JFreeChart, una serie por computador) |
| Validación de tipo y rango en todos los campos | `ProcessDialog`, `BufferDialog`, `ConfigFrame`, `ComputerPanel`, `GlobalPanel` (mensaje de error, no se crea nada) |

El diseño difiere del anexo del enunciado a propósito: el propio anexo aclara que no hay que seguir su estilo.

## 5. Pendientes antes de la entrega

1. ⚠️ `tests/SchedulingTest` usa `java.util.Scanner` (menú interactivo; falla sin teclado). Es el único uso de
   `java.util` fuera de `Semaphore`. Convertirlo en prueba automática o eliminarlo (ya cubierto por T03–T06).
2. `CustomStack` / `StackNode` no se usan: eliminarlos.
3. Recomendado: pestaña **Resumen** con ambas computadoras visibles a la vez (proceso en CPU, PC, modo,
   política, RAM y tamaño de colas), para la demostración de T10.
4. Unir `develop` → `main` con un único PR el día de la entrega.
