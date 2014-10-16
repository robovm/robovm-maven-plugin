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
package org.robovm;

import org.robovm.junit.deps.com.google.gson.GsonBuilder;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.robovm.junit.protocol.AtomicIntegerTypeAdapter;
import org.robovm.junit.protocol.DescriptionTypeAdapter;
import org.robovm.junit.protocol.FailureTypeAdapter;
import org.robovm.junit.protocol.ResultObject;

import java.util.concurrent.atomic.AtomicInteger;

import static junit.framework.Assert.assertTrue;

public class RoboVMSurefireGSONTest {

    @Test
    public void testResultObjectSerialization() {
        ResultObject resultObject = new ResultObject();
        Description description = Description.createSuiteDescription("test");
        Description subDescription = Description.createSuiteDescription("test2");

        description.addChild(subDescription);

        resultObject.setResult(new Result());
        resultObject.setDescription(description);
        resultObject.setFailure(
            new Failure(Description.createSuiteDescription("test"), new RuntimeException("error")));

        String jsonString = new GsonBuilder()
            .registerTypeAdapter(Description.class, new DescriptionTypeAdapter())
            .registerTypeAdapter(AtomicInteger.class, new AtomicIntegerTypeAdapter())
            .registerTypeAdapter(Failure.class, new FailureTypeAdapter())
            .create()
            .toJson(resultObject);
        assertTrue(jsonString != null);

    }

    @Test
    public void testResultObjectDeserialization() {

        String jsonString = "{\"resultType\":2,\"description\":{\"display_name\":\"null\",\"sub_description\":[{\"display_name\":\"com.example.TestTest\",\"sub_description\":[{\"display_name\":\"testTest(com.example.TestTest)\",\"sub_description\":[]}]}]}}";

        ResultObject deserializedResultObject = new GsonBuilder()
            .registerTypeAdapter(Description.class, new DescriptionTypeAdapter())
            .create()
            .fromJson(jsonString, ResultObject.class);

        assertTrue(deserializedResultObject.getDescription().getChildren().get(0).getDisplayName()
            .equals("com.example.TestTest"));

    }

    @Test
    public void testResultObjectDeserialization2() {

        String jsonString = " {\"resultType\":3,\"result\":{\"fStartTime\":1405232992842,\"fRunTime\":6,\"fFailures\":[],\"fIgnoreCount\":0,\"fCount\":1}}";

        ResultObject deserializedResultObject = new GsonBuilder()
            .registerTypeAdapter(Description.class, new DescriptionTypeAdapter())
            .registerTypeAdapter(AtomicInteger.class, new AtomicIntegerTypeAdapter())
            .create()
            .fromJson(jsonString, ResultObject.class);

        assertTrue(deserializedResultObject.getResult() != null);
        assertTrue(deserializedResultObject.getResult().getRunTime() == 6);
        assertTrue(deserializedResultObject.getResult().getRunCount() == 1);
    }

    @Test
    public void testResultObjectDeserialization3() {
        ResultObject resultObject = new ResultObject();
        Description description = Description.createSuiteDescription("test");
        Description subDescription = Description.createSuiteDescription("test2");

        description.addChild(subDescription);

        resultObject.setResult(new Result());
        resultObject.setDescription(description);
        resultObject.setFailure(
            new Failure(Description.createSuiteDescription("test"), new RuntimeException("error")));

        String jsonString = new GsonBuilder()
            .registerTypeAdapter(Description.class, new DescriptionTypeAdapter())
            .registerTypeAdapter(AtomicInteger.class, new AtomicIntegerTypeAdapter())
            .registerTypeAdapter(Failure.class, new FailureTypeAdapter())
            .create()
            .toJson(resultObject);
        assertTrue(jsonString != null);

        ResultObject resultObject2 = new GsonBuilder()
            .registerTypeAdapter(Description.class, new DescriptionTypeAdapter())
            .registerTypeAdapter(AtomicInteger.class, new AtomicIntegerTypeAdapter())
            .registerTypeAdapter(Failure.class, new FailureTypeAdapter())
            .create()
            .fromJson(jsonString, ResultObject.class);

        assertTrue(resultObject2 != null);

    }

}
