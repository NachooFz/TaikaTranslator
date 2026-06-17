package com.udocmachine.core.vision;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import com.udocmachine.model.BoundingBox;

public class VisionResult {
    private File cleanImage;
    private List<ArtifactInfo> artifacts = new ArrayList<>();

    public VisionResult() {}

    public VisionResult(File cleanImage, List<ArtifactInfo> artifacts) {
        this.cleanImage = cleanImage;
        this.artifacts = artifacts;
    }

    public File getCleanImage() {
        return cleanImage;
    }

    public void setCleanImage(File cleanImage) {
        this.cleanImage = cleanImage;
    }

    public List<ArtifactInfo> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<ArtifactInfo> artifacts) {
        this.artifacts = artifacts;
    }

    public void addArtifact(File file, BoundingBox bounds, String type) {
        this.artifacts.add(new ArtifactInfo(file, bounds, type));
    }

    public static class ArtifactInfo {
        private File artifactFile;
        private BoundingBox boundingBox;
        private String type; // e.g. "signature", "stamp", "seal"

        public ArtifactInfo() {}

        public ArtifactInfo(File artifactFile, BoundingBox boundingBox, String type) {
            this.artifactFile = artifactFile;
            this.boundingBox = boundingBox;
            this.type = type;
        }

        public File getArtifactFile() {
            return artifactFile;
        }

        public void setArtifactFile(File artifactFile) {
            this.artifactFile = artifactFile;
        }

        public BoundingBox getBoundingBox() {
            return boundingBox;
        }

        public void setBoundingBox(BoundingBox boundingBox) {
            this.boundingBox = boundingBox;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return String.format("Artifact[type=%s, file=%s, bounds=%s]", type, artifactFile.getName(), boundingBox);
        }
    }
}
