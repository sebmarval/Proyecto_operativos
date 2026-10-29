# Escenarios de prueba (uno por funcionalidad)

Cada archivo prueba **una sola cosa**. Se cargan desde la vista de configuración
(**Cargar JSON…** → **Iniciar simulación**). Todos usan ciclos de 500 ms para poder
seguirlos a ojo; si se quiere más rápido, cambiar la duración del ciclo en el panel global.

Cada JSON trae un campo `"descripcion"` con lo que se debe observar (Gson lo ignora al cargar).
Los resultados de abajo se verificaron simulando cada archivo ciclo a ciclo.

| Archivo | Qué prueba | Qué se debe ver |
|---|---|---|
| `01_fcfs.json` | FCFS | En PC0 corren en orden de llegada: Largo (ciclos 1–10), Corto (11–13), Urgente (14–15). La prioridad y la duración no importan y no hay expropiación. |
| `02_round_robin.json` | Round Robin, q = 2 | Turnos de 2 ciclos: A A B B C C A A B B C C A A B B C C. Al agotar el quantum, el proceso vuelve al final de Listos. |
| `03_prioridades.json` | Prioridades apropiativas | Interactivo (prioridad 0) corre 2 ciclos, se va a E/S y entra Fondo (prioridad 5). Cuando Interactivo vuelve, **expropia** a Fondo (ciclos 5 y 9). Fondo no pierde su avance. |
| `04_edf.json` | EDF | La lista está al revés (Lejos, Medio, Cerca), pero se ejecuta Cerca (deadline 10) → Medio (30) → Lejos (90). Todos cumplen. |
| `05_memoria_insuficiente.json` | Planificador de largo plazo / RAM | PC0 tiene 64 MB y llegan 3 procesos de 32 MB. Tercero se queda en **Nuevos** hasta que Primero termina y libera RAM. |
| `06_entrada_salida_dma.json` | E/S por DMA | Lector se bloquea por IO_WAIT 5 ciclos y la CPU ejecuta Calculo mientras tanto. Al final, la CPU queda ociosa (ciclos 17–20) porque solo queda Lector y está en E/S. |
| `07_productor_consumidor_local.json` | Productor-consumidor local | Buffer B1 (capacidad 2) en PC0. El productor es rápido y el consumidor lento, así que el productor se bloquea en **e** (BUFFER_FULL). Al final la tabla de buffers muestra Producidos = Consumidos = 6. |
| `08_productor_consumidor_remoto.json` | Acceso remoto con latencia | Consumidor en PC1 y buffer en PC0, latencia 5. Cada operación del consumidor sobre B1 hace 1 ciclo de SO (solicitud de red) y luego 5 ciclos BLOQUEADO por NETWORK_LATENCY. Termina en el ciclo 124; el caso local equivalente tarda mucho menos. |
| `09_deadline_vencido.json` | Deadlines | Con FCFS, Apurado (deadline 8) espera detrás de Pesado y termina como **incumplido** en el ciclo 8 sin ejecutarse. Si se cambia la política de PC0 a EDF antes de iniciar, Apurado cumple. |
| `10_asignacion_automatica.json` | Asignación automática (`computerId = -1`) | Auto1 → PC0, Auto2 → PC1, Auto3 → PC2, Auto4 → PC0 (va al menos cargado; si hay empate, al de menor id). Grande (200 MB) solo cabe en PC0 y va allí aunque PC2 esté menos cargado. |

Notas para la defensa:
- En la vista de resumen, la RAM de PC0 en los escenarios 07 y 08 nunca baja de 2 MB: el buffer **reserva su memoria** en el computador anfitrión (1 MB por casilla). Es parte del diseño.
- Para comparar políticas con la misma carga: cargar 01, volver a la configuración, cambiar la política en la tabla de computadores e iniciar de nuevo.
