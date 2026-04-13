import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.time.DayOfWeek;
import java.util.*;

public class PDFHorarioStripper extends PDFTextStripper {

    // =======================
    // Constantes
    // =======================

    private static final Map<String, DayOfWeek> DIAS = Map.of(
            "LUNES", DayOfWeek.MONDAY,
            "MARTES", DayOfWeek.TUESDAY,
            "MIERCOLES", DayOfWeek.WEDNESDAY,
            "MIÉRCOLES", DayOfWeek.WEDNESDAY,
            "JUEVES", DayOfWeek.THURSDAY,
            "VIERNES", DayOfWeek.FRIDAY,
            "SABADO", DayOfWeek.SATURDAY,
            "SÁBADO", DayOfWeek.SATURDAY,
            "DOMINGO", DayOfWeek.SUNDAY
    );

    private static final String REGEX_CODIGO = "[A-Z]{3,4}-\\d{3}";
    private static final String REGEX_CODIGO_PARCIAL = "[A-Z]{3,4}-";
    private static final String REGEX_HORARIO_COMPLETO = "\\d{4}-\\d{4}";
    private static final String REGEX_HORARIO_INICIO = "\\d{4}-";
    private static final String REGEX_HORARIO_FIN = "\\d{4}";

    // =======================
    // Estado
    // =======================

    private final TreeMap<Float, DayOfWeek> columnasDias = new TreeMap<>();
    private final Map<DayOfWeek, List<ClaseHorario>> horario =
            new EnumMap<>(DayOfWeek.class);

    private String nombreAlumno;

    private String codigoMateriaActual;
    private String codigoParcial;

    private StringBuilder nombreMateriaActual;
    private boolean aceptandoNombreMateria;

    private String horarioParcial;

    private ClaseHorario ultimaClaseCreada;

    public PDFHorarioStripper() throws IOException {
        setSortByPosition(true);
        for (DayOfWeek d : DayOfWeek.values()) {
            horario.put(d, new ArrayList<>());
        }
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) {

        if (text == null || positions.isEmpty()) return;

        String limpio = text
                .replace('\u00A0', ' ')
                .trim()
                .toUpperCase();

        if (limpio.isEmpty()) return;

        TextPosition p = positions.get(0);
        float x = p.getXDirAdj();

        // =======================
        // Nombre del alumno
        // =======================
        if (limpio.startsWith("NOMBRE:")) {
            nombreAlumno = limpio.substring("NOMBRE:".length()).trim();
            return;
        }

        // =======================
        // Encabezados de días
        // =======================
        if (DIAS.containsKey(limpio)) {
            registrarColumnaDia(x, DIAS.get(limpio));
            return;
        }

        // =======================
        // Código de materia completo
        // =======================
        if (limpio.matches(REGEX_CODIGO)) {
            codigoMateriaActual = limpio;
            codigoParcial = null;
            nombreMateriaActual = new StringBuilder();
            ultimaClaseCreada = null;
            aceptandoNombreMateria = true;
            return;
        }

        // =======================
        // Código de materia partido
        // =======================
        if (limpio.matches(REGEX_CODIGO_PARCIAL)) {
            codigoParcial = limpio;
            return;
        }

        if (codigoParcial != null && limpio.matches("\\d{3}")) {
            codigoMateriaActual = codigoParcial + limpio;
            codigoParcial = null;
            nombreMateriaActual = new StringBuilder();
            ultimaClaseCreada = null;
            aceptandoNombreMateria = true;
            return;
        }

        // =======================
        // Nombre de materia (solo ANTES del horario)
        // =======================
        if (aceptandoNombreMateria
                && Character.isLetter(limpio.charAt(0))
                && !DIAS.containsKey(limpio)
                && !limpio.startsWith("TOTAL")
                && !limpio.startsWith("NOTAS")
                && !limpio.startsWith("VERSIÓN")
                && !limpio.matches(REGEX_CODIGO)
                && !limpio.matches(REGEX_CODIGO_PARCIAL)
                && !limpio.matches(REGEX_HORARIO_COMPLETO)
                && !limpio.matches(REGEX_HORARIO_INICIO)
                && !limpio.matches(REGEX_HORARIO_FIN)) {

            if (nombreMateriaActual.length() > 0)
                nombreMateriaActual.append(" ");

            String limpioMateria = limpiarNombreMateria(limpio);

            if (!limpioMateria.isEmpty()) {
                if (nombreMateriaActual.length() > 0)
                    nombreMateriaActual.append(" ");

                nombreMateriaActual.append(limpioMateria);
            }

            return;
        }

        // =======================
        // Horario completo
        // =======================
        if (limpio.matches(REGEX_HORARIO_COMPLETO)) {
            procesarHorario(limpio, x);
            return;
        }

        // =======================
        // Horario partido (inicio)
        // =======================
        if (limpio.matches(REGEX_HORARIO_INICIO)) {
            horarioParcial = limpio;
            return;
        }

        // =======================
        // Horario partido (fin)
        // =======================
        if (horarioParcial != null && limpio.matches(REGEX_HORARIO_FIN)) {
            String completo = horarioParcial + limpio;
            horarioParcial = null;
            procesarHorario(completo, x);
        }
    }

    // =======================
    // Procesamiento de horario
    // =======================

    private void procesarHorario(String texto, float x) {

        if (codigoMateriaActual == null) return;

        DayOfWeek dia = diaPorColumna(x);
        if (dia == null) return;

        Intervalo intervalo = HorarioUtils.parseHorario(texto);

        String nombre =
                (nombreMateriaActual != null && nombreMateriaActual.length() > 0)
                        ? normalizarNombreMateria(nombreMateriaActual.toString())
                        : null;

        ClaseHorario clase = new ClaseHorario(
                codigoMateriaActual,
                nombre,
                dia,
                intervalo
        );

        horario.get(dia).add(clase);
        ultimaClaseCreada = clase;

        // 🔒 Después del primer horario ya NO se acepta más nombre
        aceptandoNombreMateria = false;
    }

    // =======================
    // Utilidades
    // =======================

    private void registrarColumnaDia(float x, DayOfWeek dia) {
        final float TOLERANCIA = 5f;
        for (float px : columnasDias.keySet()) {
            if (Math.abs(px - x) < TOLERANCIA) return;
        }
        columnasDias.put(x, dia);
    }

    private DayOfWeek diaPorColumna(float xHorario) {
        float mejor = Float.MAX_VALUE;
        DayOfWeek dia = null;

        for (var e : columnasDias.entrySet()) {
            float dx = Math.abs(e.getKey() - xHorario);
            if (dx < mejor) {
                mejor = dx;
                dia = e.getValue();
            }
        }
        return dia;
    }
    private String limpiarNombreMateria(String texto) {

        // Normaliza espacios
        texto = texto.replaceAll("\\s+", " ").trim();

        // Si no hay guion, no hay docente
        if (!texto.contains("-")) {
            return texto;
        }

        String[] partes = texto.split("-");

        if (partes.length < 2) {
            return texto;
        }

        String izquierda = partes[0].trim();   // Materia + Docente
        String derecha = partes[1].trim();     // Grupo

        // Quitar el último "token largo" de la izquierda (docente)
        String[] palabras = izquierda.split(" ");

        StringBuilder materia = new StringBuilder();
        for (String p : palabras) {
            // Heurística: apellidos suelen ser largos
            if (p.length() >= 12) {
                break;
            }
            materia.append(p).append(" ");
        }

        // Añadir grupo (ETIC, A, B, etc.)
        materia.append(derecha);

        return materia.toString().trim();
    }
    private String normalizarNombreMateria(String texto) {

        // Normaliza espacios
        texto = texto.replaceAll("\\s+", " ").trim();

        // Caso: MATERIA  DOCENTE - GRUPO
        // Ej: SEM DE TITULACION Y NETZAHUALCOYOTZI - ETIC
        return texto.replaceAll(
                "^(.+?)\\s+[A-ZÁÉÍÓÚÑ]{6,}(?:\\s+[A-ZÁÉÍÓÚÑ]{6,})*\\s*-\\s*([A-Z0-9]{1,6})$",
                "$1 $2"
        ).trim();
    }


    // =======================
    // Getters
    // =======================

    public Map<DayOfWeek, List<ClaseHorario>> getHorario() {
        return horario;
    }

    public String getNombreAlumno() {
        return nombreAlumno;
    }

}

