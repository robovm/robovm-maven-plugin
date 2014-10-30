/*
 * Copyright (C) 2013 Trillian Mobile AB.
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

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.XMLWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomWriter;
import org.robovm.compiler.AppCompiler;
import org.robovm.compiler.Version;
import org.robovm.compiler.config.Arch;
import org.robovm.compiler.config.Config;
import org.robovm.compiler.config.Config.Lib;
import org.robovm.compiler.config.Config.TargetType;
import org.robovm.compiler.config.OS;
import org.robovm.compiler.log.Logger;
import org.robovm.compiler.target.ios.ProvisioningProfile;
import org.robovm.compiler.target.ios.SigningIdentity;

/**
 */
public abstract class AbstractRoboVMMojo extends AbstractMojo {

    /**
     * The maven project.
     * 
     * @parameter expression="${project}"
     * @required
     */
    protected MavenProject project;

    /**
     * To look up Archiver/UnArchiver implementations
     * 
     * @component
     * @readonly
     */
    private ArchiverManager archiverManager;

    /**
     * To resolve artifacts
     * 
     * @component
     * @readonly
     */
    private ArtifactResolver artifactResolver;

    /**
     * 
     * @parameter default-value="${localRepository}"
     * 
     */
    private ArtifactRepository localRepository;

    /**
     * Base directory to extract RoboVM native distribution files into. The
     * robovm-dist bundle will be downloaded from Maven and extracted into this
     * directory. Note that each release of RoboVM is placed in a separate
     * sub-directory with the version number as suffix.
     * 
     * If not set, then the tar file is extracted into the local Maven
     * repository where the tar file is downloaded to.
     * 
     * @parameter
     */
    protected File home;

    /**
     * @parameter expression="${robovm.propertiesFile}"
     */
    protected File propertiesFile;

    /**
     * @parameter expression="${robovm.configFile}"
     */
    protected File configFile;

    /**
     * The identity to sign the app as when building an iOS bundle for the app.
     * 
     * @parameter expression="${robovm.iosSignIdentity}"
     */
    protected String iosSignIdentity;

    /**
     * The provisioning profile to use when building for device..
     * 
     * @parameter expression="${robovm.iosProvisioningProfile}"
     */
    protected String iosProvisioningProfile;

    /**
     * Whether the app should be signed or not. Unsigned apps can only be run
     * on jailbroken devices.
     *
     * @parameter expression="${robovm.iosSkipSigning}"
     */
    protected boolean iosSkipSigning = false;

    /**
     * The directory that the RoboVM distributable for the project will be built
     * to.
     * 
     * @parameter expression="${project.build.directory}/robovm"
     */
    protected File installDir;

    /**
     * @parameter
     */
    protected boolean includeJFX;

    private Logger roboVMLogger;

    protected Config configure(Config.Builder configBuilder) throws IOException {
        return configBuilder.build();
    }

    public Config buildArchive(OS os, Arch arch, TargetType targetType)
            throws MojoExecutionException, MojoFailureException {

        getLog().info("Building RoboVM app for: " + os + " (" + arch + ")");

        Config.Builder builder;
        try {
            builder = new Config.Builder();
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        builder.logger(getRoboVMLogger());

        // load config base file if it exists (and properties)

        if (propertiesFile != null) {
            if (!propertiesFile.exists()) {
                throw new MojoExecutionException(
                        "Invalid 'propertiesFile' specified for RoboVM compile: "
                                + propertiesFile);
            }
            try {
                getLog().debug(
                        "Including properties file in RoboVM compiler config: "
                                + propertiesFile.getAbsolutePath());
                builder.addProperties(propertiesFile);
            } catch (IOException e) {
                throw new MojoExecutionException(
                        "Failed to add properties file to RoboVM config: "
                                + propertiesFile);
            }
        } else {
            try {
                builder.readProjectProperties(project.getBasedir(), false);
            } catch (IOException e) {
                throw new MojoExecutionException(
                        "Failed to read RoboVM project properties file(s) in " 
                                + project.getBasedir().getAbsolutePath(), e);
            }
        }

        if (configFile != null) {
            if (!configFile.exists()) {
                throw new MojoExecutionException(
                        "Invalid 'configFile' specified for RoboVM compile: "
                                + configFile);
            }
            try {
                getLog().debug(
                        "Loading config file for RoboVM compiler: "
                                + configFile.getAbsolutePath());
                builder.read(configFile);
            } catch (Exception e) {
                throw new MojoExecutionException(
                        "Failed to read RoboVM config file: " + configFile);
            }
        } else {
            try {
                builder.readProjectConfig(project.getBasedir(), false);
            } catch (Exception e) {
                throw new MojoExecutionException(
                        "Failed to read project RoboVM config file in " 
                                + project.getBasedir().getAbsolutePath(), e);
            }
        }

        // Read embedded RoboVM <config> if there is one
        Plugin plugin = project.getPlugin("org.robovm:robovm-maven-plugin");
        MavenProject p = project;
        while (p != null && plugin == null) {
            plugin = p.getPluginManagement().getPluginsAsMap().get("org.robovm:robovm-maven-plugin");
            if (plugin == null) p = p.getParent();
        }
        if (plugin != null) {
            getLog().debug("Reading RoboVM plugin configuration from " + p.getFile().getAbsolutePath());
            Xpp3Dom configDom = (Xpp3Dom) plugin.getConfiguration();
            if (configDom != null && configDom.getChild("config") != null) {
                StringWriter sw = new StringWriter();
                XMLWriter xmlWriter = new PrettyPrintXMLWriter(sw, "UTF-8", null);
                Xpp3DomWriter.write(xmlWriter, configDom.getChild("config"));
                try {
                    builder.read(new StringReader(sw.toString()),
                            project.getBasedir());
                } catch (Exception e) {
                    throw new MojoExecutionException(
                            "Failed to read RoboVM config embedded in POM", e);
                }
            }
        }

        File tmpDir = new File(new File(installDir, os.name()), arch.name());
        try {
            FileUtils.deleteDirectory(tmpDir);
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failed to clean output dir " + tmpDir, e);
        }
        tmpDir.mkdirs();

        builder.home(new Config.Home(unpackRoboVMDist()))
                .tmpDir(tmpDir)
                .targetType(targetType).skipInstall(true)
                .installDir(installDir).os(os).arch(arch);

        if (iosSkipSigning) {
            builder.iosSkipSigning(true);
        } else {
            if (iosSignIdentity != null) {
                getLog().debug(
                        "Using explicit iOS Signing identity: " + iosSignIdentity);
                builder.iosSignIdentity(SigningIdentity.find(
                        SigningIdentity.list(), iosSignIdentity));
            }

            if (iosProvisioningProfile != null) {
                getLog().debug(
                        "Using explicit iOS provisioning profile: "
                                + iosProvisioningProfile);
                builder.iosProvisioningProfile(ProvisioningProfile.find(
                        ProvisioningProfile.list(), iosProvisioningProfile));
            }
        }

        builder.clearClasspathEntries();

        // add JavaFX if needed

        if (includeJFX) {

            getLog().info("Including JavaFX Runtime in build");

            // add jfxrt.jar from 78 backport to classpath
            File jfxJar = resolveJavaFXBackportRuntimeArtifact();
            getLog().debug("JavaFX backport runtime JAR found at: " + jfxJar);
            builder.addClasspathEntry(jfxJar);

            // add backport compatibility additions to classpath
            File jfxCompatJar = resolveJavaFXBackportCompatibilityArtifact();
            getLog().debug(
                    "JavaFX backport compatibilty JAR found at: "
                            + jfxCompatJar);
            builder.addClasspathEntry(jfxCompatJar);

            // include native files as resources

            File iosNativesBaseDir = unpackJavaFXNativeIOSArtifact();
            // builder.addLib(new File(iosNativesBaseDir, "libdecora-sse-" +
            // arch.getClangName() + ".a").getAbsolutePath());
            builder.addLib(new Lib(new File(iosNativesBaseDir, "libglass-"
                    + arch.getClangName() + ".a").getAbsolutePath(), true));
            builder.addLib(new Lib(new File(iosNativesBaseDir, "libjavafx-font-"
                    + arch.getClangName() + ".a").getAbsolutePath(), true));
            builder.addLib(new Lib(new File(iosNativesBaseDir, "libjavafx-iio-"
                    + arch.getClangName() + ".a").getAbsolutePath(), true));
            builder.addLib(new Lib(new File(iosNativesBaseDir, "libprism-common-"
                    + arch.getClangName() + ".a").getAbsolutePath(), true));
            builder.addLib(new Lib(new File(iosNativesBaseDir, "libprism-es2-"
                    + arch.getClangName() + ".a").getAbsolutePath(), true));
            // builder.addLib(new File(iosNativesBaseDir, "libprism-sw-" +
            // arch.getClangName() + ".a").getAbsolutePath());

            // add default 'roots' needed for JFX to work
            builder.addForceLinkClass("com.sun.javafx.tk.quantum.QuantumToolkit");
            builder.addForceLinkClass("com.sun.prism.es2.ES2Pipeline");
            builder.addForceLinkClass("com.sun.prism.es2.IOSGLFactory");
            builder.addForceLinkClass("com.sun.glass.ui.ios.**.*");
            builder.addForceLinkClass("javafx.scene.CssStyleHelper");
            builder.addForceLinkClass("com.sun.prism.shader.**.*");
            builder.addForceLinkClass("com.sun.scenario.effect.impl.es2.ES2ShaderSource");
            builder.addForceLinkClass("com.sun.javafx.font.coretext.CTFactory");
            builder.addForceLinkClass("sun.util.logging.PlatformLogger");

            // add default 'frameworks' needed for JFX to work
            builder.addFramework("UIKit");
            builder.addFramework("OpenGLES");
            builder.addFramework("QuartzCore");
            builder.addFramework("CoreGraphics");
            builder.addFramework("CoreText");
            builder.addFramework("ImageIO");
            builder.addFramework("MobileCoreServices");

            // todo do we need to exclude built-in JFX from JDK classpath?
        }

        // configure the runtime classpath

        try {
            for (Object object : project.getRuntimeClasspathElements()) {
                String path = (String) object;
                if (getLog().isDebugEnabled()) {
                    getLog().debug(
                            "Including classpath element for RoboVM app: "
                                    + path);
                }
                builder.addClasspathEntry(new File(path));
            }
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException(
                    "Error resolving application classpath for RoboVM build", e);
        }

        // execute the RoboVM build

        try {

            getLog().info(
                    "Compiling RoboVM app, this could take a while, especially the first time round");
            Config config = configure(builder);
            AppCompiler compiler = new AppCompiler(config);
            compiler.compile();

            return config;

        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Error building RoboVM executable for app", e);
        }
    }

    protected String getRoboVMVersion() {
        return Version.getVersion();
    }

    protected File unpackRoboVMDist() throws MojoExecutionException {

        Artifact distTarArtifact = resolveRoboVMDistArtifact();
        File distTarFile = distTarArtifact.getFile();
        File unpackBaseDir;
        if (home != null) {
            unpackBaseDir = home;
        } else {
            // by default unpack into the local repo directory
            unpackBaseDir = new File(distTarFile.getParent(), "unpacked");
        }
        if (unpackBaseDir.exists() && distTarArtifact.isSnapshot()) {
            getLog().debug("Deleting directory for unpacked snapshots: " + unpackBaseDir);
            try {
                FileUtils.deleteDirectory(unpackBaseDir);
            } catch (IOException e) {
                throw new MojoExecutionException(
                        "Failed to delete " + unpackBaseDir, e);
            }
        }
        unpack(distTarFile, unpackBaseDir);
        File unpackedDir = new File(unpackBaseDir, "robovm-" + getRoboVMVersion());
        return unpackedDir;
    }

    protected Artifact resolveRoboVMDistArtifact() throws MojoExecutionException {

        MavenArtifactHandler handler = new MavenArtifactHandler("tar.gz");
        Artifact artifact = new DefaultArtifact("org.robovm", "robovm-dist",
                getRoboVMVersion(), "", "tar.gz", "nocompiler", handler);
        return resolveArtifact(artifact);
    }

    protected File resolveJavaFXBackportRuntimeArtifact()
            throws MojoExecutionException {

        MavenArtifactHandler handler = new MavenArtifactHandler("jar");
        Artifact artifact = new DefaultArtifact("net.java.openjfx.backport",
                "openjfx-78-backport", "1.8.0-ea-b96.1", "", "jar", "ios",
                handler);
        return resolveArtifact(artifact).getFile();
    }

    protected File resolveJavaFXBackportCompatibilityArtifact()
            throws MojoExecutionException {

        MavenArtifactHandler handler = new MavenArtifactHandler("jar");
        Artifact artifact = new DefaultArtifact("net.java.openjfx.backport",
                "openjfx-78-backport-compat", "1.8.0.1", "", "jar", "", handler);
        return resolveArtifact(artifact).getFile();
    }

    protected File resolveJavaFXNativeArtifact() throws MojoExecutionException {

        MavenArtifactHandler handler = new MavenArtifactHandler("jar");
        Artifact artifact = new DefaultArtifact("net.java.openjfx.backport",
                "openjfx-78-backport-native", "1.8.0-ea-b96.1", "", "jar",
                "ios", handler);
        return resolveArtifact(artifact).getFile();
    }

    protected File unpackJavaFXNativeIOSArtifact()
            throws MojoExecutionException {

        File jarFile = resolveJavaFXNativeArtifact();
        // by default unpack into the local repo directory
        File unpackBaseDir = new File(jarFile.getParent(), "unpacked");
        unpack(jarFile, unpackBaseDir);
        return unpackBaseDir;
    }

    protected Artifact resolveArtifact(Artifact artifact)
            throws MojoExecutionException {

        ArtifactResolutionRequest request = new ArtifactResolutionRequest();
        request.setArtifact(artifact);
        request.setLocalRepository(localRepository);
        final List<ArtifactRepository> remoteRepositories = project
                .getRemoteArtifactRepositories();
        request.setRemoteRepositories(remoteRepositories);

        getLog().debug("Resolving artifact " + artifact);

        ArtifactResolutionResult result = artifactResolver.resolve(request);
        if (!result.isSuccess()) {
            throw new MojoExecutionException("Unable to resolve artifact: "
                    + artifact);
        }
        Collection<Artifact> resolvedArtifacts = result.getArtifacts();
        artifact = (Artifact) resolvedArtifacts.iterator().next();
        return artifact;
    }

    protected void unpack(File archive, File targetDirectory)
            throws MojoExecutionException {

        if (!targetDirectory.exists()) {

            getLog().info("Extracting '" + archive + "' to: " + targetDirectory);
            if (!targetDirectory.mkdirs()) {
                throw new MojoExecutionException(
                        "Unable to create base directory to unpack into: "
                                + targetDirectory);
            }

            try {
                UnArchiver unArchiver = archiverManager.getUnArchiver(archive);
                unArchiver.setSourceFile(archive);
                unArchiver.setDestDirectory(targetDirectory);
                unArchiver.extract();
            } catch (NoSuchArchiverException e) {
                throw new MojoExecutionException("Unable to unpack archive "
                        + archive + " to " + targetDirectory, e);
            }
            getLog().debug(
                    "Archive '" + archive + "' unpacked to: " + targetDirectory);

        } else {
            getLog().debug(
                    "Archive '" + archive + "' was already unpacked in: "
                            + targetDirectory);
        }
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
