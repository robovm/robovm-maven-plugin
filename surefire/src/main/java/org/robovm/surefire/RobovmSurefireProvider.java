/*
 * Copyright (C) 2014 Trillian Mobile AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.robovm.surefire;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.surefire.providerapi.AbstractProvider;
import org.apache.maven.surefire.providerapi.ProviderParameters;
import org.apache.maven.surefire.report.*;
import org.apache.maven.surefire.suite.RunResult;
import org.apache.maven.surefire.testset.TestSetFailedException;
import org.apache.maven.surefire.util.RunOrderCalculator;
import org.apache.maven.surefire.util.ScanResult;
import org.apache.maven.surefire.util.TestsToRun;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.robovm.compiler.Version;
import org.robovm.compiler.config.Config;
import org.robovm.junit.client.TestClient;
import org.robovm.junit.protocol.ResultObject;
import org.robovm.maven.resolver.RoboVMResolver;
import org.robovm.surefire.annotations.RoboVMTest;
import org.robovm.surefire.internal.ConfigUtils;
import org.robovm.surefire.internal.Logger;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import static org.robovm.surefire.internal.Constant.*;

public class RobovmSurefireProvider extends AbstractProvider {

    private final ClassLoader testClassLoader;
    private final ProviderParameters providerParameters;
    private final ScanResult scanResult;
    private final RunOrderCalculator runOrderCalculator;
    private TestsToRun testsToRun;
    RoboVMScannerFilter testChecker;
    private Config.Builder config;
    RoboVMRunListener roboTestListener = null;
    private TestClient testClient;

    public RobovmSurefireProvider(ProviderParameters providerParameters) throws IOException {
        this.providerParameters = providerParameters;
        this.testClassLoader = providerParameters.getTestClassLoader();
        this.scanResult = providerParameters.getScanResult();
        this.runOrderCalculator = providerParameters.getRunOrderCalculator();
        testChecker = new RoboVMScannerFilter(testClassLoader);
        config = ConfigUtils.createConfig();
    }

    public RobovmSurefireProvider() {
        testClassLoader = null;
        providerParameters = null;
        scanResult = null;
        runOrderCalculator = null;
    }


    @Override
    public Iterator getSuites() {
     return null;
    }

    private TestsToRun scanClassPath() {
        final TestsToRun scannedClasses = scanResult.applyFilter(testChecker, testClassLoader);
        return runOrderCalculator.orderTestClasses(scannedClasses);
    }

    @Override
    public RunResult invoke(Object o) throws TestSetFailedException, ReporterException {
        final ReporterFactory reporterFactory = providerParameters.getReporterFactory();
        final RunListener reporter = reporterFactory.createReporter();
        testClient = new TestClient();

        Result result = new Result();
        RunNotifier runNotifier = getRunNotifier(result, roboTestListener);
        Class clazz = null;

        if (testsToRun == null) {
            testsToRun = scanClassPath();
        }

        String classesToRun[] = toStringArray(testsToRun.getLocatedClasses());

        try {
            clazz = Class.forName(classesToRun[0]);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        if (clazz.getResource("/robovm.xml") == null) {
            throw new IllegalArgumentException("Cannot find robovm.xml file in your resources directory");
        } else {
            File robovmConfigFile = new File(clazz.getResource("/robovm.xml").getFile());
            if (robovmConfigFile.exists()) {
                try {
                    config.read(robovmConfigFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                throw new IllegalArgumentException("Cannot find robovm.xml file in your resources directory");
            }
        }

        try {
            executeTestSet(classesToRun, reporter, runNotifier, config);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (MojoExecutionException e) {
            e.printStackTrace();
        }

        return reporterFactory.close();
    }

    private String[] toStringArray(Class[] locatedClasses) {
        String[] returnArray = new String[locatedClasses.length];
        for (int i = 0; i < locatedClasses.length; i++) {
            returnArray[i] = locatedClasses[i].getCanonicalName();
        }
        return returnArray;
    }

    public void executeTestSet(String[] classesToRun, final RunListener reporter, final RunNotifier runNotifier,
        Config.Builder config) throws IOException, ClassNotFoundException, MojoExecutionException {


        if (testClient == null) {
            testClient = new TestClient();
        }

        config = addJUnitConfig(classesToRun, config);
        final Config compiledConfig = testClient.compile(config);

        testClient.runTests(compiledConfig, classesToRun)
                .subscribeOn(Schedulers.newThread())
                .subscribe(new Action1<ResultObject>() {
                    @Override
                    public void call(ResultObject resultObject) {
                        process(resultObject, runNotifier, reporter);
                    }
                } , new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        System.err.println(
                                "Error receiving result from simulator " + throwable.getMessage());
                        throwable.printStackTrace();
                    }
                }, new Action0() {
                    @Override
                    public void call() {
                        System.out.println("Done");
                    }
                });


        testClient.start(compiledConfig, compiledConfig.getTarget().createLaunchParameters());

    }


    private Config.Builder addJUnitConfig(String[] classes, Config.Builder config) throws ClassNotFoundException {
        for (String aClass : classes) {
            config.addForceLinkClass(aClass);
        }

        Class clazz = Class.forName(classes[0]);

        config.addClasspathEntry(new File(clazz.getClass().getResource("/").getFile()));
        config.addForceLinkClass(RoboVMTest.class.getCanonicalName());
        config.addClasspathEntry(new RoboVMResolver().resolveArtifact("org.robovm:robovm-surefire-provider:" + Version.getVersion()).asFile());
        return config;
    }

    private void process(ResultObject resultObject, RunNotifier runNotifier, RunListener reporter) {
        switch (resultObject.getResultType()) {
            case TEST_RUN_STARTED:
                try {
                    Logger.log("Test run started");
                    runNotifier.fireTestRunStarted(resultObject.getDescription());
                    reporter.testSetStarting(createReportEntry(resultObject.getDescription()));

                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;

            case TEST_STARTED:
                try {
                    Logger.log(
                            "Test " + resultObject.getDescription().getDisplayName()
                                    + " started");
                    runNotifier.fireTestStarted(resultObject.getDescription());
                    reporter.testSetStarting(createReportEntry(resultObject.getDescription()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;

            case TEST_RUN_FINISHED:
                try {
                    Logger.log("Test run finished");
                    runNotifier.fireTestRunFinished(resultObject.getResult());
                        reporter.testSetCompleted(new SimpleReportEntry("RoboVMTestRuner","RoboVMTestRun"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;

            case TEST_FINISHED:
                try {
                    Logger.log("Test finished");
                    runNotifier.fireTestFinished(resultObject.getDescription());
                    reporter.testSucceeded(createReportEntry(resultObject.getDescription()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;

            case TEST_FAILURE:
                try {
                    Logger.log("Test failed");
                    runNotifier.fireTestFailure(resultObject.getFailure());
                    reporter.testFailed(createReportEntry(resultObject.getFailure()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            default:
                Logger.log("Got result, but don't know what it is");
                break;
        }
    }

    private ReportEntry createReportEntry(Description description) {
        return new SimpleReportEntry(getClassName(description), description.getDisplayName());
    }

    private ReportEntry createReportEntry(Failure failure) {
        Description description = failure.getDescription();

        return SimpleReportEntry.withException(getClassName(description),
                description.getDisplayName(),
                new LegacyPojoStackTraceWriter(getClassName(description), description.getDisplayName(),
                        failure.getException()));

    }

    private String getClassName(Description description) {
        if (description == null) {
            return null;
        }
        String name = description.getDisplayName();

        if (name == null) {
            Description subDescription = description.getChildren().get(0);
            name = subDescription.getDisplayName();
        }
        return name;
    }


    private RunNotifier getRunNotifier(Result result, RoboVMRunListener roboTestListener) {
        RunNotifier runNotifier = new RunNotifier();
        runNotifier.addListener(result.createListener());
        runNotifier.addListener(roboTestListener);
        return runNotifier;
    }
}
