package com.avilaos.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

// Lee y guarda la configuración en un archivo JSON.
// Cualquier problema con el archivo sale como IllegalArgumentException("Archivo inválido: ...").
// load no valida los valores: eso lo hace ConfigValidator.
public final class ConfigLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigLoader() {
    }

        public static SimulationConfig load(File file) {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            SimulationConfig config = GSON.fromJson(reader, SimulationConfig.class);
            if (config == null) {
                throw new IllegalArgumentException("Archivo inválido: está vacío");
            }
            checkNoNullEntries(config.computers, "computers");
            checkNoNullEntries(config.buffers, "buffers");
            checkNoNullEntries(config.processes, "processes");
            return config;
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Archivo inválido: no es un JSON de configuración de ÁvilaOS ("
                    + describe(e) + ")");
        } catch (IOException e) {
            throw new IllegalArgumentException("Archivo inválido: no se pudo leer (" + e.getMessage() + ")");
        }
    }

    // Una lista con un elemento null ("computers": [null]) es JSON válido, pero no una configuración.
    private static void checkNoNullEntries(Object[] list, String name) {
        if (list == null) {
            return;                       // lista ausente: ConfigValidator dice qué falta
        }
        for (int i = 0; i < list.length; i++) {
            if (list[i] == null) {
                throw new IllegalArgumentException("Archivo inválido: el elemento " + (i + 1)
                        + " de \"" + name + "\" está vacío (null)");
            }
        }
    }

    // Traduce el error de Gson a algo legible: qué pasó y en qué línea/columna.
    private static String describe(JsonParseException e) {
        Throwable cause = (e.getCause() != null) ? e.getCause() : e;
        String msg = String.valueOf(cause.getMessage());
        String what = (cause instanceof NumberFormatException)
                ? "un campo numérico no contiene un número entero"
                : "estructura incorrecta o texto que no es JSON";
        int at = msg.indexOf("line ");
        if (at < 0) {
            return what;
        }
        String where = msg.substring(at);
        int end = where.indexOf(" path");
        if (end >= 0) {
            where = where.substring(0, end);
        }
        return what + ", cerca de " + where.replace("line", "la línea").replace("column", "columna");
    }

    public static void save(SimulationConfig config, File file) {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (JsonParseException | IOException e) {
            throw new IllegalArgumentException("Archivo inválido: no se pudo guardar (" + e.getMessage() + ")");
        }
    }
}
