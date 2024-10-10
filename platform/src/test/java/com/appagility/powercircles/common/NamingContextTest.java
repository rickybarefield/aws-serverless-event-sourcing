package com.appagility.powercircles.common;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

public class NamingContextTest {

    @Test
    void testDistinctPartSingle() {

        var first = new NamingContext("one").with("two").with("three");
        var second = new NamingContext("one").with("two").with("something_else");

        var distinctPart = first.distinctName(second);

        assertThat(distinctPart, Matchers.equalTo("three"));
    }

    @Test
    void testDistinctPartMultiple() {

        var first = new NamingContext("one").with("a").with("b");
        var second = new NamingContext("one").with("two").with("three");

        var distinctPart = first.distinctName(second);

        assertThat(distinctPart, Matchers.equalTo("a-b"));
    }

}
