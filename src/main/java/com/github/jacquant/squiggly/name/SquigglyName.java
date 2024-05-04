package com.github.jacquant.squiggly.name;

public interface SquigglyName {

    String getName();

    String getRawName();

    int match(String name);
}
