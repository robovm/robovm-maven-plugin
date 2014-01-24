package org.robovm.maven.plugin;

import org.apache.maven.artifact.handler.ArtifactHandler;

public class MavenArtifactHandler implements ArtifactHandler {

	private String extension;

	public MavenArtifactHandler() {
		}

	public MavenArtifactHandler(String ext) {
		this.extension = ext;
	}

	public String getExtension() {
		return extension;
	}

	public String getDirectory() {
		return "";
	}

	public String getClassifier() {
		return "";
	}

	public String getPackaging() {
		return "";
	}

	public boolean isIncludesDependencies() {
		return false;
	}

	public String getLanguage() {
		return "";
	}

	public boolean isAddedToClasspath() {
		return false;
	}
}
