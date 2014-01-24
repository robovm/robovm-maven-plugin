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
import org.robovm.compiler.config.Arch;
import org.robovm.compiler.config.Config;
import org.robovm.compiler.config.Config.TargetType;
import org.robovm.compiler.config.OS;
import org.robovm.compiler.target.ios.IOSSimulatorLaunchParameters;

import java.io.IOException;

public abstract class AbstractIOSSimulatorMojo extends AbstractRoboVMMojo {

    private IOSSimulatorLaunchParameters.Family targetFamily;

    /**
     * @parameter expression="${robovm.iosSimSdk}"
     */
    protected String sdk;

    protected AbstractIOSSimulatorMojo(
            IOSSimulatorLaunchParameters.Family targetFamily) {
        this.targetFamily = targetFamily;
    }

    public void execute() throws MojoExecutionException, MojoFailureException {

        try {

            Config config = buildArchive(OS.ios, Arch.x86, TargetType.ios);
            IOSSimulatorLaunchParameters launchParameters = (IOSSimulatorLaunchParameters) config
                    .getTarget().createLaunchParameters();
            launchParameters.setFamily(targetFamily);
            launchParameters.setSdk(sdk);
            config.getTarget().launch(launchParameters).waitFor();

        } catch (InterruptedException e) {
            throw new MojoExecutionException("Failed to launch IOS Simulator",
                    e);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to launch IOS Simulator",
                    e);
        }
    }
}
