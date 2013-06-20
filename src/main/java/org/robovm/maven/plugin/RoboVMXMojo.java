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
public class RoboVMXMojo extends AbstractRoboVMMojo {

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     */
    protected MavenProject project;

    /**
     * @parameter expression="${project.baseDir}/src/main/robovm/robovm.properties"
     */
    protected File propertiesFile;

    /**
     * @parameter expression="${project.baseDir}/src/main/robovm/config.xml"
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
     * @parameter expression="${project.baseDir}/src/main/robovm/Info.plist"
     */
    protected File infoPList;

    /**
     * @parameter expression="${project.baseDir}/src/main/robovm/Entitlements.plist"
     */
    protected File entitlementsPList;

    /**
     * @parameter expression="${project.baseDir}/src/main/robovm/ResourceRules.plist"
     */
    protected File resourceRulesPList;

    /**
     * @parameter
     */
    protected String[] frameworks;

    /**
     * @parameter
     */
    protected String[] libs;


    public void execute() throws MojoExecutionException, MojoFailureException {

        getLog().info("Building RoboVM app");

        // standard configuration

        Config.Builder builder = new Config.Builder()
                .home(new Config.Home(unpackRoboVMDist()))
                .logger(getRoboVMLogger())
                .mainClass(mainClass)
                .executableName(executableName)
                .os(os)
                .arch(arch)
                .llvmHomeDir(unpackLLVM())
                .installDir(installDir);


        if (propertiesFile.exists()) {
            try {
                getLog().debug("Including properties file in RoboVM compiler config: " + propertiesFile.getAbsolutePath());
                builder.addProperties(propertiesFile);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to add properties file to RoboVM config: " + propertiesFile);
            }
        }

        if (configFile.exists()) {
            try {
                getLog().debug("Loading config file for RoboVM compiler: " + configFile.getAbsolutePath());
                builder.read(configFile);
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to read RoboVM config file: " + configFile);
            }
        }

        if (frameworks != null) {
            for (String framework : frameworks) {
                builder.addFramework(framework);
            }
        }

        if (libs != null) {
            for (String lib : libs) {
                builder.addLib(lib);
            }
        }

        if (iosSdkVersion != null) {
            builder.iosSdkVersion(iosSdkVersion);
        }

        if (iosSignIdentity != null) {
            builder.iosSignIdentity(iosSignIdentity);
        }

        if (infoPList.exists()) {
            getLog().debug("Using Info.plist input file: " + infoPList.getAbsolutePath());
            builder.iosInfoPList(infoPList);
        }

        if (entitlementsPList.exists()) {
            getLog().debug("Using Entitlements.plist input file: " + entitlementsPList.getAbsolutePath());
            builder.iosEntitlementsPList(entitlementsPList);
        }

        if (resourceRulesPList.exists()) {
            getLog().debug("Using ResourceRules.plist input file: " + resourceRulesPList.getAbsolutePath());
            builder.iosResourceRulesPList(resourceRulesPList);
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

            Config config = builder.build();
            AppCompiler compiler = new AppCompiler(config);
            compiler.compile();
            config.getTarget().install();

        } catch (IOException e) {
            throw new MojoExecutionException("Error building RoboVM executable for app", e);
        }
    }
}
