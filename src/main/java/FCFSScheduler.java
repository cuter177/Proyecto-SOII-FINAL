import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Planificación FCFS (First Come, First Served).
 * Los PDFs se procesan en el orden exacto en que fueron entregados
 * por el DirectoryStream del sistema operativo, sin reordenamiento.
 */
public class FCFSScheduler implements AlgoritmoScheduler {

    @Override
    public String getNombre() {
        return "FCFS - First Come, First Served";
    }

    /**
     * No reordena — devuelve la lista tal como llegó.
     */
    @Override
    public List<Path> ordenar(List<Path> pdfs) {
        System.out.println("[FCFS] Orden de llegada conservado. Sin reordenamiento.");
        return new ArrayList<>(pdfs);
    }

    /**
     * Procesa cada PDF en orden secuencial sin interrupciones.
     * Registra inicio y fin de cada uno para calcular métricas.
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

            System.out.println("[FCFS] Procesando: " + pdf.getFileName());
            metrica.registrarInicio();

            // Procesamiento real del PDF
            try {
                ProcesadorPDFTask tarea = new ProcesadorPDFTask(pdf);
                tarea.call(); // resultado ignorado aquí; las métricas son el objetivo
            } catch (Exception e) {
                System.err.println("[FCFS] Error procesando " + pdf.getFileName() + ": " + e.getMessage());
            }

            metrica.registrarFin();
            metricas.add(metrica);

            System.out.println("[FCFS] Finalizado: " + pdf.getFileName()
                + " | burst: " + metrica.getBurstReal() + "ms");
        }

        return metricas;
    }
}
