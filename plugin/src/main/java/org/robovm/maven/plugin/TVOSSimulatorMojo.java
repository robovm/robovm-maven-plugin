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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.robovm.compiler.AppCompiler;
import org.robovm.compiler.config.Arch;
import org.robovm.compiler.config.Config;
import org.robovm.compiler.config.OS;
import org.robovm.compiler.target.ios.DeviceType;
import org.robovm.compiler.target.ios.DeviceType.DeviceFamily;
import org.robovm.compiler.target.ios.SimulatorLaunchParameters;
import org.robovm.compiler.target.ios.TVOSTarget;

/**
 * Compiles your application and deploys it to the AppleTV simulator.
 */
@Mojo(name="tvos-sim", defaultPhase=LifecyclePhase.PACKAGE,
      requiresDependencyResolution=ResolutionScope.COMPILE_PLUS_RUNTIME)
public class TVOSSimulatorMojo extends AbstractRoboVMMojo {
    /**
     * The tvOS SDK version to use when choosing the simulator (e.g. "9.0"). Defaults to the newest
     * SDK version.
     */
    @Parameter(property="robovm.tvosSimSdk")
    protected String sdk;

    /**
     * The identifier of the simulator device to use (e.g. "Apple-TV"). Run
     * {@code ios-sim showdevicetypes} for a full list.
     */
    @Parameter(property="robovm.tvosDeviceName")
    protected String deviceName;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            
            AppCompiler compiler = build(OS.tvos, Arch.x86_64, TVOSTarget.TYPE);
            Config config = compiler.getConfig();
            SimulatorLaunchParameters launchParameters = (SimulatorLaunchParameters)
                config.getTarget().createLaunchParameters();

            // select the device based on the (optional) SDK version and (optional) device type
            DeviceType deviceType = DeviceType.getBestDeviceType(
                    Arch.x86_64, OS.tvos, DeviceFamily.AppleTV, deviceName, sdk);
            launchParameters.setDeviceType(deviceType);
            compiler.launch(launchParameters);

        } catch (Throwable t) {
            throw new MojoExecutionException("Failed to launch tvOS simulator", t);
        }
    }
}
