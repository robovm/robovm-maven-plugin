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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.robovm.compiler.util.Executor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @goal ios-simulator
 * @phase package
 * @execute goal="robovm"
 * @requiresDependencyResolution
 */
public class IOSSimulatorMojo extends AbstractRoboVMMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {

        try {

            List<Object> args = new ArrayList<Object>();
            args.add("launch");
            args.add(outputDir);
            args.add("--unbuffered");

            args.add("--family");
            args.add("iphone");

            File roboVMBinDir = new File(unpackRoboVMDist(), "bin");
            String iosSimPath = new File(roboVMBinDir, "ios-sim").getAbsolutePath();

            Executor executor = new Executor(getRoboVMLogger(), iosSimPath)
                    .args(args)
                    .wd(outputDir);
            executor.execAsync();

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to launch IOS Simulator", e);
        }
    }
}
