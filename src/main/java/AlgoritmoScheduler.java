import java.nio.file.Path;
import java.util.List;

/**
 * Interfaz que define el contrato para los algoritmos de planificación de CPU.
 * Cada implementación decide el orden y la forma en que se despachan los PDFs.
 */
public interface AlgoritmoScheduler {

    /**
     * Nombre del algoritmo para mostrar en consola y métricas.
     */
    String getNombre();

    /**
     * Recibe la lista de PDFs en orden de llegada (FCFS natural) y
     * devuelve la lista en el orden que el algoritmo determine.
     *
     * @param pdfs Lista de rutas de PDFs en orden de llegada
     * @return Lista reordenada según el criterio del algoritmo
     * @throws Exception si ocurre un error al inspeccionar los archivos
     */
    List<Path> ordenar(List<Path> pdfs) throws Exception;

    /**
     * Ejecuta el procesamiento de los PDFs según el algoritmo e
     * registra las métricas de cada uno.
     *
     * @param pdfs Lista de rutas ya ordenadas por el algoritmo
     * @return Lista de métricas, una por PDF procesado
     * @throws Exception si ocurre un error durante el procesamiento
     */
    List<MetricaPDF> ejecutar(List<Path> pdfs) throws Exception;
}
