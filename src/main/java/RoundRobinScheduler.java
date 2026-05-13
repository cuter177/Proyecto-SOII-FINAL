import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

/**
 * Planificación Round Robin con quantum de tiempo en milisegundos.
 * Cada PDF recibe un máximo de QUANTUM_MS para procesarse.
 * Si el procesamiento excede el quantum, el hilo es interrumpido,
 * el PDF se reencola y se retoma en la siguiente vuelta.
 *
 * En la práctica, los PDFs de horarios BUAP son pequeños y homogéneos,
 * por lo que es probable que ninguno supere el quantum — lo que demuestra
 * empíricamente que RR converge a FCFS cuando los burst times son uniformes.
 */
public class RoundRobinScheduler implements AlgoritmoScheduler {

    private static final long QUANTUM_MS = 150;

    @Override
    public String getNombre() {
        return "RR - Round Robin (quantum=" + QUANTUM_MS + "ms)";
    }

    /**
     * Round Robin no reordena la cola inicial —
     * el reordenamiento ocurre dinámicamente durante ejecutar().
     */
    @Override
    public List<Path> ordenar(List<Path> pdfs) {
        System.out.println("[RR] Cola inicial en orden de llegada. "
            + "Quantum: " + QUANTUM_MS + "ms");
        return new ArrayList<>(pdfs);
    }

    /**
     * Ejecuta Round Robin con quantum de tiempo.
     * Cada PDF se somete en un Future con timeout = QUANTUM_MS.
     * Si termina dentro del quantum: se registra y se avanza.
     * Si excede el quantum: se cancela, se reencola y se reintenta en la siguiente vuelta.
     */
    @Override
    public List<MetricaPDF> ejecutar(List<Path> pdfs) throws Exception {

        // Cola circular de PDFs pendientes
        Queue<Path> cola = new LinkedList<>(pdfs);

        // Métricas indexadas por nombre de archivo
        List<MetricaPDF> metricas = new ArrayList<>();
        long tiempoInicioCola = System.currentTimeMillis();

        // Una métrica por PDF, creada al entrar por primera vez
        java.util.Map<String, MetricaPDF> mapaMetricas = new java.util.LinkedHashMap<>();
        for (Path pdf : pdfs) {
            mapaMetricas.put(
                pdf.getFileName().toString(),
                new MetricaPDF(pdf.getFileName().toString(), tiempoInicioCola)
            );
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        int vuelta = 1;

        while (!cola.isEmpty()) {

            System.out.println("[RR] --- Vuelta " + vuelta + " ---");
            int tamañoCola = cola.size();

            for (int i = 0; i < tamañoCola; i++) {

                Path pdf = cola.poll();
                if (pdf == null) continue;

                String nombre = pdf.getFileName().toString();
                MetricaPDF metrica = mapaMetricas.get(nombre);

                // Registrar inicio solo la primera vez que se procesa
                if (metrica.getInicio() == 0) {
                    metrica.registrarInicio();
                }

                System.out.println("[RR] Procesando (quantum " + QUANTUM_MS + "ms): " + nombre);

                Future<?> future = executor.submit(() -> {
                    try {
                        ProcesadorPDFTask tarea = new ProcesadorPDFTask(pdf);
                        tarea.call();
                    } catch (Exception e) {
                        System.err.println("[RR] Error en: " + nombre + " -> " + e.getMessage());
                    }
                });

                try {
                    future.get(QUANTUM_MS, TimeUnit.MILLISECONDS);
                    // Terminó dentro del quantum
                    metrica.registrarFin();
                    System.out.println("[RR] Completado dentro del quantum: " + nombre
                        + " | burst: " + metrica.getBurstReal() + "ms");

                } catch (TimeoutException e) {
                    // Excedió el quantum — cancelar y reencolar
                    future.cancel(true);
                    cola.offer(pdf);
                    System.out.println("[RR] Quantum agotado, reencolando: " + nombre);
                }
            }

            vuelta++;

            // Protección contra bucle infinito si todos los PDFs fallan
            if (vuelta > 100) {
                System.err.println("[RR] Límite de vueltas alcanzado. Abortando.");
                break;
            }
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Recolectar métricas en orden de llegada original
        for (Path pdf : pdfs) {
            metricas.add(mapaMetricas.get(pdf.getFileName().toString()));
        }

        return metricas;
    }
}
