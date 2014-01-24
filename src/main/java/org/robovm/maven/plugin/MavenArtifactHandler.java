package org.robovm.maven.plugin;

import org.apache.maven.artifact.handler.ArtifactHandler;

public class MavenArtifactHandler implements ArtifactHandler
{
	public String getExtension()
	{
		return "tar.gz";
	}

	public String getDirectory()
	{
		return "";
	}

	public String getClassifier()
	{
		return "";
	}

	public String getPackaging()
	{
		return "";
	}

	public boolean isIncludesDependencies()
	{
		return false;
	}

	public String getLanguage()
	{
		return "";
	}

	public boolean isAddedToClasspath()
	{
		return false;
	}
}
