package org.elasticsearch.test;

import com.carrotsearch.randomizedtesting.SeedDecorator;

/*
    very ugly workaround to disable the SecurityManager that Elasticsearch
    now injects in Tests, it is cumbersome to set up the security policy everywhere
    and causes lots of test-failures and strange side-effects, e.g.
    https://issues.gradle.org/browse/GRADLE-2170, which hangs junit test runs in Gradle as a result
 */
public class SecurityManagerWorkaroundSeedDecorator implements SeedDecorator {
    @Override
    public void initialize(Class<?> suiteClass) {
        System.setProperty("tests.security.manager", "false");
    }

    @Override
    public long decorate(long seed) {
        return seed;
    }
}
