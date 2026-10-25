# ÁvilaOS — Resultados experimentales (base para las conclusiones del informe)

Generados con `com.avilaos.tests.ExperimentsRunner` (Run File). Las corridas son **deterministas**
(ticks manuales, sin azar): cualquiera obtiene exactamente estos números.

## Experimento 1 — Misma carga con cada política

2 computadores, 11 procesos (9 CPU bound y 2 I/O bound), llegadas escalonadas (ciclos 0 a 14), deadlines
holgados y ajustados. Ningún proceso vence por falta de RAM: solo influye la planificación.

| Política | Ciclos totales | Deadlines cumplidos | Vencidos | Respuesta prom. | Espera en listos prom. | Retorno prom. | Equidad (Jain) | Utilización CPU |
|---|---|---|---|---|---|---|---|---|
| FCFS | 60 | 72.7 % | 3 (Urgente0, Critico0, Urgente1) | 15.75 | 20.18 | 32.27 | 0.486 | 87.5 % |
| EDF | 63 | 100.0 % | 0 | 5.45 | 13.18 | 26.18 | 0.816 | 93.7 % |
| Round Robin q=2 | 62 | 90.9 % | 1 (Critico0) | 3.91 | 24.64 | 37.64 | 0.949 | 94.4 % |
| Round Robin q=5 | 62 | 90.9 % | 1 (Critico0) | 8.64 | 25.45 | 38.36 | 0.931 | 94.4 % |
| Prioridades apropiativas | 63 | 100.0 % | 0 | 12.27 | 16.36 | 29.36 | 0.756 | 93.7 % |

**Lectura (borrador de conclusiones):**
- **FCFS** es la peor en casi todo: los procesos cortos y urgentes que llegan tarde esperan detrás de los
  largos (efecto convoy), vence 3 deadlines y tiene la peor equidad (0.486) y la peor respuesta. Además
  desaprovecha más CPU (87.5 %) porque, cuando un I/O bound vuelve de la E/S, no puede adelantarse.
- **EDF** es la mejor para cumplir plazos (100 %) y da el menor tiempo de retorno: ordena exactamente por lo
  que se mide. Su respuesta es buena porque los urgentes entran de inmediato.
- **Round Robin** da la **mejor respuesta** (q=2: 3.91 ciclos) y la **mejor equidad** (Jain 0.949): todos
  avanzan un poco. El precio es el mayor tiempo de retorno y de espera, y vence 1 deadline porque no
  distingue urgencia. Un quantum menor mejora la respuesta (3.91 vs 8.64) a costa de más cambios de proceso.
- **Prioridades** también cumple el 100 % en esta carga, pero **porque aquí los urgentes tienen prioridad alta**:
  depende de que el usuario asigne bien las prioridades (EDF lo logra solo con los deadlines). Su equidad es
  menor (0.756): favorece a los prioritarios y los de prioridad baja pueden esperar mucho (riesgo de inanición).
- El **tiempo total** casi no cambia (60–63 ciclos): el trabajo total es el mismo; la política decide
  **quién** espera, no **cuánto** trabajo hay.

## Experimento 2 — Productor-consumidor local vs remoto

Productor en PC0 (8 elementos, 2 ciclos de trabajo c/u), buffer de capacidad 2 en PC0, FCFS.
El consumidor está en PC0 (local) o en PC1 (remoto) con distintas latencias.

| Consumidor | Latencia | Ciclos totales | Elementos por 100 ciclos | Bloqueado en semáforos (prom.) | Bloqueado por red (consumidor) | Utilización PC0 | Utilización PC1 |
|---|---|---|---|---|---|---|---|
| Local (PC0) | 0 | 112 | 7.14 | 15.50 | 0 | 100.0 % | 0.0 % |
| Remoto (PC1) | 0 | 62 | 12.90 | 3.50 | 0 | 90.3 % | 90.3 % |
| Remoto (PC1) | 1 | 102 | 7.84 | 19.00 | 40 | 54.9 % | 94.1 % |
| Remoto (PC1) | 2 | 140 | 5.71 | 31.50 | 80 | 40.0 % | 68.6 % |
| Remoto (PC1) | 4 | 219 | 3.65 | 61.00 | 160 | 25.6 % | 43.8 % |
| Remoto (PC1) | 8 | 376 | 2.13 | 117.00 | 320 | 14.9 % | 25.5 % |

**Lectura (borrador de conclusiones):**
- **Hay un compromiso entre paralelismo y latencia.** Con latencia 0, el consumidor remoto termina en casi la
  **mitad** de ciclos (62 vs 112) porque productor y consumidor corren en **dos CPU a la vez**; en local
  comparten una sola CPU (100 % de uso en PC0 y PC1 ocioso).
- Desde latencia **1** la ventaja casi desaparece (102 vs 112) y desde **2** el remoto ya es **más lento** (140).
  Con latencia 8 tarda 3.4 veces más que el local.
- El costo de la red crece **linealmente** con la latencia: el consumidor queda bloqueado por red
  `5 operaciones × 8 elementos × L` ciclos (40, 80, 160, 320), exactamente lo que define la decisión D6.
- La latencia también aumenta el bloqueo en semáforos **del productor local**: como el consumidor remoto
  extrae más lento, el buffer se llena y el productor espera en `e` (de 3.5 a 117 ciclos promedio), y la
  utilización de PC0 cae (90 % → 15 %). La lentitud de la red se propaga al otro computador.
- Conclusión práctica: conviene ubicar el buffer en el computador del proceso que más lo usa, o en el
  del consumidor cuando la latencia es alta; repartir procesos en distintos computadores solo compensa
  si la latencia es muy baja.

## Experimento 3 — Capacidad del buffer

2 productores + 2 consumidores locales (12 elementos), Round Robin q=2, un solo computador.

| Capacidad | Ciclos totales | Bloqueado en semáforos (prom.) | Bloqueos por lleno | Bloqueos por vacío | Bloqueos por mutex |
|---|---|---|---|---|---|
| 1 | 168 | 80.50 | 11 | 12 | 0 |
| 2 | 168 | 41.25 | 7 | 11 | 9 |
| 4 | 168 | 24.00 | 0 | 4 | 16 |
| 8 | 168 | 24.00 | 0 | 4 | 16 |

**Lectura (borrador de conclusiones):**
- Con **una sola CPU siempre ocupada**, el tiempo total es igual a la suma de instrucciones (168) sin importar
  la capacidad: el buffer no cambia cuánto trabajo hay, sino **quién espera y por qué**.
- Un buffer más grande **reduce el tiempo bloqueado** (80.5 → 24 ciclos) y elimina los bloqueos por
  buffer lleno; a partir de capacidad 4 ya no mejora (el buffer nunca se llena).
- Con más capacidad aparecen más bloqueos en el **mutex s**: hay más procesos con permiso para entrar a la
  sección crítica al mismo tiempo, y con RR q=2 alguno es expropiado dentro de ella. Esto muestra la
  diferencia entre los semáforos de **conteo** (n, e: cuántos elementos/espacios) y el **binario** (s:
  exclusión mutua), que el enunciado señala como la confusión más común.
