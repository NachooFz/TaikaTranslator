package com.udocmachine.infra.vision;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.udocmachine.core.vision.VisionException;
import com.udocmachine.core.vision.VisionProcessor;
import com.udocmachine.core.vision.VisionResult;
import com.udocmachine.model.BoundingBox;

public class ProcessBuilderVisionProcessor implements VisionProcessor {
    private static final Logger log = LoggerFactory.getLogger(ProcessBuilderVisionProcessor.class);
    private final String pythonExecutable;
    private final String scriptPath;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProcessBuilderVisionProcessor(String pythonExecutable, String scriptPath) {
        this.pythonExecutable = pythonExecutable;
        this.scriptPath = scriptPath;
    }

    @Override
    public VisionResult processPage(File sourcePageImage, File outputDirectory) throws VisionException {
        log.info("Invoking Python Vision Helper on: {}", sourcePageImage.getAbsolutePath());
        
        try {
            // Build the command line process
            List<String> command = new ArrayList<>();
            command.add(pythonExecutable);
            command.add(scriptPath);
            command.add("--input");
            command.add(sourcePageImage.getAbsolutePath());
            command.add("--output-dir");
            command.add(outputDirectory.getAbsolutePath());

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true); // Combine standard error and output
            
            log.debug("Executing command: {}", String.join(" ", command));
            Process process = builder.start();
            
            // Stream and parse output
            StringBuilder outputCollector = new StringBuilder();
            String resultJsonString = null;
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[Python stdout] {}", line);
                    outputCollector.append(line).append("\n");
                    if (line.startsWith("RESULT_JSON:")) {
                        resultJsonString = line.substring("RESULT_JSON:".length());
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new VisionException("Python vision helper failed with exit code " + exitCode + ". Log:\n" + outputCollector);
            }

            if (resultJsonString == null) {
                throw new VisionException("Python script completed but did not return RESULT_JSON marker.");
            }

            // Parse result JSON
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = mapper.readValue(resultJsonString, Map.class);
            String status = (String) resultMap.get("status");
            
            if ("ERROR".equals(status)) {
                throw new VisionException("Python vision model reported error: " + resultMap.get("message"));
            }

            String cleanImageName = (String) resultMap.get("cleanImage");
            File cleanPageFile = new File(outputDirectory, cleanImageName);
            
            VisionResult result = new VisionResult();
            result.setCleanImage(cleanPageFile);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> artifactsList = (List<Map<String, Object>>) resultMap.get("artifacts");
            if (artifactsList != null) {
                for (Map<String, Object> artMap : artifactsList) {
                    String filename = (String) artMap.get("file");
                    String type = (String) artMap.get("type");
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> boxMap = (Map<String, Object>) artMap.get("boundingBox");
                    double top = ((Number) boxMap.get("top")).doubleValue();
                    double left = ((Number) boxMap.get("left")).doubleValue();
                    double width = ((Number) boxMap.get("width")).doubleValue();
                    double height = ((Number) boxMap.get("height")).doubleValue();

                    File artifactFile = new File(outputDirectory, filename);
                    BoundingBox boundingBox = new BoundingBox(top, left, width, height);
                    result.addArtifact(artifactFile, boundingBox, type);
                }
            }

            log.info("Python vision processing completed successfully. Found {} visual artifacts.", 
                    result.getArtifacts().size());
            return result;

        } catch (Exception e) {
            log.error("Failed to run Python Vision Helper", e);
            throw new VisionException("ProcessBuilder failed to execute Vision Helper: " + e.getMessage(), e);
        }
    }
}
