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

import org.apache.maven.surefire.report.RunListener;
import org.apache.maven.surefire.report.SimpleReportEntry;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.robovm.surefire.internal.Logger;

import java.io.IOException;

public class RoboVMRunListener extends org.junit.runner.notification.RunListener {

    private final RunListener reporter;

    public RoboVMRunListener(final RunListener reporter) throws IOException {
        this.reporter = reporter;
    }


    @Override
    public void testIgnored(Description description) throws Exception {
        SimpleReportEntry report = SimpleReportEntry.ignored("Test Ignored", description.getDisplayName(), "");
        reporter.testSkipped(report);
    }

    @Override
    public void testRunStarted(Description description) throws Exception {
        super.testRunStarted(description);
    }

    @Override
    public void testRunFinished(Result result) throws Exception {

        super.testRunFinished(result);
        Logger.log("Fired finished");
        Logger.log("Server socket closed");
    }

    @Override
    public void testStarted(Description description) throws Exception {
        super.testStarted(description);
    }

    @Override
    public void testFinished(Description description) throws Exception {
        super.testFinished(description);
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        super.testFailure(failure);
    }

}
