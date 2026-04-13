import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

public class Alumno {

    private final String nombreCompleto;
    private final Map<DayOfWeek, List<ClaseHorario>> horario;

    public Alumno(String nombreCompleto,
                  Map<DayOfWeek, List<ClaseHorario>> horario) {
        this.nombreCompleto = nombreCompleto;
        this.horario = horario;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public Map<DayOfWeek, List<ClaseHorario>> getHorario() {
        return horario;
    }
}

