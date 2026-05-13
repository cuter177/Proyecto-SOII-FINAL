/**
 * Almacena las métricas de planificación de CPU para un PDF individual.
 *
 * Términos:
 *   - llegada:    momento en que el PDF entró a la cola (ms desde inicio)
 *   - inicio:     momento en que el hilo comenzó a procesarlo por primera vez
 *   - fin:        momento en que el procesamiento terminó completamente
 *   - espera:     tiempo que el PDF estuvo en cola sin ser procesado (inicio - llegada)
 *   - turnaround: tiempo total desde llegada hasta finalización (fin - llegada)
 */
public class MetricaPDF {

    private final String nombreArchivo;
    private final long llegada;   // ms absolutos desde System.currentTimeMillis()
    private long inicio;
    private long fin;

    public MetricaPDF(String nombreArchivo, long llegada) {
        this.nombreArchivo = nombreArchivo;
        this.llegada = llegada;
    }

    // =======================
    // Setters de eventos
    // =======================

    public void registrarInicio() {
        this.inicio = System.currentTimeMillis();
    }

    public void registrarFin() {
        this.fin = System.currentTimeMillis();
    }

    // =======================
    // Métricas calculadas
    // =======================

    /** Tiempo de espera en cola antes del primer procesamiento (ms) */
    public long getEspera() {
        return inicio - llegada;
    }

    /** Tiempo total desde llegada hasta finalización (ms) */
    public long getTurnaround() {
        return fin - llegada;
    }

    /** Tiempo real de procesamiento en CPU (ms) */
    public long getBurstReal() {
        return fin - inicio;
    }

    // =======================
    // Getters
    // =======================

    public String getNombreArchivo() { return nombreArchivo; }
    public long getLlegada()         { return llegada; }
    public long getInicio()          { return inicio; }
    public long getFin()             { return fin; }

    // =======================
    // Representación
    // =======================

    @Override
    public String toString() {
        return String.format(
            "  %-25s | llegada: %4dms | inicio: %4dms | fin: %4dms | espera: %4dms | turnaround: %4dms",
            nombreArchivo,
            0L,                  // llegada relativa siempre 0 (todos llegan al inicio)
            inicio - llegada,    // inicio relativo
            fin - llegada,       // fin relativo
            getEspera(),
            getTurnaround()
        );
    }
}
