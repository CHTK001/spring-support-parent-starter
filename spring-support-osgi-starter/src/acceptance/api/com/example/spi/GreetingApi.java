package com.example.spi;

/** Host-owned API used by the lifecycle acceptance bundles. */
public interface GreetingApi {
    /** Returns a versioned greeting supplied by the active plugin. */
    String greet(String name);
}
