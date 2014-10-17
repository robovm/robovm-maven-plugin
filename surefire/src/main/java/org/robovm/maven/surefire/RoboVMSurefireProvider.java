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
package org.robovm.maven.surefire;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.maven.surefire.common.junit4.JUnit4RunListener;
import org.apache.maven.surefire.common.junit4.JUnit4RunListenerFactory;
import org.apache.maven.surefire.common.junit4.JUnit4TestChecker;
import org.apache.maven.surefire.providerapi.AbstractProvider;
import org.apache.maven.surefire.providerapi.ProviderParameters;
import org.apache.maven.surefire.report.ConsoleOutputCapture;
import org.apache.maven.surefire.report.ConsoleOutputReceiver;
import org.apache.maven.surefire.report.PojoStackTraceWriter;
import org.apache.maven.surefire.report.ReportEntry;
import org.apache.maven.surefire.report.ReporterException;
import org.apache.maven.surefire.report.ReporterFactory;
import org.apache.maven.surefire.report.RunListener;
import org.apache.maven.surefire.report.SimpleReportEntry;
import org.apache.maven.surefire.shade.org.apache.maven.shared.utils.io.SelectorUtils;
import org.apache.maven.surefire.suite.RunResult;
import org.apache.maven.surefire.testset.TestSetFailedException;
import org.apache.maven.surefire.util.RunOrderCalculator;
import org.apache.maven.surefire.util.ScanResult;
import org.apache.maven.surefire.util.TestsToRun;
import org.apache.maven.surefire.util.internal.StringUtils;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.robovm.compiler.AppCompiler;
import org.robovm.compiler.Version;
import org.robovm.compiler.config.Arch;
import org.robovm.compiler.config.Config;
import org.robovm.compiler.config.Config.Home;
import org.robovm.compiler.config.OS;
import org.robovm.compiler.log.Logger;
import org.robovm.compiler.target.LaunchParameters;
import org.robovm.compiler.target.ios.ProvisioningProfile;
import org.robovm.compiler.target.ios.SigningIdentity;
import org.robovm.junit.client.TestClient;
import org.robovm.maven.resolver.RoboVMResolver;

public class RoboVMSurefireProvider extends AbstractProvider {
    private final static String PROP_LOG_DEBUG = "robovm.test.enableDebugLogging";
    private final static String PROP_SERVER_DEBUG = "robovm.test.enableServerDebugging";
    private final static String PROP_OS = "robovm.test.os";
    private final static String PROP_ARCH = "robovm.test.arch";
    private final static String PROP_CONFIG_FILE = "robovm.test.configFile";
    private final static String PROP_PROPERTIES_FILE = "robovm.test.propertiesFile";
    private final static String PROP_IOS_SIGNING_IDENTITY = "robovm.test.iosSignIdentity";
    private final static String PROP_IOS_PROVISIONING_PROFILE = "robovm.test.iosProvisioningProfile";
    private final static String PROP_IOS_SKIP_SIGNING = "robovm.test.iosSkipSigning";

    private final ClassLoader testClassLoader;
    private final List<org.junit.runner.notification.RunListener> customRunListeners;
    private final JUnit4TestChecker jUnit4TestChecker;
    private final String requestedTestMethod;
    private final ProviderParameters providerParameters;
    private final RunOrderCalculator runOrderCalculator;
    private final ScanResult scanResult;
    private TestsToRun testsToRun;

    public RoboVMSurefireProvider(ProviderParameters booterParameters) {
        providerParameters = booterParameters;
        testClassLoader = booterParameters.getTestClassLoader();
        scanResult = booterParameters.getScanResult();
        runOrderCalculator = booterParameters.getRunOrderCalculator();
        customRunListeners = JUnit4RunListenerFactory.
                createCustomListeners(booterParameters.getProviderProperties().getProperty("listener"));
        jUnit4TestChecker = new JUnit4TestChecker(testClassLoader);
        requestedTestMethod = booterParameters.getTestRequest().getRequestedTestMethod();
    }

    @Override
    public Iterator<?> getSuites() {
        testsToRun = scanClassPath();
        return testsToRun.iterator();
    }

    private TestsToRun scanClassPath() {
        final TestsToRun scannedClasses = scanResult.applyFilter(jUnit4TestChecker, testClassLoader);
        return runOrderCalculator.orderTestClasses(scannedClasses);
    }

    @Override
    public RunResult invoke(Object forkTestSet) throws TestSetFailedException, ReporterException {
        if (testsToRun == null) {
            if (forkTestSet instanceof TestsToRun) {
                testsToRun = (TestsToRun) forkTestSet;
            } else if (forkTestSet instanceof Class) {
                testsToRun = TestsToRun.fromClass((Class<?>) forkTestSet);
            } else {
                testsToRun = scanClassPath();
            }
        }

        final ReporterFactory reporterFactory = providerParameters.getReporterFactory();
        final RunListener reporter = reporterFactory.createReporter();
        ConsoleOutputCapture.startCapture((ConsoleOutputReceiver) reporter);
        final JUnit4RunListener jUnit4TestSetReporter = new JUnit4RunListener(reporter);
        Result result = new Result();
        final RunNotifier runNotifier = getRunNotifier(jUnit4TestSetReporter, result, customRunListeners);

        TestClient testClient = new TestClient();
        testClient.setRunListener(new org.junit.runner.notification.RunListener() {
            public void testRunStarted(Description description) throws Exception {
                runNotifier.fireTestRunStarted(description);
            }
            public void testRunFinished(Result result) throws Exception {
                runNotifier.fireTestRunFinished(result);
            }
            public void testStarted(Description description) throws Exception {
                runNotifier.fireTestStarted(description);
            }
            public void testFinished(Description description) throws Exception {
                runNotifier.fireTestFinished(description);
            }
            public void testFailure(Failure failure) throws Exception {
                runNotifier.fireTestFailure(failure);
            }
            public void testAssumptionFailure(Failure failure) {
                runNotifier.fireTestAssumptionFailed(failure);
            }
            public void testIgnored(Description description) throws Exception {
                runNotifier.fireTestIgnored(description);
            }
        });

        Process process = null;
        try {
            Config config = testClient.configure(createConfig()).build();
            config.getLogger().info("Building RoboVM tests for: %s (%s)", config.getOs(), config.getArch());
            config.getLogger().info("This could take a while, especially the first time round");
            AppCompiler appCompiler = new AppCompiler(config);
            appCompiler.compile();
            
            LaunchParameters launchParameters = config.getTarget().createLaunchParameters();
            if (Boolean.getBoolean(PROP_SERVER_DEBUG)) {
                launchParameters.getArguments().add("-rvm:Drobovm.debug=true");
            }
            process = appCompiler.launchAsync(launchParameters);
            
            runNotifier.fireTestRunStarted(null);
            for (Class<?> clazz : testsToRun) {
                executeTestSet(testClient, clazz, reporter, runNotifier);
            }
            testClient.terminate();
            process.waitFor();
            runNotifier.fireTestRunFinished(result);
            JUnit4RunListener.rethrowAnyTestMechanismFailures(result);
        } catch (Throwable t) {
            throw new RuntimeException("RoboVM test run failed", t);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

        return reporterFactory.close();
    }

    private String[] testToRunToClassPatterns(Class<?> clazz) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.isBlank(this.requestedTestMethod)) {
            // Copied from JUnit4Provider
            String actualTestMethod = getMethod(clazz, this.requestedTestMethod);
            String[] testMethods = StringUtils.split(actualTestMethod, "+");
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                for (String testMethod : testMethods) {
                    if (SelectorUtils.match(testMethod, method.getName())) {
                        result.add(clazz.getName() + "#" + method.getName());
                    }
                }
            }
        } else {
            result.add(clazz.getName());
        }
        return result.toArray(new String[result.size()]);
    }

    private void executeTestSet(TestClient testClient, Class<?> clazz, RunListener reporter, RunNotifier listeners)
            throws ReporterException, TestSetFailedException {

        final ReportEntry report = new SimpleReportEntry(this.getClass().getName(), clazz.getName());
        reporter.testSetStarting(report);

        try {
            testClient.runTests(testToRunToClassPatterns(clazz)).flush();
        } catch (Throwable e) {
            reporter.testError(SimpleReportEntry.withException(report.getSourceName(), report.getName(),
                    new PojoStackTraceWriter(report.getSourceName(),
                            report.getName(), e)));
        } finally {
            reporter.testSetCompleted(report);
        }
    }

    private Config.Builder createConfig() throws IOException {
        Config.Builder configBuilder = new Config.Builder();
        
        Logger logger = new Logger() {
            public void debug(String format, Object... args) {
                if (Boolean.getBoolean(PROP_LOG_DEBUG)) {
                    providerParameters.getConsoleLogger().info("[DEBUG] " + String.format(format, args) + "\n");
                }
            }
            public void info(String format, Object... args) {
                providerParameters.getConsoleLogger().info("[INFO] " + String.format(format, args) + "\n");
            }
            public void warn(String format, Object... args) {
                providerParameters.getConsoleLogger().info("[WARNING] " + String.format(format, args) + "\n");
            }
            public void error(String format, Object... args) {
                providerParameters.getConsoleLogger().info("[ERROR] " + String.format(format, args) + "\n");
            }
        };
        configBuilder.logger(logger);
        
        RoboVMResolver roboVMResolver = new RoboVMResolver();
        roboVMResolver.setLogger(new org.robovm.maven.resolver.Logger() {
            public void info(String logLine) {
                providerParameters.getConsoleLogger().info("[INFO] " + logLine + "\n");
            }
            public void debug(String logLine) {
                if (Boolean.getBoolean(PROP_LOG_DEBUG)) {
                    providerParameters.getConsoleLogger().info("[DEBUG] " + logLine + "\n");
                }
            }
        });
        Home home = null;
        if (System.getenv("ROBOVM_DEV_ROOT") != null) {
            home = Home.find();
        } else {
            home = new Home(roboVMResolver.resolveAndUnpackRoboVMDistArtifact(Version.getVersion()));
        }
        configBuilder.home(home);

        String basedir = System.getProperty("basedir");
        File propertiesFile = null;
        if (System.getProperties().containsKey(PROP_PROPERTIES_FILE)) {
            propertiesFile = new File(System.getProperty(PROP_PROPERTIES_FILE));
            if (!propertiesFile.exists()) {
                throw new FileNotFoundException("Failed to find specified propertiesFile: " + propertiesFile.getAbsolutePath());
            }
        } else {
            propertiesFile = new File(basedir, "robovm-test.properties");
            if (!propertiesFile.exists()) {
                propertiesFile = new File(basedir, "robovm.properties");
            }
        }
        if (propertiesFile.exists()) {
            configBuilder.addProperties(propertiesFile);
        }
        File configFile = null;
        if (System.getProperties().containsKey(PROP_CONFIG_FILE)) {
            configFile = new File(System.getProperty(PROP_CONFIG_FILE));
            if (!configFile.exists()) {
                throw new FileNotFoundException("Failed to find specified configFile: " + configFile.getAbsolutePath());
            }
        } else {
            configFile = new File(basedir, "robovm-test.xml");
            if (!configFile.exists()) {
                configFile = new File(basedir, "robovm.xml");
            }
        }
        if (configFile.exists()) {
            configBuilder.read(configFile);
        }
        
        if (System.getProperty(PROP_OS) != null) {
            configBuilder.os(OS.valueOf(System.getProperty(PROP_OS)));
        }
        if (System.getProperty(PROP_ARCH) != null) {
            configBuilder.arch(Arch.valueOf(System.getProperty(PROP_ARCH)));
        }
        if (Boolean.getBoolean(PROP_IOS_SKIP_SIGNING)) {
            configBuilder.iosSkipSigning(true);
        } else {
            if (System.getProperty(PROP_IOS_SIGNING_IDENTITY) != null) {
                String iosSignIdentity = System.getProperty(PROP_IOS_SIGNING_IDENTITY);
                logger.debug("Using explicit iOS Signing identity: " + iosSignIdentity);
                configBuilder.iosSignIdentity(SigningIdentity.find(
                        SigningIdentity.list(), iosSignIdentity));
            }
            if (System.getProperty(PROP_IOS_PROVISIONING_PROFILE) != null) {
                String iosProvisioningProfile = System.getProperty(PROP_IOS_PROVISIONING_PROFILE);
                logger.debug("Using explicit iOS provisioning profile: " + iosProvisioningProfile);
                configBuilder.iosProvisioningProfile(ProvisioningProfile.find(
                        ProvisioningProfile.list(), iosProvisioningProfile));
            }
        }
        
        // Ignore any classpath entries in the loaded robovm.xml file.
        configBuilder.clearClasspathEntries();
        
        configBuilder.addClasspathEntry(roboVMResolver.resolveArtifact("org.robovm:robovm-junit-server:" + Version.getVersion()).asFile());
        for (String p : System.getProperty("java.class.path").split(File.pathSeparator)) {
            configBuilder.addClasspathEntry(new File(p));
        }

        if (testClassLoader.getClass().getName().equals("org.apache.maven.surefire.booter.IsolatedClassLoader")) {
            // Not fork mode. We need to get to the URLs of the IsolatedClassLoader.
            // Only way is to use reflection.
            try {
                Field f = testClassLoader.getClass().getDeclaredField("urls");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                Collection<URL> urls = (Collection<URL>) f.get(testClassLoader);
                for (URL url :urls) {
                    File file = new File(url.getFile());
                    if (file.isDirectory() || file.getName().toLowerCase().matches(".*\\.(jar|zip)$")) {
                        configBuilder.addClasspathEntry(file);
                    }
                }
            } catch (Throwable t) {
                throw new RuntimeException("Failed to get classpath URLs from IsolatedClassLoader using reflection", t);
            }
        } else {
            Properties props = providerParameters.getProviderProperties();
            for (int i = 0; true; i++) {
                String path = props.getProperty("classPathUrl." + i);
                if (path == null) {
                    break;
                }
                configBuilder.addClasspathEntry(new File(path));
            }
        }
        
        configBuilder.addForceLinkClass(TestClient.SERVER_CLASS_NAME);
        for (Class<?> c : testsToRun.getLocatedClasses()) {
            configBuilder.addForceLinkClass(c.getName());
        }
        configBuilder.skipInstall(true);

        return configBuilder;
    }
    
    private RunNotifier getRunNotifier(org.junit.runner.notification.RunListener main, Result result,
            List<org.junit.runner.notification.RunListener> others) {

        RunNotifier notifier = new RunNotifier();
        notifier.addListener(main);
        notifier.addListener(result.createListener());
        for (org.junit.runner.notification.RunListener listener : others) {
            notifier.addListener(listener);
        }
        return notifier;
    }
    
    /**
     * RoboVM note: Copied from JUnit4Provider and cleaned up.
     * 
     * This method retrieves test methods from String like 
     * "com.xx.ImmutablePairTest#testBasic,com.xx.StopWatchTest#testLang315+testStopWatchSimpleGet"
     * <br>
     * and we need to think about cases that 2 or more method in 1 class. we should choose the correct method
     *
     * @param testClass     the testclass
     * @param testMethodStr the test method string
     * @return a string ;)
     */
    private static String getMethod(Class<?> testClass, String testMethodStr) {
        String className = testClass.getName();

        if (!testMethodStr.contains("#") && !testMethodStr.contains(",")) {// the original way
            return testMethodStr;
        }
        testMethodStr += ",";// for the bellow split code
        int beginIndex = testMethodStr.indexOf(className);
        int endIndex = testMethodStr.indexOf(",", beginIndex);
        String classMethodStr =
                testMethodStr.substring(beginIndex, endIndex);// String like
                                                              // "StopWatchTest#testLang315"

        int index = classMethodStr.indexOf('#');
        if (index >= 0) {
            return classMethodStr.substring(index + 1, classMethodStr.length());
        }
        return null;
    }
}
