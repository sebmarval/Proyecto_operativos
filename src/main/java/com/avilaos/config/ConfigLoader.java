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
            return config;
        } catch (JsonParseException | IOException e) {
            throw new IllegalArgumentException("Archivo inválido: " + e.getMessage());
        }
    }

    public static void save(SimulationConfig config, File file) {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (JsonParseException | IOException e) {
            throw new IllegalArgumentException("Archivo inválido: no se pudo guardar (" + e.getMessage() + ")");
        }
    }
}
