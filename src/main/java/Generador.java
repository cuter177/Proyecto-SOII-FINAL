import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.BufferedWriter;
import java.nio.file.*;
import java.time.DayOfWeek;
import java.util.*;

public class Generador {

    private static final Path CARPETA_PDF =
            Paths.get("src/main/resources/pdf/");

    private static final Path ARCHIVO_SALIDA =
            Paths.get("horarios.txt");

    public void ejecutar() throws Exception {

        Path rutaSalida = SelectorDestino.pedirRutaAlUsuario("horarios.txt");

        if (rutaSalida == null) {
        System.out.println("Proceso cancelado por el usuario.");
        return;
        }

        List<Alumno> alumnos = cargarAlumnosDesdePDF();

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

