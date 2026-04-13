import java.time.DayOfWeek;

public class ClaseHorario {

    private final String codigo;
    private String nombre;
    private final DayOfWeek dia;
    private final Intervalo intervalo;

    public ClaseHorario(String codigo, String nombre, DayOfWeek dia, Intervalo intervalo) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.dia = dia;
        this.intervalo = intervalo;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombreMateria() {
        return nombre;
    }
    public void setNombreMateria(String nombre) {
        this.nombre = nombre;
    }

    public DayOfWeek getDia() {
        return dia;
    }

    public Intervalo getIntervalo() {
        return intervalo;
    }
}


