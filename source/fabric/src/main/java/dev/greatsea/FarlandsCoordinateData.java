package dev.greatsea;

/** Persistent display origin; this never changes the player's actual world position. */
public interface FarlandsCoordinateData {
    FarlandsCoordinates.Origin greatsea$coordinateOrigin();
    void greatsea$coordinateOrigin(FarlandsCoordinates.Origin origin);
}
