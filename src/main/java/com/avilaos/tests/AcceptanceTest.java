/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.tests;

import com.avilaos.enums.BlockReason;
import com.avilaos.enums.ProcessState;
import com.avilaos.enums.ProcessType;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.kernel.EventLogger;
import com.avilaos.kernel.Kernel;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.sync.BoundedBuffer;
import com.avilaos.sync.BufferStatus;
import com.avilaos.system.Cluster;

/**
 * Pruebas de aceptación T01–T10 del Plan de Acción
 *
 * A diferencia de las pruebas unitarias, aquí se usa SOLO la API pública del
 * Cluster (el sistema integrado, tal como lo usan la GUI y la configuración).
 * Cada escenario imprime la evidencia que respalda su resultado.
 *
 *
 * @author SebMarval
 */
public class AcceptanceTest {

    private static int passed = 0;
    private static int failed = 0;
    private static int scenariosFailed = 0;

    public static void main(String[] args) throws InterruptedException {
        scenario("T01", "Ciclo de vida de un proceso", AcceptanceTest::t01Lifecycle);
        scenario("T02", "Memoria insuficiente", AcceptanceTest::t02Memory);
        scenario("T03", "FCFS", AcceptanceTest::t03Fcfs);
        scenario("T04", "EDF y deadlines", AcceptanceTest::t04Edf);
        scenario("T05", "Round Robin y quantum configurable", AcceptanceTest::t05RoundRobin);
        scenario("T06", "Prioridades preventivas", AcceptanceTest::t06Priority);
        scenario("T07", "Bloqueo y desbloqueo con semáforos", AcceptanceTest::t07Semaphores);
        scenario("T08", "Productor-consumidor local", AcceptanceTest::t08LocalBuffer);
        scenario("T09", "Productor-consumidor remoto con latencia", AcceptanceTest::t09RemoteBuffer);
        scenario("T10", "Dos computadoras coordinadas por el reloj global", AcceptanceTest::t10TwoComputers);

        System.out.println();
        System.out.println("================================================");
        System.out.println("Verificaciones OK: " + passed + " | Fallidas: " + failed);
        System.out.println("Escenarios con fallas: " + scenariosFailed + " de 10");
        System.out.println("T10 incluye además la revisión visual de la GUI (ver checklist impreso).");
        System.out.println("================================================");
    }

    // ===================================================================== T01
    /**
     * Un I/O bound (A) entre dos CPU bound (B y C) en FCFS. A debe recorrer todos
     * los estados: NEW -> READY -> RUNNING -> BLOCKED (E/S) -> READY -> RUNNING -> TERMINATED.
     * C mantiene la CPU ocupada cuando A vuelve de la E/S, para que se vea su
     * paso por READY (si la CPU estuviera libre, entraría y se despacharía en el mismo ciclo).
     */
    private static void t01Lifecycle() {
        Cluster cl = cluster(SchedulingPolicy.FCFS, 2, 0);
        ProcessControlBlock b = cpu("B", 0, 1, 10, 500, 6);
        ProcessControlBlock a = new ProcessControlBlock("A", 0, ProcessType.IO_BOUND, 1, 10, 500, 4);
        a.configureIO(2, 3);
        ProcessControlBlock c = cpu("C", 0, 1, 10, 500, 8);
        cl.submit(b);
        cl.submit(a);
        cl.submit(c);

        String trace = a.getState().toString();
        ProcessState last = a.getState();
        int cycle = 1;
        while (cycle <= 100 && !done(a, b, c)) {
            tick(cl, cycle++);
            if (a.getState() != last) {
                last = a.getState();
                trace += " -> " + last + (last == ProcessState.BLOCKED ? "(" + a.getBlockReason() + ")" : "");
            }
        }
        evidence("A: " + trace);
        check("pasa por NEW, READY, RUNNING, BLOCKED y TERMINATED en ese orden",
                trace.matches("NEW.*READY.*RUNNING.*BLOCKED.*READY.*RUNNING.*TERMINATED"));
        check("se bloquea por E/S (IO_WAIT)", trace.contains("BLOCKED(IO_WAIT)"));
        check("ejecutó todas sus instrucciones (PC = 4, restante = 0)",
                a.getProgramCounter() == 4 && a.getRemainingTime() == 0);
        check("al terminar libera su memoria", ram(cl, 0) == 0);
    }

    // ===================================================================== T02
    /** RAM 64: A (40) entra; B (40) queda en NEW hasta que A termina. Uno de 100 se rechaza. */
    private static void t02Memory() {
        Cluster cl = new Cluster(new int[]{64, 64}, SchedulingPolicy.FCFS, 2, 0, 0, EventLogger.SILENT);
        ProcessControlBlock a = cpu("A", 0, 1, 40, 500, 5);
        ProcessControlBlock b = cpu("B", 0, 1, 40, 500, 3);
        cl.submit(a);
        cl.submit(b);

        int cycle = 1;
        boolean bWaitedInNew = false;
        while (cycle <= 100 && !done(a, b)) {
            tick(cl, cycle++);
            if (a.getState() != ProcessState.TERMINATED && b.getState() == ProcessState.NEW) {
                bWaitedInNew = true;
            }
            if (a.getState() != ProcessState.TERMINATED && b.getState() != ProcessState.NEW) {
                check("B nunca sale de NEW mientras A ocupa la RAM", false);
                return;
            }
        }
        evidence("A termina en el ciclo " + a.getFinishCycle() + "; B es admitido después ("
                + "primer ciclo en CPU: " + b.getFirstRunCycle() + ")");
        check("B esperó en NEW por falta de RAM", bWaitedInNew);
        check("B se admite solo después de que A libera", b.getFirstRunCycle() > a.getFinishCycle());
        check("un proceso más grande que la RAM total se rechaza",
                throwsIAE(() -> cl.submit(cpu("Gigante", 0, 1, 100, 500, 5))));
    }

    // ===================================================================== T03
    /** FCFS: terminan en el orden de llegada aunque tengan otras prioridades y deadlines. */
    private static void t03Fcfs() {
        Cluster cl = cluster(SchedulingPolicy.FCFS, 2, 0);
        ProcessControlBlock p1 = cpu("P1", 0, 9, 10, 500, 4);
        ProcessControlBlock p2 = cpu("P2", 0, 0, 10, 20, 3);    // más prioritario y urgente: igual espera
        ProcessControlBlock p3 = cpu("P3", 0, 5, 10, 500, 2);
        cl.submit(p1);
        cl.submit(p2);
        cl.submit(p3);
        runUntilDone(cl, 100, p1, p2, p3);
        evidence("fin: P1=" + p1.getFinishCycle() + ", P2=" + p2.getFinishCycle() + ", P3=" + p3.getFinishCycle());
        check("terminan en orden de llegada (P1, P2, P3)",
                p1.getFinishCycle() < p2.getFinishCycle() && p2.getFinishCycle() < p3.getFinishCycle());
        check("sin expropiación: cada uno corre de corrido (4 + 3 + 2 = 9 ciclos)",
                p1.getFinishCycle() == 4 && p2.getFinishCycle() == 7 && p3.getFinishCycle() == 9);
    }

    // ===================================================================== T04
    /** EDF: atiende primero el deadline más cercano, expropia por deadline y vence al que no alcanza. */
    private static void t04Edf() {
        Cluster cl = cluster(SchedulingPolicy.EDF, 2, 0);
        ProcessControlBlock lejano = cpu("Lejano", 0, 1, 10, 60, 4);
        ProcessControlBlock cercano = cpu("Cercano", 0, 1, 10, 20, 4);
        ProcessControlBlock medio = cpu("Medio", 0, 1, 10, 40, 4);
        cl.submit(lejano);
        cl.submit(cercano);
        cl.submit(medio);
        runUntilDone(cl, 100, lejano, cercano, medio);
        evidence("fin: Cercano(dl 20)=" + cercano.getFinishCycle() + ", Medio(dl 40)=" + medio.getFinishCycle()
                + ", Lejano(dl 60)=" + lejano.getFinishCycle());
        check("orden por deadline: Cercano, Medio, Lejano",
                cercano.getFinishCycle() < medio.getFinishCycle() && medio.getFinishCycle() < lejano.getFinishCycle());

        Cluster cl2 = cluster(SchedulingPolicy.EDF, 2, 0);
        ProcessControlBlock largo = cpu("Largo", 0, 1, 10, 100, 10);
        cl2.submit(largo);
        tick(cl2, 1);
        tick(cl2, 2);
        ProcessControlBlock urgente = cpu("Urgente", 0, 1, 10, 5, 2);
        cl2.submit(urgente);
        tick(cl2, 3);
        check("un proceso con deadline más cercano expropia al que corre",
                urgente.getState() == ProcessState.RUNNING && largo.getState() == ProcessState.READY);

        Cluster cl3 = cluster(SchedulingPolicy.EDF, 2, 0);
        ProcessControlBlock imposible = cpu("Imposible", 0, 1, 10, 3, 20);
        cl3.submit(imposible);
        runUntilDone(cl3, 50, imposible);
        Kernel k = cl3.getComputer(0).getKernel();
        evidence("Imposible: deadline 3 con 20 instrucciones -> terminado en el ciclo " + imposible.getFinishCycle());
        check("al llegar el deadline a 0 el proceso se termina y cuenta como incumplido",
                imposible.isDeadlineMissed() && imposible.getFinishCycle() == 3 && k.getDeadlineMissedCount() == 1);
        check("libera la RAM al vencer", ram(cl3, 0) == 0);
    }

    // ===================================================================== T05
    /** Round Robin: las ráfagas duran exactamente el quantum, y el quantum se cambia en ejecución. */
    private static void t05RoundRobin() {
        Cluster cl = cluster(SchedulingPolicy.ROUND_ROBIN, 2, 0);
        ProcessControlBlock a = cpu("A", 0, 1, 10, 500, 12);
        ProcessControlBlock b = cpu("B", 0, 1, 10, 500, 12);
        cl.submit(a);
        cl.submit(b);
        Kernel k = cl.getComputer(0).getKernel();

        String bursts2 = runAndRecordBursts(cl, 1, 8);           // 8 ciclos con quantum 2
        k.setSchedulingPolicy(SchedulingPolicy.ROUND_ROBIN, 3);   // cambio en ejecución
        String bursts3 = runAndRecordBursts(cl, 9, 20);           // 12 ciclos con quantum 3
        evidence("quantum 2 -> ráfagas " + bursts2);
        evidence("quantum 3 -> ráfagas " + bursts3);
        check("con quantum 2 se alternan cada 2 ciclos", bursts2.equals("A2 B2 A2 B2"));
        // B ya llevaba 2 ciclos de su quantum cuando se cambió: con el nuevo quantum (3) le
        // queda 1 ciclo ("B1", que completa su ráfaga de 3); luego todas las ráfagas son de 3.
        check("tras cambiar el quantum a 3 en ejecución, las ráfagas duran 3 (B completa su ráfaga: B1)",
                bursts3.startsWith("B1 A3 B3 A3"));
        check("el kernel reporta el nuevo quantum", k.getQuantum() == 3);
    }

    // ===================================================================== T06
    /** Prioridades preventivas: llega uno más prioritario y expropia; uno de igual prioridad no. */
    private static void t06Priority() {
        Cluster cl = cluster(SchedulingPolicy.PREEMPTIVE_PRIORITY, 2, 0);
        ProcessControlBlock bajo = cpu("Bajo", 0, 5, 10, 500, 8);
        cl.submit(bajo);
        tick(cl, 1);
        tick(cl, 2);
        ProcessControlBlock igual = cpu("Igual", 0, 5, 10, 500, 2);
        cl.submit(igual);
        tick(cl, 3);
        check("uno de IGUAL prioridad no expropia", bajo.getState() == ProcessState.RUNNING);

        ProcessControlBlock alto = cpu("Alto", 0, 1, 10, 500, 2);
        cl.submit(alto);
        tick(cl, 4);
        check("uno de MAYOR prioridad (número menor) expropia en el ciclo en que llega",
                alto.getState() == ProcessState.RUNNING && bajo.getState() == ProcessState.READY);
        runUntilDone(cl, 100, bajo, igual, alto);
        evidence("fin: Alto=" + alto.getFinishCycle() + ", Bajo=" + bajo.getFinishCycle() + ", Igual=" + igual.getFinishCycle());
        // Bajo fue expropiado y volvió al FINAL de la cola, detrás de Igual (misma prioridad,
        // ya estaba esperando): entre iguales se respeta el orden de la cola de listos.
        check("Alto termina primero; luego Igual (esperaba antes en la cola) y al final Bajo",
                alto.getFinishCycle() < igual.getFinishCycle() && igual.getFinishCycle() < bajo.getFinishCycle());
    }

    // ===================================================================== T07
    /**
     * Un consumidor sobre buffer vacío: se bloquea (BUFFER_EMPTY), sale de la CPU
     * (otro proceso la usa), aparece en la cola del semáforo n, y despierta cuando
     * el productor hace semSignal(n).
     */
    private static void t07Semaphores() {
        Cluster cl = cluster(SchedulingPolicy.FCFS, 2, 0);
        BoundedBuffer buf = cl.createBuffer("B", 2, 0);
        ProcessControlBlock cons = ProcessControlBlock.createConsumer("Cons", 0, 1, 10, 500, buf, 1, 1);
        ProcessControlBlock otro = cpu("Otro", 0, 1, 10, 500, 3);
        cl.submit(cons);
        cl.submit(otro);

        tick(cl, 1);   // Cons ejecuta semWait(n) con n = 0 -> se bloquea
        BufferStatus st = buf.snapshot();
        evidence("ciclo 1: Cons " + cons.getState() + "(" + cons.getBlockReason() + "), n = " + st.valueN()
                + ", esperan en n: " + st.waitingN().length);
        check("se bloquea con motivo BUFFER_EMPTY",
                cons.getState() == ProcessState.BLOCKED && cons.getBlockReason() == BlockReason.BUFFER_EMPTY);
        check("entra a la cola del semáforo n (n = -1)", st.valueN() == -1
                && st.waitingN().length == 1 && st.waitingN()[0] == cons);
        tick(cl, 2);
        check("abandona la CPU: otro proceso la ocupa en el ciclo siguiente",
                cl.getComputer(0).getKernel().getCpu().getRunning() == otro);

        ProcessControlBlock prod = ProcessControlBlock.createProducer("Prod", 0, 0, 10, 500, buf, 1, 1);
        cl.submit(prod);
        int cycle = 3;
        while (cycle <= 60 && cons.getState() == ProcessState.BLOCKED) {
            tick(cl, cycle++);
        }
        evidence("Cons despierta en el ciclo " + (cycle - 1) + " tras el semSignal(n) del productor");
        check("despierta tras el semSignal(n) (deja de estar bloqueado)", cons.getState() != ProcessState.BLOCKED);
        runUntilDone(cl, 100, cons, otro, prod);
        st = buf.snapshot();
        check("al final los semáforos quedan en s=1, n=0, e=2", st.valueS() == 1 && st.valueN() == 0 && st.valueE() == 2);
    }

    // ===================================================================== T08
    /** Dos productores y un consumidor locales con RR quantum 1: nunca se desborda ni se pierde nada. */
    private static void t08LocalBuffer() {
        Cluster cl = cluster(SchedulingPolicy.ROUND_ROBIN, 1, 0);
        BoundedBuffer buf = cl.createBuffer("B", 2, 0);
        ProcessControlBlock p1 = ProcessControlBlock.createProducer("P1", 0, 1, 10, 3000, buf, 5, 1);
        ProcessControlBlock p2 = ProcessControlBlock.createProducer("P2", 0, 1, 10, 3000, buf, 5, 2);
        ProcessControlBlock c1 = ProcessControlBlock.createConsumer("C1", 0, 1, 10, 3000, buf, 10, 1);
        cl.submit(p1);
        cl.submit(p2);
        cl.submit(c1);

        boolean withinBounds = true;
        boolean sawFull = false;
        boolean sawEmpty = false;
        boolean sawMutex = false;
        int cycle = 1;
        while (cycle <= 1000 && !done(p1, p2, c1)) {
            tick(cl, cycle++);
            BufferStatus st = buf.snapshot();
            withinBounds &= st.count() >= 0 && st.count() <= st.capacity()
                    && st.produced() - st.consumed() == st.count();
            sawFull |= p1.getBlockReason() == BlockReason.BUFFER_FULL || p2.getBlockReason() == BlockReason.BUFFER_FULL;
            sawEmpty |= c1.getBlockReason() == BlockReason.BUFFER_EMPTY;
            sawMutex |= p1.getBlockReason() == BlockReason.SEMAPHORE_MUTEX
                    || p2.getBlockReason() == BlockReason.SEMAPHORE_MUTEX
                    || c1.getBlockReason() == BlockReason.SEMAPHORE_MUTEX;
        }
        BufferStatus st = buf.snapshot();
        evidence("producidos " + st.produced() + ", consumidos " + st.consumed() + " en " + (cycle - 1)
                + " ciclos; bloqueos vistos: lleno=" + sawFull + ", vacío=" + sawEmpty + ", mutex=" + sawMutex);
        check("todos terminan sin vencer deadline", done(p1, p2, c1)
                && !p1.isDeadlineMissed() && !p2.isDeadlineMissed() && !c1.isDeadlineMissed());
        check("en todo momento 0 <= elementos <= capacidad y producidos - consumidos = elementos", withinBounds);
        check("10 producidos = 10 consumidos", st.produced() == 10 && st.consumed() == 10);
        check("se ejercitaron los 3 semáforos (lleno, vacío y mutex)", sawFull && sawEmpty && sawMutex);
        check("semáforos íntegros al final (s=1, n=0, e=2)", st.valueS() == 1 && st.valueN() == 0 && st.valueE() == 2);
    }

    // ===================================================================== T09
    /**
     * Consumidor en PC1 con buffer en PC0, latencia 3. Cada operación sobre el
     * buffer (5 por elemento) debe pagar la latencia exactamente una vez.
     */
    private static void t09RemoteBuffer() {
        final int latency = 3;
        final int items = 3;
        Cluster cl = cluster(SchedulingPolicy.FCFS, 2, latency);
        BoundedBuffer buf = cl.createBuffer("B", 1, 0);
        ProcessControlBlock prod = ProcessControlBlock.createProducer("ProdLocal", 0, 1, 10, 3000, buf, items, 1);
        ProcessControlBlock cons = ProcessControlBlock.createConsumer("ConsRemoto", 1, 1, 10, 3000, buf, items, 1);
        cl.submit(prod);
        cl.submit(cons);

        int latencyBlocks = 0;
        int prodLatencyBlocks = 0;
        BlockReason lastCons = BlockReason.NONE;
        tick(cl, 1);
        boolean firstRequestOk = cons.getBlockReason() == BlockReason.NETWORK_LATENCY && cons.getProgramCounter() == 0;
        if (firstRequestOk) {
            latencyBlocks++;
            lastCons = BlockReason.NETWORK_LATENCY;
        }
        int cycle = 2;
        while (cycle <= 2000 && !done(prod, cons)) {
            tick(cl, cycle);
            if (cycle == 1 + latency) {
                check("el primer acceso remoto se completa exactamente " + latency + " ciclos después",
                        cons.getProgramCounter() == 1);
            }
            cycle++;
            BlockReason now = cons.getBlockReason();
            if (now == BlockReason.NETWORK_LATENCY && lastCons != BlockReason.NETWORK_LATENCY) {
                latencyBlocks++;
            }
            lastCons = now;
            if (prod.getBlockReason() == BlockReason.NETWORK_LATENCY) {
                prodLatencyBlocks++;
            }
        }
        BufferStatus st = buf.snapshot();
        evidence("ConsRemoto pagó latencia " + latencyBlocks + " veces (esperado " + (5 * items)
                + " = 5 operaciones x " + items + " elementos); terminó en el ciclo " + cons.getFinishCycle());
        check("en el ciclo 1 solicita el acceso y se bloquea por NETWORK_LATENCY sin avanzar el PC", firstRequestOk);
        check("cada operación remota paga la latencia una vez (5 por elemento)", latencyBlocks == 5 * items);
        check("el productor local nunca paga latencia", prodLatencyBlocks == 0);
        check("la transferencia remota se completa íntegra", done(prod, cons)
                && st.produced() == items && st.consumed() == items && st.valueS() == 1);

        int fast = remoteFinish(0);
        int slow = remoteFinish(latency);
        evidence("misma carga: " + fast + " ciclos sin latencia vs " + slow + " con latencia " + latency);
        check("la latencia configurada se refleja en el tiempo total", slow > fast);
    }

    // ===================================================================== T10
    /**
     * Dos computadoras con hilos REALES y el reloj global: ambas avanzan el mismo
     * número de ciclos, ninguna se adelanta y trabajan en paralelo (incluso
     * compartiendo un buffer remoto).
     */
    private static void t10TwoComputers() throws InterruptedException {
        Cluster cl = new Cluster(new int[]{256, 256}, SchedulingPolicy.ROUND_ROBIN, 3, 0, 2, EventLogger.SILENT);
        cl.getComputer(1).getKernel().setSchedulingPolicy(SchedulingPolicy.EDF, 3);   // políticas distintas
        BoundedBuffer buf = cl.createBuffer("Canal", 3, 1);
        ProcessControlBlock[] all = {
            cpu("Calc0", 0, 1, 16, 9000, 30),
            cpu("Calc1", 1, 1, 16, 9000, 30),
            ProcessControlBlock.createProducer("Prod0", 0, 1, 16, 9000, buf, 6, 2),
            ProcessControlBlock.createConsumer("Cons1", 1, 1, 16, 9000, buf, 6, 1),
        };
        for (ProcessControlBlock p : all) {
            cl.submit(p);
        }
        cl.start();
        long limit = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < limit && !done(all)) {
            Thread.sleep(10);
        }
        cl.stop();

        int clockCycles = cl.getCurrentCycle();
        Kernel k0 = cl.getComputer(0).getKernel();
        Kernel k1 = cl.getComputer(1).getKernel();
        int total0 = k0.getCpu().getBusyCycles() + k0.getCpu().getIdleCycles();
        int total1 = k1.getCpu().getBusyCycles() + k1.getCpu().getIdleCycles();
        evidence("reloj: " + clockCycles + " ciclos; PC0 ejecutó " + total0 + " ciclos y PC1 " + total1
                + "; Calc0 y Calc1 empezaron en los ciclos " + all[0].getFirstRunCycle() + " y " + all[1].getFirstRunCycle());
        check("todos los procesos de ambas computadoras terminan", done(all));
        check("cada computadora ejecutó exactamente un ciclo por tick del reloj",
                total0 == clockCycles && total1 == clockCycles);
        check("ambos kernels están en el mismo ciclo que el reloj",
                k0.getCurrentCycle() == clockCycles && k1.getCurrentCycle() == clockCycles);
        check("trabajo en paralelo: Calc0 (PC0) y Calc1 (PC1) empiezan ambos en el ciclo 1",
                all[0].getFirstRunCycle() == 1 && all[1].getFirstRunCycle() == 1);
        check("cada computadora conserva su política (RR y EDF)",
                k0.getSchedulingPolicy() == SchedulingPolicy.ROUND_ROBIN && k1.getSchedulingPolicy() == SchedulingPolicy.EDF);
        BufferStatus st = buf.snapshot();
        check("el buffer compartido entre ambas cuadra (6/6)", st.produced() == 6 && st.consumed() == 6 && st.valueS() == 1);

        System.out.println("   Checklist visual T10 (en la GUI, con config/prueba_completa.json):");
        System.out.println("   [ ] el número de ciclo avanza y ambas computadoras muestran actividad");
        System.out.println("   [ ] las colas cambian en cada ciclo y los bloqueados muestran su motivo");
        System.out.println("   [ ] la tabla de buffers muestra s/n/e y quién espera; el log registra cada evento");
        System.out.println("   [ ] la gráfica dibuja una línea por computadora");
    }

    // ===================================================================== auxiliares

    private interface Scenario {
        void run() throws InterruptedException;
    }

    private static void scenario(String id, String title, Scenario body) throws InterruptedException {
        System.out.println();
        System.out.println(id + " — " + title);
        int before = failed;
        body.run();
        if (failed > before) {
            scenariosFailed++;
        }
        System.out.println("   => " + id + (failed > before ? " FALLA" : " APROBADO"));
    }

    /** Un tick manual: cada computador ejecuta su ciclo (lo que hace el reloj, sin hilos). */
    private static void tick(Cluster cl, int cycle) {
        for (int i = 0; i < cl.getComputerCount(); i++) {
            cl.getComputer(i).onTick(cycle);
        }
    }

    private static void runUntilDone(Cluster cl, int maxCycles, ProcessControlBlock... ps) {
        int start = cl.getComputer(0).getKernel().getCurrentCycle() + 1;
        for (int c = start; c <= start + maxCycles && !done(ps); c++) {
            tick(cl, c);
        }
    }

    /** Ráfagas consecutivas de CPU del computador 0 entre dos ciclos, p. ej. "A2 B2 A2". */
    private static String runAndRecordBursts(Cluster cl, int from, int to) {
        String result = "";
        String current = null;
        int length = 0;
        for (int c = from; c <= to; c++) {
            tick(cl, c);
            ProcessControlBlock r = cl.getComputer(0).getKernel().getCpu().getRunning();
            String name = (r == null) ? "-" : r.getName();
            if (name.equals(current)) {
                length++;
            } else {
                if (current != null) {
                    result += (result.isEmpty() ? "" : " ") + current + length;
                }
                current = name;
                length = 1;
            }
        }
        return result + (result.isEmpty() ? "" : " ") + current + length;
    }

    private static int remoteFinish(int latency) {
        Cluster cl = cluster(SchedulingPolicy.FCFS, 2, latency);
        BoundedBuffer buf = cl.createBuffer("B", 1, 0);
        ProcessControlBlock prod = ProcessControlBlock.createProducer("P", 0, 1, 10, 5000, buf, 3, 1);
        ProcessControlBlock cons = ProcessControlBlock.createConsumer("C", 1, 1, 10, 5000, buf, 3, 1);
        cl.submit(prod);
        cl.submit(cons);
        runUntilDone(cl, 2000, prod, cons);
        return Math.max(prod.getFinishCycle(), cons.getFinishCycle());
    }

    private static Cluster cluster(SchedulingPolicy policy, int quantum, int latency) {
        return new Cluster(new int[]{128, 128}, policy, quantum, 0, latency, EventLogger.SILENT);
    }

    private static ProcessControlBlock cpu(String name, int computer, int priority, int memory,
            int deadline, int instructions) {
        return new ProcessControlBlock(name, computer, ProcessType.CPU_BOUND, priority, memory, deadline, instructions);
    }

    private static int ram(Cluster cl, int computer) {
        return cl.getComputer(computer).getKernel().getRam().getUsed();
    }

    private static boolean done(ProcessControlBlock... ps) {
        for (ProcessControlBlock p : ps) {
            if (p.getState() != ProcessState.TERMINATED) {
                return false;
            }
        }
        return true;
    }

    private static boolean throwsIAE(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static void evidence(String text) {
        System.out.println("   · " + text);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("   [OK]    " + name);
        } else {
            failed++;
            System.out.println("   [FALLA] " + name);
        }
    }
}

