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

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.robovm.compiler.AppCompiler;
import org.robovm.compiler.config.Arch;
import org.robovm.compiler.config.Config;
import org.robovm.compiler.config.Config.TargetType;
import org.robovm.compiler.config.OS;
import org.robovm.compiler.config.Resource;
import org.robovm.compiler.log.Logger;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 */
public abstract class AbstractRoboVMMojo extends AbstractMojo {

    public static final String ROBO_VM_VERSION = "0.0.4";


    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     */
    protected MavenProject project;

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
    protected File home;

    /**
     * @parameter
     */
    protected File propertiesFile;

    /**
     * @parameter
     */
    protected File configFile;

    /**
     * The main class to run when the application is started.
     *
     * @parameter
     * @required
     */
    protected String mainClass;

    /**
     * The iOS SDK version level supported by this app when running on iOS.
     *
     * @parameter
     */
    protected String iosSdkVersion;

    /**
     * The identity to sign the app as when building an iOS bundle for the app.
     *
     * @parameter
     */
    protected String iosSignIdentity;

    /**
     * @parameter expression="${project.build.finalName}"
     */
    protected String executableName;

    /**
     * @parameter
     */
    protected File iosInfoPList;

    /**
     * @parameter
     */
    protected File iosEntitlementsPList;

    /**
     * @parameter
     */
    protected File iosResourceRulesPList;

    /**
     * @parameter
     */
    protected String[] frameworks;

    /**
     * @parameter
     */
    protected String[] libs;

    /**
     * @parameter
     */
    protected Resource[] resources;

    /**
     * Specifies class patterns matching classes that must be linked in when compiling. By default the RoboVM compiler
     * will link in all classes that are referenced, directly or indirectly, by the target main class. It will not,
     * however, link in classes that are loaded only by reflection (e.g. via e.g. Class.forName()) so these must be
     * manually specified by adding a specific 'root' value.
     *
     * @parameter
     */
    protected String[] forceLinkClasses;

    /**
     * The directory that the RoboVM distributable for the project will be built to.
     *
     * @parameter expression="${project.build.directory}/robovm"
     */
    protected File installDir;

    /**
     * @parameter
     */
    protected boolean includeJFX;


    private Logger roboVMLogger;


    public Config buildArchive(OS os, Arch arch, TargetType targetType) throws MojoExecutionException, MojoFailureException {

        getLog().info("Building RoboVM app for: " + os + " (" + arch + ")");

        Config.Builder builder = new Config.Builder();
        File robovmSrcDir = new File(project.getBasedir(), "src/main/robovm");

        // load config base file if it exists (and properties)

        if (propertiesFile != null) {
            if (!propertiesFile.exists()) {
                throw new MojoExecutionException("Invalid 'propertiesFile' specified for RoboVM compile: " + propertiesFile);
            }
            try {
                getLog().debug("Including properties file in RoboVM compiler config: " + propertiesFile.getAbsolutePath());
                builder.addProperties(propertiesFile);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to add properties file to RoboVM config: " + propertiesFile);
            }
        } else {
            File file = new File(robovmSrcDir, "Info.plist");
            if (file.exists()) {
                getLog().debug("Using default properties file: " + file.getAbsolutePath());
                builder.iosInfoPList(file);
            }
        }

        if (configFile != null) {
            if (!configFile.exists()) {
                throw new MojoExecutionException("Invalid 'configFile' specified for RoboVM compile: " + configFile);
            }
            try {
                getLog().debug("Loading config file for RoboVM compiler: " + configFile.getAbsolutePath());
                builder.read(configFile);
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to read RoboVM config file: " + configFile);
            }
        } else {
            File file = new File(robovmSrcDir, "config.xml");
            if (file.exists()) {
                getLog().debug("Using default config file: " + file.getAbsolutePath());
                builder.iosInfoPList(file);
            }
        }


        // override other settings based on POM

        File osDir = new File(installDir, os.name());
        File archiveDir = new File(new File(osDir, arch.name()), "");

        builder.home(new Config.Home(unpackRoboVMDist()))
                .logger(getRoboVMLogger())
                .mainClass(mainClass)
                .executableName(executableName)
                .tmpDir(archiveDir)
                .targetType(targetType)
                .skipInstall(true)
                .os(os)
                .arch(arch);

        if (forceLinkClasses != null) {
            for (String pattern : forceLinkClasses) {
                getLog().debug("Including class pattern for linking: " + pattern);
                builder.addForceLinkClass(pattern);
            }
        }

        if (frameworks != null) {
            for (String framework : frameworks) {
                getLog().debug("Including framework: " + framework);
                builder.addFramework(framework);
            }
        }

        if (libs != null) {
            for (String lib : libs) {
                getLog().debug("Including lib: " + lib);
                builder.addLib(lib);
            }
        }

        if (iosSdkVersion != null) {
            getLog().debug("Using explicit iOS SDK version: " + iosSdkVersion);
            builder.iosSdkVersion(iosSdkVersion);
        }

        if (iosSignIdentity != null) {
            getLog().debug("Using explicit iOS Signing identity: " + iosSignIdentity);
            builder.iosSignIdentity(iosSignIdentity);
        }

        if (iosInfoPList != null) {
            if (!iosInfoPList.exists()) {
                throw new MojoExecutionException("Invalid 'iosInfoPList' specified for RoboVM compile: " + iosInfoPList);
            }
            getLog().debug("Using Info.plist input file: " + iosInfoPList.getAbsolutePath());
            builder.iosInfoPList(iosInfoPList);
        } else {
            File file = new File(robovmSrcDir, "Info.plist");
            if (file.exists()) {
                getLog().debug("Using default Info.plist input file: " + file.getAbsolutePath());
                builder.iosInfoPList(file);
            }
        }

        if (iosEntitlementsPList != null) {
            if (!iosEntitlementsPList.exists()) {
                throw new MojoExecutionException("Invalid 'iosEntitlementsPList' specified for RoboVM compile: " + iosEntitlementsPList);
            }
            getLog().debug("Using Entitlements.plist input file: " + iosEntitlementsPList.getAbsolutePath());
            builder.iosEntitlementsPList(iosEntitlementsPList);
        } else {
            File file = new File(robovmSrcDir, "Entitlements.plist");
            if (file.exists()) {
                getLog().debug("Using default Entitlements.plist input file: " + file.getAbsolutePath());
                builder.iosEntitlementsPList(file);
            }
        }

        if (iosResourceRulesPList != null) {
            if (!iosResourceRulesPList.exists()) {
                throw new MojoExecutionException("Invalid 'iosResourceRulesPList' specified for RoboVM compile: " + iosResourceRulesPList);
            }
            getLog().debug("Using ResourceRules.plist input file: " + iosResourceRulesPList.getAbsolutePath());
            builder.iosResourceRulesPList(iosResourceRulesPList);
        } else {
            File file = new File(robovmSrcDir, "ResourceRules.plist");
            if (file.exists()) {
                getLog().debug("Using default ResourceRules.plist input file: " + file.getAbsolutePath());
                builder.iosResourceRulesPList(file);
            }
        }

        if (resources != null) {
            for (Resource resource : resources) {
                getLog().debug("Including resource: " + resource);
                builder.addResource(resource);
            }
        } else {
            // check if default resource dir exists
            File resource = new File(robovmSrcDir, "resources");
            if (resource.exists()) {
                getLog().debug("Using default resource directory: " + resource.getAbsolutePath());
                builder.addResource(new Resource(resource, null));
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
            getLog().debug("JavaFX backport compatibilty JAR found at: " + jfxCompatJar);
            builder.addClasspathEntry(jfxCompatJar);

            // include native files as resources

            File iosNativesBaseDir = unpackJavaFXNativeIOSArtifact();
//            builder.addLib(new File(iosNativesBaseDir, "libdecora-sse-" + arch.getClangName() + ".a").getAbsolutePath());
            builder.addLib(new File(iosNativesBaseDir, "libglass-" + arch.getClangName() + ".a").getAbsolutePath());
            builder.addLib(new File(iosNativesBaseDir, "libjavafx-font-" + arch.getClangName() + ".a").getAbsolutePath());
            builder.addLib(new File(iosNativesBaseDir, "libjavafx-iio-" + arch.getClangName() + ".a").getAbsolutePath());
            builder.addLib(new File(iosNativesBaseDir, "libprism-common-" + arch.getClangName() + ".a").getAbsolutePath());
            builder.addLib(new File(iosNativesBaseDir, "libprism-es2-" + arch.getClangName() + ".a").getAbsolutePath());
//            builder.addLib(new File(iosNativesBaseDir, "libprism-sw-" + arch.getClangName() + ".a").getAbsolutePath());

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
                    getLog().debug("Including classpath element for RoboVM app: " + path);
                }
                builder.addClasspathEntry(new File(path));
            }
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Error resolving application classpath for RoboVM build", e);
        }

        // execute the RoboVM build

        try {

            getLog().info("Compiling RoboVM app, this could take a while, especially the first time round");
            Config config = builder.build();
            AppCompiler compiler = new AppCompiler(config);
            compiler.compile();

            return config;

        } catch (IOException e) {
            throw new MojoExecutionException("Error building RoboVM executable for app", e);
        }
    }

    protected File unpackRoboVMDist() throws MojoExecutionException {

        File distTarFile = resolveRoboVMDistArtifact();
        File unpackBaseDir;
        if (home != null) {
            unpackBaseDir = home;
        } else {
            // by default unpack into the local repo directory
            unpackBaseDir = new File(distTarFile.getParent(), "unpacked");
        }
        File unpackedDir = new File(unpackBaseDir, "robovm-" + ROBO_VM_VERSION);
        unpack(distTarFile, unpackBaseDir);
        return unpackedDir;
    }


    protected File resolveRoboVMDistArtifact() throws MojoExecutionException {

        return resolveArtifact("org.robovm:robovm-dist:tar.gz:nocompiler:" + ROBO_VM_VERSION);
    }

    protected File resolveJavaFXBackportRuntimeArtifact() throws MojoExecutionException {

        return resolveArtifact("net.java.openjfx.backport:openjfx-78-backport:jar:ios:1.8.0-ea-b96.1");
    }

    protected File resolveJavaFXBackportCompatibilityArtifact() throws MojoExecutionException {

        return resolveArtifact("net.java.openjfx.backport:openjfx-78-backport-compat:1.8.0.1");
    }

    protected File resolveJavaFXNativeArtifact() throws MojoExecutionException {

        return resolveArtifact("net.java.openjfx.backport:openjfx-78-backport-native:jar:ios:1.8.0-ea-b96.1");
    }


    protected File unpackJavaFXNativeIOSArtifact() throws MojoExecutionException {

        File jarFile = resolveJavaFXNativeArtifact();
        // by default unpack into the local repo directory
        File unpackBaseDir = new File(jarFile.getParent(), "unpacked");
        unpack(jarFile, unpackBaseDir);
        return unpackBaseDir;
    }

    protected File resolveArtifact(String artifactLocator) throws MojoExecutionException {

        ArtifactRequest request = new ArtifactRequest();
        DefaultArtifact artifact = new DefaultArtifact(artifactLocator);
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

        return result.getArtifact().getFile();
    }

    protected void unpack(File archive, File targetDirectory) throws MojoExecutionException {

        if (!targetDirectory.exists()) {

            getLog().info("Extracting '" + archive + "' to: " + targetDirectory);
            if (!targetDirectory.mkdirs()) {
                throw new MojoExecutionException("Unable to create base directory to unpack into: " + targetDirectory);
            }

            try {
                UnArchiver unArchiver = archiverManager.getUnArchiver(archive);
                unArchiver.setSourceFile(archive);
                unArchiver.setDestDirectory(targetDirectory);
                unArchiver.extract();
            } catch (NoSuchArchiverException e) {
                throw new MojoExecutionException("Unable to unpack archive " + archive + " to " + targetDirectory, e);
            }
            getLog().debug("Archive '" + archive + "' unpacked to: " + targetDirectory);

        } else {
            getLog().debug("Archive '" + archive + "' was already unpacked in: " + targetDirectory);
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
