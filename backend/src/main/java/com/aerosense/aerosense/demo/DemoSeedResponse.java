package com.aerosense.aerosense.demo;

public record DemoSeedResponse(
    boolean seeded, long rigCount, long cycleCount, String syntheticNotice) {}
