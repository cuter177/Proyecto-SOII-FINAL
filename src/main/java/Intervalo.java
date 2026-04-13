import java.time.LocalTime;

public class Intervalo {

    public final LocalTime inicio;
    public final LocalTime fin;

    public Intervalo(LocalTime inicio, LocalTime fin) {
        this.inicio = inicio;
        this.fin = fin;
    }

    @Override
    public String toString() {
        return inicio + "–" + fin;
    }
}

