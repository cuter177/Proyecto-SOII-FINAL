import org.apache.pdfbox.pdmodel.PDDocument;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public class ProcesadorPDFTask implements Callable<Alumno> {

    private final Path pdf;

    public ProcesadorPDFTask(Path pdf) {
        this.pdf = pdf;
    }

    @Override
    public Alumno call() throws Exception {

        System.out.println(
                "[" + Thread.currentThread().getName() + "] Procesando: "
                        + pdf.getFileName());

        // ─── Validación 3: el archivo no está vacío ──────────────────────────
        long tamano = Files.size(pdf);
        System.out.println("[DEBUG] Tamaño del archivo " + pdf.getFileName() + ": " + tamano + " bytes");

        if (tamano == 0) {
            throw new Exception("El archivo está vacío: " + pdf.getFileName());
        }
        // ─────────────────────────────────────────────────────────────────────

        try (PDDocument doc = PDDocument.load(pdf.toFile())) {

            System.out.println("[DEBUG] PDF cargado correctamente: " + pdf.getFileName()
                    + " (" + doc.getNumberOfPages() + " página(s))");

            PDFHorarioStripper stripper =
                    new PDFHorarioStripper();

            stripper.getText(doc);

            String nombre = stripper.getNombreAlumno();
            System.out.println("[DEBUG] Nombre extraído: " + nombre);

            return new Alumno(
                    nombre,
                    stripper.getHorario()
            );
        }
    }
}
