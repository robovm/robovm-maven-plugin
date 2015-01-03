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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.robovm.compiler.config.Arch;
import org.robovm.compiler.config.Config;
import org.robovm.compiler.config.Config.TargetType;
import org.robovm.compiler.config.OS;
import org.robovm.compiler.target.ios.DeviceType;
import org.robovm.compiler.target.ios.DeviceType.DeviceFamily;
import org.robovm.compiler.target.ios.IOSSimulatorLaunchParameters;

import java.io.IOException;
import java.util.List;

public abstract class AbstractIOSSimulatorMojo extends AbstractRoboVMMojo {

    private DeviceFamily deviceFamily;

    /**
     * The iOS SDK version to use when choosing the simulator (e.g. "8.0"). Defaults to the newest
     * SDK version.
     */
    @Parameter(property="robovm.iosSimSdk")
    protected String sdk;

    /**
     * The identifier of the simulator device to use (e.g. "iPhone-5s", "iPad-Retina"). Run {@code
     * ios-sim showdevicetypes} for a full list.
     */
    @Parameter(property="robovm.iosDeviceName")
    protected String deviceName;

    protected AbstractIOSSimulatorMojo(DeviceFamily deviceFamily) {
        this.deviceFamily = deviceFamily;
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Arch arch = Arch.x86;
            if (super.arch != null && super.arch.equals(Arch.x86_64.toString())) {
                arch = Arch.x86_64;
            }
            
            Config config = buildArchive(OS.ios, arch, TargetType.ios);
            IOSSimulatorLaunchParameters launchParameters = (IOSSimulatorLaunchParameters)
                config.getTarget().createLaunchParameters();

            // select the device based on the (optional) SDK version and (optional) device type
            DeviceType deviceType = getBestDeviceType(
                config.getHome(), deviceFamily, deviceName, sdk);
            launchParameters.setDeviceType(deviceType);
            config.getTarget().launch(launchParameters).waitFor();

        } catch (InterruptedException e) {
            throw new MojoExecutionException("Failed to launch IOS Simulator", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to launch IOS Simulator", e);
        }
    }

    private DeviceType getBestDeviceType(Config.Home home, DeviceFamily family,
                                         String deviceName, String sdk)
        throws MojoFailureException {
        List<DeviceType> devices = DeviceType.listDeviceTypes(home);
        if (devices.isEmpty()) {
            throw new MojoFailureException("Unable to enumerate simulator devices");
        }
        DeviceType best = null;
        for (DeviceType dt : devices) {
            if (dt.getFamily() != family) continue;
            boolean nameMatch = (deviceName == null) || deviceName.equals(dt.getSimpleDeviceName());
            boolean sdkMatch = (sdk == null) || sdk.equals(dt.getSdk().getVersion());
            if (!nameMatch || !sdkMatch) continue;
            // if we have an existing match, we need to check whether this match is "better"; that
            // only happens when we have not specified an SDK, in which case we want the match with
            // the newest SDK
            if (best == null ||
                dt.getSdk().getVersion().compareTo(best.getSdk().getVersion()) > 0) {
                best = dt;
            }
        }
        if (best == null) {
            throw new MojoFailureException("Unable to find a matching device [name=" + deviceName +
                ", sdk=" + sdk + "]");
        }
        return best;
    }
}
