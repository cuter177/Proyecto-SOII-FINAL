import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

public class HorarioUtils {

    private HorarioUtils() {}

    // =======================
    // Parseo de horarios PDF
    // =======================
    public static Intervalo parseHorario(String texto) {

        String[] partes = texto.split("-");

        int hIni = Integer.parseInt(partes[0].substring(0, 2));
        int mIni = Integer.parseInt(partes[0].substring(2, 4));

        int hFin = Integer.parseInt(partes[1].substring(0, 2));
        int mFin = Integer.parseInt(partes[1].substring(2, 4));

        LocalTime inicio = LocalTime.of(hIni, mIni);

        // Regla BUAP: 59 → siguiente hora
        LocalTime fin = (mFin == 59)
                ? LocalTime.of(hFin, 0).plusHours(1)
                : LocalTime.of(hFin, mFin);

        return new Intervalo(inicio, fin);
    }

    // =======================
    // Unión de intervalos
    // =======================
    public static List<Intervalo>
    unirIntervalos(List<Intervalo> intervalos) {

        if (intervalos == null || intervalos.isEmpty())
            return List.of();

        intervalos.sort(
                Comparator.comparing(i -> i.inicio)
        );

        List<Intervalo> resultado = new ArrayList<>();
        Intervalo actual = intervalos.get(0);

        for (int i = 1; i < intervalos.size(); i++) {
            Intervalo sig = intervalos.get(i);

            if (!sig.inicio.isAfter(actual.fin)) {
                actual = new Intervalo(
                        actual.inicio,
                        actual.fin.isAfter(sig.fin)
                                ? actual.fin
                                : sig.fin
                );
            } else {
                resultado.add(actual);
                actual = sig;
            }
        }

        resultado.add(actual);
        return resultado;
    }

    // =======================
    // Unión de horarios (alumnos)
    // =======================
    public static Map<DayOfWeek, List<Intervalo>>
    unirHorariosOcupados(List<Alumno> alumnos) {

        Map<DayOfWeek, List<Intervalo>> ocupados =
                new EnumMap<>(DayOfWeek.class);

        for (DayOfWeek d : DayOfWeek.values()) {
            ocupados.put(d, new ArrayList<>());
        }

        for (Alumno a : alumnos) {
            for (var entry : a.getHorario().entrySet()) {
                for (ClaseHorario c : entry.getValue()) {
                    ocupados.get(entry.getKey())
                            .add(c.getIntervalo());
                }
            }
        }

        Map<DayOfWeek, List<Intervalo>> resultado =
                new EnumMap<>(DayOfWeek.class);

        for (DayOfWeek dia : List.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
        )) {
            resultado.put(
                    dia,
                    unirIntervalos(ocupados.get(dia))
            );
        }

        return resultado;
    }

    // =======================
    // Complemento de intervalos
    // =======================
    public static List<Intervalo>
    complemento(List<Intervalo> ocupados,
                LocalTime inicio,
                LocalTime fin) {

        List<Intervalo> libres = new ArrayList<>();
        LocalTime cursor = inicio;

        for (Intervalo o : ocupados) {

            if (cursor.isBefore(o.inicio)) {
                libres.add(new Intervalo(cursor, o.inicio));
            }

            if (o.fin.isAfter(cursor)) {
                cursor = o.fin;
            }
        }

        if (cursor.isBefore(fin)) {
            libres.add(new Intervalo(cursor, fin));
        }

        return libres;
    }

    // =======================
    // Horarios libres comunes
    // =======================
    public static Map<DayOfWeek, List<Intervalo>>
    horariosLibres(List<Alumno> alumnos) {

        Map<DayOfWeek, List<Intervalo>> ocupados =
                unirHorariosOcupados(alumnos);

        Map<DayOfWeek, List<Intervalo>> libres =
                new EnumMap<>(DayOfWeek.class);

        LocalTime INICIO = LocalTime.of(11, 0);
        LocalTime FIN = LocalTime.of(19, 0);

        for (DayOfWeek dia : ocupados.keySet()) {

            List<Intervalo> libresDia =
                    complemento(
                            ocupados.get(dia),
                            INICIO,
                            FIN
                    );

            libres.put(dia, libresDia);
        }

        return libres;
    }
}

