import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.*;
import java.nio.file.*;
import java.time.DayOfWeek;
import java.util.*;

public class Generador {

    private static final Path CARPETA_PDF = Paths.get("src/main/resources/pdf/");

    // Línea del archivo .txt en la que se escribirán datos falsos (1-indexed)
    private static final int LINEA_CORRUPCION = 5;

    private int contadorLineas = 0;

    private volatile boolean internetCaido = false;
    private volatile boolean cancelado = false;

    public void ejecutar() throws Exception {

        System.out.println("El programa iniciará en 3 segundos.");
        System.out.println("ENTER para interrumpir el proceso");

        // Hilo para detectar teclas
        Thread hiloTeclado = new Thread(() -> {
            try {
                while (true) {
                    int tecla = System.in.read();

                    if (tecla == '\n') { // ENTER
                        cancelado = true;
                        System.out.println("\n[!] Ejecución cancelada.");
                        System.exit(0);
                    }

                    if (tecla == 'v' || tecla == 'V') {
                        internetCaido = true;
                        System.out.println("\n[!] Internet caído...");
                        break;
                    }
                }
            } catch (Exception e) {
            }
        });

        hiloTeclado.setDaemon(true);
        hiloTeclado.start();

        // Cuenta regresiva
        for (int i = 3; i > 0; i--) {
            if (cancelado)
                return;
            System.out.print(i + "... ");
            Thread.sleep(1000);
        }

        System.out.println("\n¡Iniciando procesamiento!\n");

        Path rutaSalida = SelectorDestino.pedirRutaAlUsuario("horarios.txt");

        if (rutaSalida == null) {
            System.out.println("Proceso cancelado.");
            return;
        }

        try (OutputStream out = Files.newOutputStream(
                rutaSalida,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            procesarYEscribir(out);

        }
    }

    // =======================
    // PROCESO EN TIEMPO REAL
    // =======================
    private void procesarYEscribir(OutputStream out) throws Exception {

        if (!Files.exists(CARPETA_PDF)) {
            throw new RuntimeException("No existe la carpeta de PDFs");
        }

        List<Alumno> alumnosProcesados = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(CARPETA_PDF, "*.pdf")) {

            for (Path pdf : stream) {

                if (internetCaido) {
                    escribirLinea(out, "\nHORARIOS LIBRES COMUNES");
                    escribirLinea(out, "  [DATOS INCOMPLETOS]");
                    escribirCorrupcionTotal(out);
                    return;
                }

                System.out.println("Procesando: " + pdf.getFileName());

                try (PDDocument doc = PDDocument.load(pdf.toFile())) {

                    PDFHorarioStripper stripper = new PDFHorarioStripper();
                    stripper.getText(doc);

                    Alumno alumno = new Alumno(
                            stripper.getNombreAlumno(),
                            stripper.getHorario());

                    alumnosProcesados.add(alumno);

                    escribirAlumno(alumno, out);

                } catch (Exception e) {
                    System.err.println("[!] Error en: " + pdf.getFileName());
                }
            }
        }

        // SOLO si terminó correctamente
        escribirLinea(out, "\nHORARIOS LIBRES COMUNES");

        Map<DayOfWeek, List<Intervalo>> libres = HorarioUtils.horariosLibres(alumnosProcesados);

        for (DayOfWeek dia : diasSemana()) {

            escribirLinea(out, "  " + diaEnEspanol(dia) + ":");

            List<Intervalo> intervalos = libres.get(dia);

            if (intervalos == null || intervalos.isEmpty()) {
                escribirLinea(out, "    N/H");
                continue;
            }

            for (Intervalo i : intervalos) {
                escribirLinea(out, "    " + i);
            }
        }
    }

    // =======================
    // ESCRIBIR ALUMNO
    // =======================
    private void escribirAlumno(Alumno a, OutputStream out) throws Exception {

        escribirLinea(out, "[" + a.getNombreCompleto() + "]");

        for (DayOfWeek dia : diasSemana()) {

            if (internetCaido) {
                escribirCorrupcionTotal(out);
                return;
            }

            escribirLinea(out, "  " + diaEnEspanol(dia) + ":");

            var clases = a.getHorario().get(dia);

            if (clases == null || clases.isEmpty()) {
                escribirLinea(out, "    N/H");
                continue;
            }

            clases.sort(Comparator.comparing(
                    c -> c.getIntervalo().inicio));

            for (ClaseHorario c : clases) {

                if (internetCaido) {
                    escribirCorrupcionParcial(out);
                    escribirCorrupcionTotal(out);
                    return;
                }

                escribirLinea(out,
                        "    " +
                                c.getIntervalo().inicio + "-" +
                                c.getIntervalo().fin + "  " +
                                c.getNombreMateria());

                Thread.sleep(200);
            }
        }
    }

    // =======================
    // ESCRITURA
    // =======================
    private void escribirLinea(OutputStream out, String texto) throws Exception {
        contadorLineas++;
        // Si esta es la línea marcada para corrupción, sustituir por datos falsos
        if (contadorLineas == LINEA_CORRUPCION) {
            texto = "    " + horaFalsa() + "-" + horaFalsa() + "  " + materiaFalsa();
        }
        out.write((texto + "\n").getBytes());
    }

    // =======================
    // CORRUPCIÓN
    // =======================
    private void escribirCorrupcionParcial(OutputStream out) throws Exception {

        // Corrupción parcial

        byte[] basura = new byte[20];
        new Random().nextBytes(basura);

        out.write(basura);
    }

    private void escribirCorrupcionTotal(OutputStream out) throws Exception {

        // Escribiendo resto corrupto...

        byte[] basura = new byte[1000];
        new Random().nextBytes(basura);

        out.write(basura);

        out.flush();
    }

    // =======================
    // DATOS FALSOS
    // =======================
    private static final String[] MATERIAS_FALSAS = {
        "Somnolencia Fisica", "Filosofia Medieval", "Cocina Molecular",
        "Ingenieria en Tiktok", "Folclor milagroso", "Economia de Marte",
        "Latin Lover", "Tanatologia", "Sociologia Perruna",
        "Botanica Aerea", "Derecho Galactico", "Etica no tan Etica",
        "Musica de TikTok", "Arquitectura de Anime", "Paleografia (De paletas)"
    };

    private String horaFalsa() {
        int hora = new Random().nextInt(30) + 20; // e.g. 20 a 49
        int minuto = new Random().nextBoolean() ? 0 : 30;
        return String.format("%d:%02d", hora, minuto);
    }

    private String materiaFalsa() {
        return MATERIAS_FALSAS[new Random().nextInt(MATERIAS_FALSAS.length)];
    }

    // =======================
    // UTILIDADES
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
            case MONDAY -> "Lunes";
            case TUESDAY -> "Martes";
            case WEDNESDAY -> "Miércoles";
            case THURSDAY -> "Jueves";
            case FRIDAY -> "Viernes";
            default -> "";
        };
    }
}
