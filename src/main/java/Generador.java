import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.DayOfWeek;
import java.util.*;

public class Generador {

    private static final Path CARPETA_PDF =
            Paths.get("src/main/resources/pdf/");

    private static final Path ARCHIVO_SALIDA =
            Paths.get("horarios.txt");

    public void ejecutar() throws Exception {

        System.out.println("El programa iniciará en 3 segundos.");
        System.out.println("Presiona [ENTER] en cualquier momento para cancelar...");

        // Hilo para vigilar el teclado y permitir la interrupción del programa
        Thread hiloInterrupcion = new Thread(() -> {
            try {
                if (System.in.read() != -1) { 
                    System.out.println("\n[!] Ejecución cancelada por el usuario.");
                    System.exit(0);
                }
            } catch (Exception e) {
            }
        });
        
        // Al ser Daemon, este hilo morirá automáticamente cuando el programa termine
        hiloInterrupcion.setDaemon(true); 
        hiloInterrupcion.start();

        // El hilo principal hace la cuenta regresiva
        for (int i = 3; i > 0; i--) {
            System.out.print(i + "... ");
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

        /* Se reemplazo ARCHIVO_SALIDA por rutaSalida */
        try (BufferedWriter writer = Files.newBufferedWriter(
                rutaSalida,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {

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

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(CARPETA_PDF, "*.pdf")) {
            for (Path pdf : stream) {
                System.out.println("Procesando: " + pdf.getFileName());

                try {
                    try (PDDocument doc = PDDocument.load(pdf.toFile())) {
                        PDFHorarioStripper stripper = new PDFHorarioStripper();
                        stripper.getText(doc);
                        alumnos.add(new Alumno(stripper.getNombreAlumno(), stripper.getHorario()));
                    }
                } catch (IOException e) {
                    System.err.println("\n[!] Error fatal:");
                    System.err.println("[!] PDF no valido: " + pdf.getFileName());
                    System.err.println("[!] Detalle: " + e.getMessage());
                    System.err.println("[!] Se cancela toda la ejecucion.\n");
                    throw new RuntimeException(
                            "Proceso cancelado por archivo PDF no valido: " + pdf.getFileName(),
                            e
                    );
                }
            }
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

