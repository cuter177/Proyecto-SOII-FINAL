import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.BufferedWriter;
import java.io.IOException;
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

  public void ejecutar() throws Exception {
    System.out.println("El programa iniciará en 3 segundos.");
    System.out.println("Presiona [ENTER] en cualquier momento para cancelar...");

    Thread hiloInterrupcion = new Thread(() -> {
      try {
        // System.in.read() ignora interrupciones; este canal sí las respeta
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
      System.out.println("Proceso cancelado por el usuario.");
      return;
    }

    List<Alumno> alumnos;
    try {
      alumnos = cargarAlumnosDesdePDF();
    } catch (RuntimeException e) {
      System.err.println("[!] Proceso terminado.");
      return;
    }

    try (BufferedWriter writer = Files.newBufferedWriter(
          rutaSalida,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING)) {
      escribirHorariosAlumnos(alumnos, writer);
      writer.newLine();
      escribirHorariosLibres(alumnos, writer);
          }
  }
  // =======================
  // Cargar alumnos
  // =======================

  /*
     private List<Alumno> cargarAlumnosDesdePDF() throws Exception {

     if (!Files.exists(CARPETA_PDF)) {
     throw new RuntimeException("No existe la carpeta de PDFs");
     }

     List<Alumno> alumnos = new ArrayList<>();

     try (DirectoryStream<Path> stream =
     Files.newDirectoryStream(CARPETA_PDF, "*.pdf")) {

     for (Path pdf : stream) {

     try (PDDocument doc = PDDocument.load(pdf.toFile())) {

     PDFHorarioStripper stripper = new PDFHorarioStripper();
     stripper.getText(doc);

     alumnos.add(new Alumno(
     stripper.getNombreAlumno(),
     stripper.getHorario()
     ));
     }
     }
     }

     return alumnos;
     }
     */
  private List<Alumno> cargarAlumnosDesdePDF() throws Exception {

    if (!Files.exists(CARPETA_PDF)) {
      throw new RuntimeException("No existe la carpeta de PDFs");
    }

    List<Alumno> alumnos = new ArrayList<>();

    // Pool de hilos
    ExecutorService pool =
      Executors.newFixedThreadPool(4);

    // Lista de tareas
    List<Future<Alumno>> futures =
      new ArrayList<>();

    try (DirectoryStream<Path> stream =
        Files.newDirectoryStream(CARPETA_PDF, "*.pdf")) {

      for (Path pdf : stream) {

        System.out.println(
            "[MAIN] Enviando tarea: "
            + pdf.getFileName());

        ProcesadorPDFTask tarea =
          new ProcesadorPDFTask(pdf);

        futures.add(pool.submit(tarea));
      }
        }

    // Esperar resultados
    for (Future<Alumno> future : futures) {

      try {

        Alumno alumno = future.get();

        if (alumno != null) {
          alumnos.add(alumno);
        }

      } catch (Exception e) {

        System.err.println(
            "[!] Error procesando PDF: "
            + e.getMessage());
      }
    }

    pool.shutdown();

    try {
      if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
        pool.shutdownNow();
      }
    } catch (InterruptedException e) {
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
      BufferedWriter writer
      ) throws Exception {

    for (Alumno a : alumnos) {
      escribirAlumno(a, writer);
      writer.newLine();
    }
      }

  private void escribirAlumno(Alumno a, BufferedWriter writer)
      throws Exception {

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

        clases.sort(Comparator.comparing(
              c -> c.getIntervalo().inicio
              ));

        for (ClaseHorario c : clases) {
          writer.write("    " +
              c.getIntervalo().inicio + "-" +
              c.getIntervalo().fin + "  " +
              c.getNombreMateria()
              );
          writer.newLine();
        }
      }
  }

  // =======================
  // Horarios libres
  // =======================
  private void escribirHorariosLibres(
      List<Alumno> alumnos,
      BufferedWriter writer
      ) throws Exception {

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
        DayOfWeek.FRIDAY
        );
  }

  private String diaEnEspanol(DayOfWeek d) {
    return switch (d) {
      case MONDAY -> "Lunes";
      case TUESDAY -> "Martes";
      case WEDNESDAY -> "Miércoles";
      case THURSDAY -> "Jueves";
      case FRIDAY -> "Viernes";
        default -> "";
    };
  }
}

