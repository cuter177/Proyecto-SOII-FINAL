import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.UIManager;
import java.io.File;
import java.nio.file.Path;

public class SelectorDestino {
    public static Path pedirRutaAlUsuario(String nombrePorDefecto) {
        // Intentar que el diálogo use la estética del SO actual (Windows, Linux, etc.)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Si falla, Java usará su tema visual por defecto
        }

        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Seleccionar destino de guardado");
        selector.setSelectedFile(new File(nombrePorDefecto));
        
        // Filtro para asegurar que sea un archivo de texto
        FileNameExtensionFilter filtro = new FileNameExtensionFilter("Documento de texto (*.txt)", "txt");
        selector.setFileFilter(filtro);

        int resultado = selector.showSaveDialog(null);

        if (resultado == JFileChooser.APPROVE_OPTION) {
            File archivo = selector.getSelectedFile();
            
            // Verificación manual de extensión .txt si el usuario no la escribió
            String ruta = archivo.getAbsolutePath();
            if (!ruta.toLowerCase().endsWith(".txt")) {
                archivo = new File(ruta + ".txt");
            }
            
            return archivo.toPath();
        }

        return null; // El usuario cerró la ventana o canceló
    }
}
