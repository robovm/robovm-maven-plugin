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

import org.apache.maven.surefire.NonAbstractClassFilter;
import org.apache.maven.surefire.util.ScannerFilter;
import org.robovm.surefire.annotations.RoboVMTest;


import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.apache.maven.surefire.util.ReflectionUtils.tryLoadClass;

public class RoboVMScannerFilter implements ScannerFilter {

    private final Class runWith;
    private final NonAbstractClassFilter nonAbstractClassFilter;

    @Override
    public boolean accept(Class testClass) {
        return isValidJunit4Test(testClass);
    }

    private boolean isValidJunit4Test(Class testClass) {
        if (!nonAbstractClassFilter.accept(testClass)) {
            return false;
        }
        if (isRunWithPresentInClassLoader()) {
            Annotation runWithAnnotation = testClass.getAnnotation(runWith);
            if (runWithAnnotation != null) {
                return true;
            }
        }
        if (!testClass.isAnnotationPresent(RoboVMTest.class)) {
            System.err.println("Class " + testClass.getCanonicalName() + " missing RoboVMTest annotation, skipping");
            return false;
        }
        return lookForAnnotateMethods(testClass);
    }

    private boolean lookForAnnotateMethods(Class testClass) {
        Class classToCheck = testClass;
        while (classToCheck != null) {
            if (checkForTestAnnotatedMethod(classToCheck)) {
                return true;
            }
            classToCheck = classToCheck.getSuperclass();
        }
        return false;
    }

    private boolean checkForTestAnnotatedMethod(Class classToCheck) {
        for (Method m : classToCheck.getDeclaredMethods()) {
            for (Annotation annotation : m.getAnnotations()) {
                if (org.junit.Test.class.isAssignableFrom(annotation.annotationType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isRunWithPresentInClassLoader() {
        return runWith != null;
    }

    public RoboVMScannerFilter(ClassLoader classLoader) {
        this.runWith = tryLoadClass(classLoader, org.junit.runner.RunWith.class.getName());
        this.nonAbstractClassFilter = new NonAbstractClassFilter();
    }

}
