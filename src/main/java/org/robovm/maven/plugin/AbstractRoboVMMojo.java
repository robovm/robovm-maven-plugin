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
import org.codehaus.plexus.util.FileUtils;
import org.robovm.compiler.AppCompiler;
import org.robovm.compiler.config.Arch;
import org.robovm.compiler.config.Config;
import org.robovm.compiler.config.Config.TargetType;
import org.robovm.compiler.config.OS;
import org.robovm.compiler.log.Logger;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 */
public abstract class AbstractRoboVMMojo extends AbstractMojo {

    public static final String ROBO_VM_VERSION = "0.0.2";


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
     * The directory where LLVM is installed. If this is not set, then the plugin will default to using the local
     * repository (i.e. .m2 directory) and LLVM will be downloaded and installed under
     * org/robovm/robovm-dist/robovm-dist-{version}/unpack/llvm.
     */
    protected File llvmHomeDir;

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
    protected File[] resources;

    /**
     * Specifies class patterns matching classes that must be linked in when compiling. By default the RoboVM compiler
     * will link in all classes that are referenced, directly or indirectly, by the target main class. It will not,
     * however, link in classes that are loaded only by reflection (e.g. via e.g. Class.forName()) so these must be
     * manually specified by adding a specific 'root' value.
     *
     * @parameter
     */
    protected String[] roots;

    /**
     * The directory that the RoboVM distributable for the project will be built to.
     *
     * @parameter expression="${project.build.directory}/robovm"
     */
    protected File installDir;


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
                .llvmHomeDir(unpackLLVM())
                .tmpDir(archiveDir)
                .targetType(targetType)
                .skipInstall(true)
                .os(os)
                .arch(arch);

        if (roots != null) {
            for (String root : roots) {
                getLog().debug("Including class root for linking: " + root);
                builder.addRoot(root);
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
            for (File resource : resources) {
                if (!resource.exists()) {
                    throw new MojoExecutionException("Invalid 'resource' directory specified for RoboVM compile: " + resource);
                }
                getLog().debug("Including resource directory: " + resource.getAbsolutePath());
                builder.addResource(resource);
            }
        } else {
            // check if default resource dir exists
            File resource = new File(robovmSrcDir, "resources");
            if (resource.exists()) {
                getLog().debug("Using default resource directory: " + resource.getAbsolutePath());
                builder.addResource(resource);
            }
        }


        // configure the runtime classpath

        builder.clearClasspathEntries();
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


    protected File unpackLLVM() throws MojoExecutionException {

        if (llvmHomeDir != null) {

            // if LLVM Home has been manually specified, use this directly
            return llvmHomeDir;

        } else {

            // if LLVM Home has not been set, use the robovm-dist repository directory

            File distTarFile = resolveRoboVMDistArtifact();
            File unpackBaseDir = new File(distTarFile.getParent(), "unpacked");

            File llvmInstallDir = new File(unpackBaseDir, "llvm");

            if (new File(llvmInstallDir, "bin").exists()) {

                getLog().debug("Using LLVM already installed in " + llvmInstallDir.getAbsolutePath());

            } else {

                String llvmDownloadURL;
                String llvmExtractedDir;

                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("mac")) {
                    llvmDownloadURL = "http://llvm.org/releases/3.2/clang+llvm-3.2-x86_64-apple-darwin11.tar.gz";
                    llvmExtractedDir = "clang+llvm-3.2-x86_64-apple-darwin11";
                    getLog().debug("Using Mac Download URL for LLVM: " + llvmDownloadURL);
                } else if (os.contains("linux")) {
                    // note we assume Ubuntu - not sure what happens on other distros
                    llvmDownloadURL = "http://llvm.org/releases/3.2/clang+llvm-3.2-x86_64-linux-ubuntu-12.04.tar.gz";
                    llvmExtractedDir = "clang+llvm-3.2-x86_64-linux-ubuntu-12.04";
                    getLog().debug("Using Linux Download URL for LLVM: " + llvmDownloadURL);
                } else {
                    throw new MojoExecutionException("The OS you are running on ('" + os
                            + "') is not supported by RoboVM. Only Mac and Linux are supported at this stage");
                }

                try {

                    File llvmTempArchive = new File(unpackBaseDir, "llvm.tar.gz");
                    if (llvmTempArchive.exists()) {
                        getLog().debug("Deleting previous downloaded archive of LLVM");
                        if (!llvmTempArchive.delete()) {
                            throw new MojoExecutionException(
                                    "Failed to delete archive from previous download of LLVM, try manually deleting this file: " + llvmTempArchive);
                        }
                    }

                    getLog().info("Downloading LLVM from " + llvmDownloadURL);
                    BufferedInputStream in = null;
                    FileOutputStream fout = null;
                    try {
                        in = new BufferedInputStream(new URL(llvmDownloadURL).openStream());
                        fout = new FileOutputStream(llvmTempArchive);
                        byte data[] = new byte[1024];
                        int count;
                        int total = 0;
                        while ((count = in.read(data, 0, 1024)) != -1) {
                            fout.write(data, 0, count);
                            total += count;
                            System.out.print("Downloading LLVM  " + total + " bytes                   \r");
                        }
                        getLog().info("LLVM downloaded to " + llvmTempArchive);
                    }
                    finally {
                        if (in != null) {
                            in.close();
                        }
                        if (fout != null) {
                            fout.close();
                        }
                    }

                    getLog().info("Unpacking LLVM to " + llvmInstallDir);
                    File tempDir = new File(unpackBaseDir, "llvm.temp");
                    if (tempDir.exists()) {
                        getLog().debug("Deleting previous unpacked archive of LLVM");
                        if (!tempDir.delete()) {
                            throw new MojoExecutionException(
                                    "Failed to delete unpacked directory for previous download of LLVM, try manually deleting: " + tempDir);
                        }
                    }
                    unpack(llvmTempArchive, tempDir);
                    FileUtils.rename(new File(tempDir, llvmExtractedDir), llvmInstallDir);

                    tempDir.delete();
                    llvmTempArchive.delete();

                } catch (Exception e) {
                    throw new MojoExecutionException("Failed to download LLVM from " + llvmDownloadURL, e);
                }
            }

            return llvmInstallDir;
        }
    }

    protected File resolveRoboVMDistArtifact() throws MojoExecutionException {

        // resolve the 'dist' dependency using Maven
        ArtifactRequest request = new ArtifactRequest();
        DefaultArtifact artifact = new DefaultArtifact("org.robovm:robovm-dist:tar.gz:" + ROBO_VM_VERSION);
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
