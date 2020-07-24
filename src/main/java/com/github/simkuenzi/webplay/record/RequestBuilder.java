package com.github.simkuenzi.webplay.record;

import java.util.Map;

public interface RequestBuilder extends TestBuilder {
    AssertionBuilder request(String urlPath, String method, Map<String, String> headers, String payload) throws Exception;
}
