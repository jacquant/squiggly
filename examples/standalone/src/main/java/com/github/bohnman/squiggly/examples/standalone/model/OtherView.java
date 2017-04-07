package com.github.bohnman.squiggly.examples.standalone.model;

import com.github.bohnman.squiggly.view.PropertyView;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(FIELD)
@Retention(RUNTIME)
@Documented
@PropertyView({"other"})
public @interface OtherView {
}
