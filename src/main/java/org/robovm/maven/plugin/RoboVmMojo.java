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
import org.robovm.compiler.AppCompiler;
import org.robovm.compiler.config.Config;

import java.io.File;
import java.io.IOException;

/**
 * @goal robovm
 * @phase package
 * @execute phase="package"
 * @requiresDependencyResolution
 */
public class RoboVmMojo extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     */
    protected MavenProject project;

    public void execute() throws MojoExecutionException, MojoFailureException {

        getLog().info("Building RoboVM app");

        Config.Builder builder = new Config.Builder();

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
            AppCompiler compiler = new AppCompiler(builder.build());
            compiler.compile();
        } catch (IOException e) {
            throw new MojoExecutionException("Error resolving application classpath for RoboVM build", e);
        }
    }
}
