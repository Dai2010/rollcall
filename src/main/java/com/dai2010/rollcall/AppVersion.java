package com.dai2010.rollcall;

/** Provides the version embedded in the executable JAR manifest. */
public final class AppVersion {
    private static final String FALLBACK_VERSION = "0.1.0";

    private AppVersion() {
    }

    public static String current() {
        String implementationVersion = AppVersion.class.getPackage().getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? FALLBACK_VERSION
                : implementationVersion.trim();
    }
}
