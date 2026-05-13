import org.apache.pdfbox.pdmodel.PDDocument;

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

        try (PDDocument doc = PDDocument.load(pdf.toFile())) {

            PDFHorarioStripper stripper =
                    new PDFHorarioStripper();

            stripper.getText(doc);

            return new Alumno(
                    stripper.getNombreAlumno(),
                    stripper.getHorario()
            );
        }
    }
}
