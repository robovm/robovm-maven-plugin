/*
 * Copyright (C) 2015 RoboVM AB.
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

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.robovm.compiler.AppCompiler;
import org.robovm.compiler.config.Config;

/**
 * Goal which builds the app or binary as specified by the RoboVM config and
 * installs it to the install dir (usually <code>target/robovm</code>.
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class BuildMojo extends AbstractRoboVMMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        try {

            Config.Builder builder = configure(new Config.Builder())
                    .skipInstall(false);

            AppCompiler compiler = new AppCompiler(builder.build());
            compiler.compile();
            Config config = compiler.getConfig();
            if (config.getInstallDir().exists()) {
                FileUtils.cleanDirectory(config.getInstallDir());
            }
            config.getTarget().install();

        } catch (Throwable t) {
            throw new MojoExecutionException("Failed to build", t);
        }
    }
}
