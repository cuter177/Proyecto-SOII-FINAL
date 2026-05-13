import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Console;

import java.nio.file.*;
import java.time.DayOfWeek;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ReadableByteChannel;


public class Generador {

  private static final Path CARPETA_PDF =
    Paths.get("src/main/resources/pdf/");

  private final Lock lockArchivo =
    new ReentrantLock();

  private static final Path ARCHIVO_SALIDA =
    Paths.get("horarios.txt");

  private static final String USUARIO_CORRECTO = "admin";
  private static final String PASSWORD_CORRECTA = "horarios2026";

  public void ejecutar() throws Exception {
    if (!autenticar()) return;

    // ─── Menú de algoritmo de planificación ──────────────────────────────────
    AlgoritmoScheduler scheduler = elegirAlgoritmo();
    if (scheduler == null) {
      System.out.println("[!] Algoritmo no válido. Proceso cancelado.");
      return;
    }
    System.out.println("[DEBUG] Algoritmo seleccionado: " + scheduler.getNombre());
    // ─────────────────────────────────────────────────────────────────────────

    System.out.println("El programa iniciará en 3 segundos.");
    System.out.println("Presiona [ENTER] en cualquier momento para cancelar...");

    Thread hiloInterrupcion = new Thread(() -> {
      try {
        ReadableByteChannel canal = Channels.newChannel(System.in);
        ByteBuffer buffer = ByteBuffer.allocate(1);
        if (canal.read(buffer) != -1) {
          System.out.println("\n[!] Ejecución cancelada por el usuario.");
          System.exit(0);
        }
      } catch (ClosedByInterruptException e) {
        // Maven interrumpió el hilo limpiamente — comportamiento esperado
      } catch (Exception e) {
        // Ignorado intencionalmente
      }
    });
    hiloInterrupcion.setDaemon(true);
    hiloInterrupcion.start();

    for (int i = 3; i > 0; i--) {
      System.out.print(i + "... ");
      System.out.flush();
      Thread.sleep(1000);
    }
    System.out.println("\n¡Iniciando procesamiento!\n");

    Path rutaSalida = SelectorDestino.pedirRutaAlUsuario("horarios.txt");
    if (rutaSalida == null) {
      System.out.println("[DEBUG] rutaSalida es null — proceso cancelado por el usuario.");
      return;
    }

    // ─── Validación: permisos de escritura en el destino ─────────────────────
    System.out.println("[DEBUG] Verificando permisos de escritura en: " + rutaSalida);
    Path destino = Files.exists(rutaSalida) ? rutaSalida : rutaSalida.getParent();
    if (destino != null && !Files.isWritable(destino)) {
      System.err.println("[!] Sin permisos de escritura en el destino: " + destino);
      return;
    }
    System.out.println("[DEBUG] Permisos de escritura OK.");
    // ─────────────────────────────────────────────────────────────────────────

    // ─── Cargar lista de PDFs y ejecutar con el scheduler elegido ────────────
    List<Path> pdfs = listarPDFs();
    if (pdfs.isEmpty()) {
      System.err.println("[!] No se encontraron archivos PDF. Proceso terminado.");
      return;
    }

    List<Path> pdfsOrdenados = scheduler.ordenar(pdfs);
    List<MetricaPDF> metricas = scheduler.ejecutar(pdfsOrdenados);
    imprimirTablaMetricas(scheduler.getNombre(), metricas);
    // ─────────────────────────────────────────────────────────────────────────

    // ─── Cargar alumnos y escribir archivo de salida ──────────────────────────
    List<Alumno> alumnos;
    try {
      alumnos = cargarAlumnosDesdePDF();
    } catch (RuntimeException e) {
      System.err.println("[!] Proceso terminado: " + e.getMessage());
      return;
    }

    System.out.println("[DEBUG] Total de alumnos cargados: " + alumnos.size());

    try (BufferedWriter writer = Files.newBufferedWriter(
          rutaSalida,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING)) {
      escribirHorariosAlumnos(alumnos, writer);
      writer.newLine();
      escribirHorariosLibres(alumnos, writer);
      System.out.println("[DEBUG] Archivo escrito correctamente en: " + rutaSalida);
    }
  }

  // =======================
  // Menú de algoritmo
  // =======================
  private AlgoritmoScheduler elegirAlgoritmo() {
    System.out.println("\n╔══════════════════════════════════════════╗");
    System.out.println("║   Algoritmo de planificación de CPU      ║");
    System.out.println("╠══════════════════════════════════════════╣");
    System.out.println("║  [1] FCFS  - First Come, First Served    ║");
    System.out.println("║  [2] SJF   - Shortest Job First          ║");
    System.out.println("║  [3] RR    - Round Robin (quantum=150ms) ║");
    System.out.println("╚══════════════════════════════════════════╝");
    System.out.print("Selecciona una opción [1-3]: ");

    Scanner scanner = new Scanner(System.in);
    String opcion = scanner.nextLine().trim();

    return switch (opcion) {
      case "1" -> new FCFSScheduler();
      case "2" -> new SJFScheduler();
      case "3" -> new RoundRobinScheduler();
      default  -> null;
    };
  }

  // =======================
  // Listar PDFs
  // =======================
  private List<Path> listarPDFs() throws Exception {

    // ─── Validación: la carpeta existe ───────────────────────────────────────
    if (!Files.exists(CARPETA_PDF)) {
      System.err.println("[DEBUG] La carpeta no existe: " + CARPETA_PDF.toAbsolutePath());
      throw new RuntimeException("No existe la carpeta de PDFs");
    }

    // ─── Validación: es realmente un directorio ───────────────────────────────
    if (!Files.isDirectory(CARPETA_PDF)) {
      System.err.println("[DEBUG] La ruta existe pero no es un directorio: " + CARPETA_PDF.toAbsolutePath());
      throw new RuntimeException("La ruta de PDFs no es un directorio válido");
    }

    System.out.println("[DEBUG] Carpeta de PDFs validada: " + CARPETA_PDF.toAbsolutePath());

    List<Path> pdfs = new ArrayList<>();
    try (DirectoryStream<Path> stream =
        Files.newDirectoryStream(CARPETA_PDF, "*.pdf")) {
      for (Path pdf : stream) {
        pdfs.add(pdf);
        System.out.println("[DEBUG] PDF encontrado: " + pdf.getFileName());
      }
    }
    return pdfs;
  }

  // =======================
  // Tabla de métricas
  // =======================
  private void imprimirTablaMetricas(String algoritmo, List<MetricaPDF> metricas) {
    System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════════════╗");
    System.out.printf( "║  Métricas de planificación — %-54s ║%n", algoritmo);
    System.out.println("╠═══════════════════════════╦═══════════╦═══════════╦═══════════╦════════════╦═════════╣");
    System.out.println("║ Archivo                   ║ Llegada   ║ Inicio    ║ Fin       ║ Espera     ║ Turnar. ║");
    System.out.println("╠═══════════════════════════╬═══════════╬═══════════╬═══════════╬════════════╬═════════╣");

    long base = metricas.isEmpty() ? 0 : metricas.get(0).getLlegada();

    for (MetricaPDF m : metricas) {
      System.out.printf("║ %-25s ║ %6dms  ║ %6dms  ║ %6dms  ║ %7dms  ║ %4dms  ║%n",
        m.getNombreArchivo().length() > 25
          ? m.getNombreArchivo().substring(0, 22) + "..."
          : m.getNombreArchivo(),
        m.getLlegada()  - base,
        m.getInicio()   - base,
        m.getFin()      - base,
        m.getEspera(),
        m.getTurnaround()
      );
    }

    System.out.println("╠═══════════════════════════╩═══════════╩═══════════╩═══════════╩════════════╩═════════╣");

    long esperaprom    = (long) metricas.stream().mapToLong(MetricaPDF::getEspera).average().orElse(0);
    long turnaroundprom = (long) metricas.stream().mapToLong(MetricaPDF::getTurnaround).average().orElse(0);

    System.out.printf( "║  Promedio — Espera: %dms   Turnaround: %dms%n", esperaprom, turnaroundprom);
    System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════╝\n");
  }

  // =======================
  // Cargar alumnos
  // =======================
  private List<Alumno> cargarAlumnosDesdePDF() throws Exception {

    if (!Files.exists(CARPETA_PDF)) {
      throw new RuntimeException("No existe la carpeta de PDFs");
    }
    if (!Files.isDirectory(CARPETA_PDF)) {
      throw new RuntimeException("La ruta de PDFs no es un directorio válido");
    }

    List<Alumno> alumnos = new ArrayList<>();

    ExecutorService pool = Executors.newFixedThreadPool(4);
    List<Future<Alumno>> futures = new ArrayList<>();

    try (DirectoryStream<Path> stream =
        Files.newDirectoryStream(CARPETA_PDF, "*.pdf")) {
      for (Path pdf : stream) {
        System.out.println("[MAIN] Enviando tarea: " + pdf.getFileName());
        futures.add(pool.submit(new ProcesadorPDFTask(pdf)));
      }
    }

    if (futures.isEmpty()) {
      System.out.println("[DEBUG] No se encontraron archivos PDF en: " + CARPETA_PDF.toAbsolutePath());
    }

    for (Future<Alumno> future : futures) {
      try {
        Alumno alumno = future.get();
        if (alumno != null) {
          alumnos.add(alumno);
          System.out.println("[DEBUG] Alumno añadido: " + alumno.getNombreCompleto());
        } else {
          System.out.println("[DEBUG] Una tarea devolvió null — PDF ignorado.");
        }
      } catch (Exception e) {
        System.err.println("[!] Error procesando PDF: " + e.getMessage());
      }
    }

    pool.shutdown();
    try {
      if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
        System.err.println("[DEBUG] El pool no terminó en 10 segundos — forzando cierre.");
        pool.shutdownNow();
      } else {
        System.out.println("[DEBUG] Pool de hilos cerrado correctamente.");
      }
    } catch (InterruptedException e) {
      System.err.println("[DEBUG] Interrupción al esperar el pool — forzando cierre.");
      pool.shutdownNow();
      Thread.currentThread().interrupt();
    }

    return alumnos;
  }

  // =======================
  // Escribir alumnos
  // =======================
  private void escribirHorariosAlumnos(
      List<Alumno> alumnos,
      BufferedWriter writer) throws Exception {
    for (Alumno a : alumnos) {
      escribirAlumno(a, writer);
      writer.newLine();
    }
  }

  private void escribirAlumno(Alumno a, BufferedWriter writer) throws Exception {
    writer.write("[" + a.getNombreCompleto() + "]");
    writer.newLine();

    for (DayOfWeek dia : diasSemana()) {
      writer.write("  " + diaEnEspanol(dia) + ":");
      writer.newLine();

      var clases = a.getHorario().get(dia);

      if (clases == null || clases.isEmpty()) {
        writer.write("    N/H");
        writer.newLine();
        continue;
      }

      clases.sort(Comparator.comparing(c -> c.getIntervalo().inicio));

      for (ClaseHorario c : clases) {
        writer.write("    " +
            c.getIntervalo().inicio + "-" +
            c.getIntervalo().fin + "  " +
            c.getNombreMateria());
        writer.newLine();
      }
    }
  }

  // =======================
  // Horarios libres
  // =======================
  private void escribirHorariosLibres(
      List<Alumno> alumnos,
      BufferedWriter writer) throws Exception {

    writer.write("HORARIOS LIBRES COMUNES");
    writer.newLine();

    Map<DayOfWeek, List<Intervalo>> libres =
      HorarioUtils.horariosLibres(alumnos);

    for (DayOfWeek dia : diasSemana()) {
      writer.write("  " + diaEnEspanol(dia) + ":");
      writer.newLine();

      List<Intervalo> intervalos = libres.get(dia);
      if (intervalos == null || intervalos.isEmpty()) {
        writer.write("    N/H");
        writer.newLine();
        continue;
      }

      for (Intervalo i : intervalos) {
        writer.write("    " + i);
        writer.newLine();
      }
    }
  }

  // =======================
  // Utilidades
  // =======================
  private List<DayOfWeek> diasSemana() {
    return List.of(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY);
  }

  private String diaEnEspanol(DayOfWeek d) {
    return switch (d) {
      case MONDAY    -> "Lunes";
      case TUESDAY   -> "Martes";
      case WEDNESDAY -> "Miércoles";
      case THURSDAY  -> "Jueves";
      case FRIDAY    -> "Viernes";
      default        -> "";
    };
  }

  // =======================
  // Autenticación
  // =======================
  private boolean autenticar() {
    Console consola = System.console();
    if (consola == null) {
      System.err.println("[!] No se puede acceder a la consola para autenticación.");
      return false;
    }

    String usuario = consola.readLine("Usuario: ");
    if (usuario == null || !usuario.equals(USUARIO_CORRECTO)) {
      System.err.println("[!] Usuario incorrecto. Acceso denegado.");
      return false;
    }

    char[] password = consola.readPassword("Contraseña: ");
    if (password == null || !new String(password).equals(PASSWORD_CORRECTA)) {
      System.err.println("[!] Contraseña incorrecta. Acceso denegado.");
      Arrays.fill(password, '0');
      return false;
    }

    Arrays.fill(password, '0');
    System.out.println("[DEBUG] Autenticación exitosa. Bienvenido, " + usuario + ".");
    return true;
  }
}
