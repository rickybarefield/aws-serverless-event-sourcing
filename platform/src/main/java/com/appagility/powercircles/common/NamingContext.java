package com.appagility.powercircles.common;

import com.google.common.collect.Streams;

import java.util.AbstractMap;
import java.util.stream.Collectors;

public class NamingContext {

    public static final String SEPARATOR = "-";
    private final NamingContext parent;
    private final String name;

    public NamingContext(String name) {

        this.parent = null;
        this.name = name;
    }

    static NamingContext fromSeparatedString(NamingContext parent, String name) {

        var firstSeparator = name.indexOf(SEPARATOR);

        if(firstSeparator != -1) {

            var childName = name.substring(0, firstSeparator);

            var child = new NamingContext(parent, childName);

            return fromSeparatedString(child, name.substring(firstSeparator));
        }

        return new NamingContext(parent, name);
    }

    protected NamingContext(NamingContext parent, String name) {

        this.parent = parent;
        this.name = name;
    }

    public NamingContext with(String childName) {

        return new NamingContext(this, childName);
    }

    public String getName() {

        if(parent == null) {
            return name;
        }

        return parent.getName() + SEPARATOR + name;
    }

    NamingContext getRoot() {

        return parent == null ? this : parent.getRoot();
    }

    /**
     *
     * @return removes the root shared with the other NamingContext.
     */
    public String distinctName(NamingContext other) {

        return getName().replace(sharedBeginning(other), "");
    }

    private String sharedBeginning(NamingContext other) {

        var thisNameChars = this.getName().chars().boxed();
        var otherNameChars = other.getName().chars().boxed();

        var streamOfPairs = Streams.zip(
                thisNameChars,
                otherNameChars,
                AbstractMap.SimpleEntry::new);

        var matchingPairs = streamOfPairs.takeWhile((entry) -> entry.getKey().equals(entry.getValue()));

        String result = matchingPairs.map(AbstractMap.SimpleEntry::getKey).map(Character::toString).collect(Collectors.joining());
        return result;
    }
}