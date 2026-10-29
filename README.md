# Proyecto_operativos
ÁvilaOS es un simulador académico de un sistema operativo distribuido desarrollado en Java 25 bajo la plataforma NetBeans 25, diseñado para coordinar la ejecución concurrente de un clúster de computadores sincronizados mediante un reloj global. El sistema implementa un modelo de ejecución monohilo donde la unidad de planificación es el propio proceso, asociando de forma unívoca su bloque de control de procesos con el de hilo. Toda la arquitectura está estructurada bajo principios de diseño orientado a objetos y absteniéndose de utilizar el framework de colecciones de Java, por lo que las estructuras de datos para las colas de estados y memoria fueron programadas desde cero.

El núcleo de cada máquina administra de forma independiente la memoria RAM acotada y la planificación de CPU mediante políticas intercambiables en tiempo de ejecución, como FCFS, EDF, Round Robin y prioridades apropiativas. Asimismo, el sistema resuelve la comunicación inter-nodo mediante el modelo del productor-consumidor utilizando semáforos y buffers acotados con penalización por latencia de red en accesos remotos. El estado del clúster, sus métricas de rendimiento y las variaciones de la CPU son monitoreados mediante una interfaz gráfica completamente desacoplada de la lógica del simulador.

## Integrantes
- Sebastián Marval
- Alejandro Marcano

## Requisitos
- JDK 25 y NetBeans 25 (proyecto Maven).
- Dependencias (las descarga Maven): Gson 2.11.0 y JFreeChart 1.5.5.

## Cómo ejecutar
1. Abrir la carpeta del repositorio en NetBeans (**File → Open Project**).
2. Clic derecho sobre el proyecto → **Build with Dependencies** (la primera vez descarga Gson y JFreeChart).
3. **Run Project** (F6). La clase principal es `com.avilaos.AvilaOS`.
4. En la vista de configuración: **Cargar JSON…** (por ejemplo `config/default.json`) o llenar las tablas a mano → **Iniciar simulación**.

## Configuraciones incluidas
- `config/default.json`: ejemplo general (2 computadores, 4 procesos, 1 buffer).
- `config/prueba_completa.json` y `config/prueba_completa_2.json`: cargas mixtas grandes.
- `config/escenarios/`: un escenario por funcionalidad (FCFS, Round Robin, prioridades, EDF, RAM insuficiente, E/S, productor-consumidor local y remoto, deadlines, asignación automática). Lo que se debe observar en cada uno está en `config/escenarios/README.md`.

## Pruebas
Las pruebas están en `src/main/java/com/avilaos/tests/` (sin JUnit). Cada clase se ejecuta con clic derecho → **Run File** (Shift+F6) e imprime cuántas verificaciones pasan. `AcceptanceTest` cubre los 10 escenarios de aceptación y `ExperimentsRunner` genera las tablas de `docs/RESULTADOS_EXPERIMENTOS.md`.

## Documentación
- `docs/ARQUITECTURA.md`: paquetes, ciclo del kernel y decisiones de diseño.
- `docs/VALIDACION.md`: cómo se validó cada requisito.
- `docs/RESULTADOS_EXPERIMENTOS.md`: comparación de políticas, local vs remoto y capacidad del buffer.
- `docs/uml/`: diagrama de clases y diagrama de actividades del ciclo del reloj (fuente PlantUML e imagen).