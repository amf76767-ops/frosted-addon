package com.manu.frosted.update;

/** The parts of a GitHub release the updater cares about. */
public record ReleaseInfo(String tag, String version, String notes, String assetName, String assetUrl,
                          long assetSize, String sha256, boolean prerelease) {}
