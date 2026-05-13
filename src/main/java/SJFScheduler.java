import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Planificación SJF (Shortest Job First), versión no expropiativa.
 * Antes de procesar, ordena los PDFs por tamaño en bytes (ascendente).
 * El tamaño del archivo se usa como estimación del burst time de CPU:
 * un PDF más pequeño implica menos contenido y menor tiempo de extracción.
 */
public class SJFScheduler implements AlgoritmoScheduler {

    @Override
    public String getNombre() {
        return "SJF - Shortest Job First";
    }

    /**
     * Reordena los PDFs de menor a mayor tamaño en bytes.
     * Si dos archivos tienen el mismo tamaño, se mantiene el orden de llegada (FCFS como desempate).
     */
    @Override
    public List<Path> ordenar(List<Path> pdfs) throws Exception {

        System.out.println("[SJF] Inspeccionando tamaños para determinar orden...");

        List<Path> ordenados = new ArrayList<>(pdfs);

        ordenados.sort(Comparator.comparingLong(pdf -> {
            try {
                long size = Files.size(pdf);
                System.out.println("[SJF]   " + pdf.getFileName() + " -> " + size + " bytes");
                return size;
            } catch (Exception e) {
                System.err.println("[SJF] No se pudo leer tamaño de: " + pdf.getFileName());
                return Long.MAX_VALUE; // va al final si no se puede leer
            }
        }));

        System.out.println("[SJF] Orden resultante:");
        for (int i = 0; i < ordenados.size(); i++) {
            System.out.println("[SJF]   " + (i + 1) + ". " + ordenados.get(i).getFileName());
        }

        return ordenados;
    }

    /**
     * Procesa los PDFs en el orden ya establecido por ordenar().
     * Al igual que FCFS, cada proceso ocupa la CPU sin interrupciones
     * hasta terminar (no expropiativo).
     */
    @Override
    public List<MetricaPDF> ejecutar(List<Path> pdfs) throws Exception {

        List<MetricaPDF> metricas = new ArrayList<>();
        long tiempoInicioCola = System.currentTimeMillis();

        for (Path pdf : pdfs) {

            MetricaPDF metrica = new MetricaPDF(
                pdf.getFileName().toString(),
                tiempoInicioCola
            );

            System.out.println("[SJF] Procesando: " + pdf.getFileName());
            metrica.registrarInicio();

            try {
                ProcesadorPDFTask tarea = new ProcesadorPDFTask(pdf);
                tarea.call();
            } catch (Exception e) {
                System.err.println("[SJF] Error procesando " + pdf.getFileName() + ": " + e.getMessage());
            }

            metrica.registrarFin();
            metricas.add(metrica);

            System.out.println("[SJF] Finalizado: " + pdf.getFileName()
                + " | burst: " + metrica.getBurstReal() + "ms");
        }

        return metricas;
    }
}
