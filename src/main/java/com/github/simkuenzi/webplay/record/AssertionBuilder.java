package com.github.simkuenzi.webplay.record;

public interface AssertionBuilder extends RequestBuilder, TestBuilder {
    AssertionBuilder assertion(String expectedAttrName, String expectedAttrValue, String selector) throws Exception;
    AssertionBuilder assertion(String expectedText, String selector) throws Exception;
}
