package org.robovm.maven.plugin;

/**
 * @goal ipad-sim
 * @phase package
 * @execute goal="robovm"
 * @requiresDependencyResolution
 */
public class IPadSimMojo extends AbstractIOSSimulatorMojo {

    public IPadSimMojo() {
        super("ipad");
    }
}
