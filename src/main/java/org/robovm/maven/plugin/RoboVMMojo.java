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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.robovm.compiler.AppCompiler;
import org.robovm.compiler.config.Arch;
import org.robovm.compiler.config.Config;
import org.robovm.compiler.config.OS;

import java.io.File;
import java.io.IOException;

/**
 * @goal robovm
 * @phase package
 * @execute phase="package"
 * @requiresDependencyResolution
 */
public class RoboVMMojo extends AbstractRoboVMMojo {

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     */
    protected MavenProject project;

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
     * The architecture to use. Use "x86" for iOS.
     *
     * @parameter
     */
    protected Arch arch;

    /**
     * The operating system to build for. Use "ios" for building to iOS.
     *
     * @parameter
     */
    protected OS os;

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


    public void execute() throws MojoExecutionException, MojoFailureException {

        getLog().info("Building RoboVM app");

        Config.Builder builder = new Config.Builder();
        File robovmSrcDir = new File(project.getBasedir(), "src/main/robovm");

        // load config base file it it exists (and properties)

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

        builder.home(new Config.Home(unpackRoboVMDist()))
                .logger(getRoboVMLogger())
                .mainClass(mainClass)
                .executableName(executableName)
                .llvmHomeDir(unpackLLVM())
                .installDir(installDir);

        if (os != null) {
            getLog().debug("Using explicit OS: " + os);
            builder.os(os);
        }

        if (arch != null) {
            getLog().debug("Using explicit chip architecture: " + arch);
            builder.arch(arch);
        }

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
            config.getTarget().install();

        } catch (IOException e) {
            throw new MojoExecutionException("Error building RoboVM executable for app", e);
        }
    }
}
