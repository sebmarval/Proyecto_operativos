package com.avilaos.gui;

import com.avilaos.kernel.EventLogger;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;

// Muestra los eventos del sistema en un JTextArea.
// El kernel llama a log() desde los hilos de los computadores, y Swing solo se
// puede tocar desde su propio hilo, por eso todo pasa por invokeLater.
public class GuiEventLogger implements EventLogger {

    private static final int MAX_LINES = 2000;

    private final JTextArea area;

    public GuiEventLogger(JTextArea area) {
        if (area == null) {
            throw new IllegalArgumentException("Se necesita un área de texto para el log");
        }
        this.area = area;
    }

    @Override
    public void log(int cycle, int computerId, String message) {
        String line = "[ciclo " + cycle + "] [PC" + computerId + "] " + message + "\n";
        SwingUtilities.invokeLater(() -> {
            area.append(line);
            trim();
            // baja el scroll para ver siempre lo último
            area.setCaretPosition(area.getDocument().getLength());
        });
    }

    // Borra las líneas más viejas cuando se pasa del máximo
    // getLineCount cuenta también la línea vacía después del último salto, por eso el -1
    private void trim() {
        int extra = area.getLineCount() -1- MAX_LINES;
        if (extra > 0) {
            try {
                area.replaceRange("", 0, area.getLineStartOffset(extra));
            } catch (BadLocationException e) {
                // no debería pasar; si pasa, se deja el texto como está
            }
        }
    }

    // Vacía el log (al iniciar una simulación nueva)
    public void clear() {
        SwingUtilities.invokeLater(() -> area.setText(""));
    }
}
