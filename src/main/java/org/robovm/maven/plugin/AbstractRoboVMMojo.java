/*
 * Copyright (C) 2013 Trillian AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.robovm.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.robovm.compiler.log.Logger;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import java.io.File;
import java.util.List;

/**
 * @requiresDependencyResolution
 */
public abstract class AbstractRoboVMMojo extends AbstractMojo {


    /**
     * The entry point to Aether, i.e. the component doing all the work.
     *
     * @component
     * @readonly
     */
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     *
     * @parameter default-value="${repositorySystemSession}"
     * @readonly
     */
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution of plugins and their dependencies.
     *
     * @parameter default-value="${project.remotePluginRepositories}"
     * @readonly
     */
    private List<RemoteRepository> remoteRepos;

    /**
     * To look up Archiver/UnArchiver implementations
     *
     * @component
     * @readonly
     */
    private ArchiverManager archiverManager;

    /**
     * Base directory to extract RoboVM native distribution files into. The robovm-dist bundle will be downloaded from
     * Maven and extracted into this directory. Note that each release of RoboVM is placed in a separate sub-directory
     * with the version number as suffix.
     *
     * If not set, then the tar file is extracted into the local Maven repository where the tar file is downloaded to.
     *
     * @parameter
     */
    protected File distDir;

    /**
     * The directory that the RoboVM distributable for the project will be built to.
     *
     * @parameter expression="${project.build.directory}/robovm"
     */
    protected File outputDir;


    private Logger roboVMLogger;


    protected File unpackRoboVMDist() throws MojoExecutionException {

        String roboVmVersion = "0.0.2";

        // resolve the 'dist' dependency using Maven
        ArtifactRequest request = new ArtifactRequest();
        DefaultArtifact artifact = new DefaultArtifact("org.robovm:robovm-dist:tar.gz:" + roboVmVersion);
        request.setArtifact(artifact);
        request.setRepositories(remoteRepos);

        getLog().debug("Resolving artifact " + artifact + " from " + remoteRepos);

        ArtifactResult result;
        try {
            result = repoSystem.resolveArtifact(repoSession, request);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        getLog().debug("Resolved artifact " + artifact + " to " + result.getArtifact().getFile()
                + " from " + result.getRepository());


        // unpack the dist bundle into the maven repository

        File distTarFile = result.getArtifact().getFile();

        File unpackBaseDir;
        if (distDir != null) {
            unpackBaseDir = distDir;
        } else {
            // by default unpack into the local repo directory
            unpackBaseDir = new File(distTarFile.getParent(), "unpacked");
        }

        File unpackedDir = new File(unpackBaseDir, "robovm-" + roboVmVersion);
        if (!unpackedDir.exists()) {

            getLog().info("Extracting RoboVM dist to: " + unpackBaseDir);
            if (!unpackBaseDir.mkdirs()) {
                throw new MojoExecutionException("Unable to create base directory to unzip RoboVM dist into: " + unpackBaseDir);
            }

            try {
                UnArchiver unArchiver = archiverManager.getUnArchiver(distTarFile);
                unArchiver.setSourceFile(distTarFile);
                unArchiver.setDestDirectory(unpackBaseDir);
                unArchiver.extract();
            } catch (NoSuchArchiverException e) {
                throw new MojoExecutionException("Unable to unzip RoboVM dist from " + distTarFile + " to " + unpackedDir, e);
            }

            getLog().debug("RoboVM dist extracted to: " + unpackedDir);

        } else {

            getLog().info("Using existing extracted RoboVM dist in: " + unpackedDir);

        }

        return unpackedDir;
    }

    protected Logger getRoboVMLogger() {

        if (roboVMLogger == null) {
            roboVMLogger = new Logger() {
                public void debug(String s, Object... objects) {
                    getLog().debug(String.format(s, objects));
                }

                public void info(String s, Object... objects) {
                    getLog().info(String.format(s, objects));
                }

                public void warn(String s, Object... objects) {
                    getLog().warn(String.format(s, objects));
                }

                public void error(String s, Object... objects) {
                    getLog().error(String.format(s, objects));
                }
            };
        }
        return roboVMLogger;
    }
}
