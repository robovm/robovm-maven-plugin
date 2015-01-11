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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.robovm.compiler.AppCompiler;
import org.robovm.compiler.config.Arch;
import org.robovm.compiler.config.Config;
import org.robovm.compiler.config.Config.TargetType;
import org.robovm.compiler.config.OS;

/**
 * Compiles your application and creates an IPA file suitable for upload to the app store.
 */
@Mojo(name="create-ipa", defaultPhase=LifecyclePhase.PACKAGE,
      requiresDependencyResolution=ResolutionScope.COMPILE_PLUS_RUNTIME)
public class CreateIPAMojo extends AbstractRoboVMMojo {

    /**
     * Colon separated list of architectures to include in the IPA. Either
     * thumbv7 or arm64 or both.
     */
    @Parameter(property="robovm.ipaArchs")
    protected String ipaArchs;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        try {

            Config.Builder builder = configure(new Config.Builder())
                    .skipInstall(false)
                    .os(OS.ios)
                    .targetType(TargetType.ios);
            
            List<Arch> archs = new ArrayList<>();
            if (ipaArchs == null || ipaArchs.trim().isEmpty()) {
                archs.add(Arch.thumbv7);
            } else {
                for (String s : ipaArchs.trim().split(":")) {
                    archs.add(Arch.valueOf(s));
                }
            }
            
            AppCompiler compiler = new AppCompiler(builder.build());
            compiler.createIpa(archs);

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create IPA", e);
        }
    }
}
