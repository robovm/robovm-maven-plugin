# RoboVM-Surefire-Provider


RoboVM surefire provider is a means of running unit tests on the iOS Simulator


## Using RoboVM Surefire Provider


### Compile and install this plugin
Compile robovm-surefire-provider and install into your local maven repository with *'mvn install'*

### Example test class:

```java
@RoboVMTest
public class TestClass {

    @Test
    public void testTest() throws Exception {
        System.err.println("Running testTest");
        assertTrue(1 == 1);
    }
}
```

### Example pom.xml

```xml
   <build>
          <plugins>
              <plugin>
                  <groupId>org.apache.maven.plugins</groupId>
                  <artifactId>maven-surefire-plugin</artifactId>
                  <version>2.17</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.robovm</groupId>
                          <artifactId>robovm-surefire-provider</artifactId>
                          <version>1.0.0-SNAPSHOT</version>
                      </dependency>
                  </dependencies>
              </plugin>
          </plugins>
      </build>  
      <dependencies>
          <dependency>
              <groupId>junit</groupId>
              <artifactId>junit</artifactId>
              <version>4.11</version>
          </dependency>
          <dependency>
              <groupId>org.robovm</groupId>
              <artifactId>robovm-surefire-provider</artifactId>
              <version>1.0.0-SNAPSHOT</version>
          </dependency>
          <dependency>
              <groupId>org.robovm</groupId>
              <artifactId>robovm-junit-bridge</artifactId>
              <version>1.0.0-SNAPSHOT</version>
          </dependency>
          <dependency>
              <groupId>org.robovm</groupId>
              <artifactId>robovm-artifact-resolver</artifactId>
              <version>1.0.0-SNAPSHOT</version>
          </dependency>
      </dependencies>
```


### Running

mvn test


## How does it work?

The surefire provider generates a list of tests to run which have been annotated with the @RoboVMTest annotation.
The classes are then compiled into native code with RoboVM and executed on the simulator or a real device. Results are transferred back 
using GSON over a TCP connection (port 8889).

## Configuration

The framework relies on a valid *'robovm.xml'* file to exist in the resources directory within your modules test directory.

The following system properties are supported:

* robovm.deviceIP -- IP of the device running the tests, default is 'localhost'


## What doesn't work?

Mocking frameworks -- These won't ever work as mocking requires bytecode manipulation. This is, unfortunately, a limitation of RoboVM


## What's Inside ?

Frameworks used:

* [RoboVM] (http://www.robovm.com/)
* [RxJava] (https://github.com/Netflix/RxJava)
* [Mockito] (https://code.google.com/p/mockito)
* [PowerMock] (https://code.google.com/p/powermock)
* [GSon] (https://code.google.com/p/google-gson)