package com.taikatranslator.infra.server;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import io.javalin.http.UploadedFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.taikatranslator.core.pipeline.TranslationPipeline;

public class TranslationServer {
    private static final Logger log = LoggerFactory.getLogger(TranslationServer.class);
    private static final Map<String, String> defaultEnv = loadEnvFile(new File(".env"));

    public static void start(int port) {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });
        });

        // 1. Healthcheck / Status endpoint
        app.get("/api/status", ctx -> {
            Map<String, Object> status = new HashMap<>();
            status.put("status", "UP");
            status.put("hasAzure", hasDefaultKey("AZURE_KEY"));
            status.put("hasDeepL", hasDefaultKey("DEEPL_KEY"));
            ctx.json(status);
        });

        // 2. Main translation trigger endpoint
        app.post("/api/translate", ctx -> {
            log.info("Received web translation request");

            UploadedFile uploadedFile = ctx.uploadedFile("file");
            if (uploadedFile == null) {
                ctx.status(400).json(errorResponse("No file uploaded. Please upload a PDF or image file."));
                return;
            }

            // Extract dynamic params or fallback to env
            String azureEndpoint = getParam(ctx, "azureEndpoint", "AZURE_ENDPOINT");
            String azureKey = getParam(ctx, "azureKey", "AZURE_KEY");
            String deeplKey = getParam(ctx, "deeplKey", "DEEPL_KEY");
            String deeplEndpoint = getParam(ctx, "deeplEndpoint", "DEEPL_ENDPOINT");
            String pythonExe = getParam(ctx, "pythonExe", "PYTHON_EXE");
            String scriptPath = getParam(ctx, "scriptPath", "SCRIPT_PATH"); // Optional script override

            // Temp files handling
            String runId = UUID.randomUUID().toString();
            File runOutputDir = new File("output/web_runs/" + runId);
            runOutputDir.mkdirs();

            File tempInputFile = new File(runOutputDir, "uploaded_" + uploadedFile.filename());
            try {
                Files.write(tempInputFile.toPath(), uploadedFile.content().readAllBytes());
            } catch (IOException e) {
                log.error("Failed to write uploaded file to disk", e);
                ctx.status(500).json(errorResponse("Server error: failed to write file to disk."));
                return;
            }

            try {
                log.info("Starting pipeline execution for runId: {}", runId);
                TranslationPipeline.PipelineResult result = TranslationPipeline.run(
                        tempInputFile,
                        runOutputDir,
                        azureEndpoint,
                        azureKey,
                        deeplKey,
                        deeplEndpoint,
                        pythonExe,
                        scriptPath
                );

                Map<String, Object> response = new HashMap<>();
                response.put("status", "SUCCESS");
                response.put("runId", runId);
                response.put("originalName", uploadedFile.filename());
                response.put("englishDownloadUrl", "/api/download?runId=" + runId + "&type=reconstructed");
                response.put("spanishDownloadUrl", "/api/download?runId=" + runId + "&type=translated");
                
                log.info("Translation completed successfully for runId: {}", runId);
                ctx.json(response);

            } catch (Exception e) {
                log.error("Error executing translation pipeline", e);
                ctx.status(500).json(errorResponse("Pipeline execution failed: " + e.getMessage()));
            }
        });

        // 3. Download generated files endpoint
        app.get("/api/download", ctx -> {
            String runId = ctx.queryParam("runId");
            String type = ctx.queryParam("type"); // "translated" or "reconstructed"

            if (runId == null || type == null) {
                ctx.status(400).result("Missing query parameters: runId and type are required");
                return;
            }

            File dir = new File("output/web_runs/" + runId);
            if (!dir.exists() || !dir.isDirectory()) {
                ctx.status(404).result("Processing directory not found or expired.");
                return;
            }

            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().endsWith(".docx")) {
                        if ("translated".equals(type) && f.getName().contains("_translated")) {
                            serveFile(ctx, f);
                            return;
                        }
                        if ("reconstructed".equals(type) && f.getName().contains("_reconstructed")) {
                            serveFile(ctx, f);
                            return;
                        }
                    }
                }
            }
            ctx.status(404).result("Requested file was not found in the output folder.");
        });

        log.info("Starting Javalin Translation Server on port {}...", port);
        app.start(port);
        log.info("Server is online! Access it at http://localhost:{}", port);
    }

    private static void serveFile(Context ctx, File f) throws IOException {
        ctx.header("Content-Disposition", "attachment; filename=\"" + f.getName() + "\"");
        ctx.contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        ctx.result(new FileInputStream(f));
    }

    private static Map<String, Object> errorResponse(String msg) {
        Map<String, Object> err = new HashMap<>();
        err.put("status", "ERROR");
        err.put("message", msg);
        return err;
    }

    private static String getParam(Context ctx, String formKey, String envKey) {
        String formVal = ctx.formParam(formKey);
        if (formVal != null && !formVal.trim().isEmpty()) {
            return formVal.trim();
        }
        
        // Check system env or .env file
        String sysVal = System.getenv(envKey);
        if (sysVal != null && !sysVal.isEmpty()) {
            return sysVal;
        }
        return defaultEnv.get(envKey);
    }

    private static boolean hasDefaultKey(String key) {
        String val = System.getenv(key);
        if (val != null && !val.trim().isEmpty()) return true;
        String envVal = defaultEnv.get(key);
        return envVal != null && !envVal.trim().isEmpty();
    }

    private static Map<String, String> loadEnvFile(File envFile) {
        Map<String, String> env = new HashMap<>();
        if (!envFile.exists()) return env;
        try {
            java.util.List<String> lines = Files.readAllLines(envFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String value = line.substring(eqIdx + 1).trim();
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    }
                    env.put(key, value);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to load .env file in server config: {}", e.getMessage());
        }
        return env;
    }
}
