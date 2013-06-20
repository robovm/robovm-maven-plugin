package org.robovm.maven.plugin;

/**
 * @goal iphone-sim
 * @phase package
 * @execute goal="robovm"
 * @requiresDependencyResolution
 */
public class IPhoneSimMojo extends AbstractIOSSimulatorMojo {

    public IPhoneSimMojo() {
        super("iphone");
    }
}
