package dev.autostore.core;

/** The storage trip borrows control from exactly one explicitly observed native job. */
public enum NativeJob {
    NONE, MINE, CLEAR_AREA;
    public boolean active(boolean mine, boolean builder) {
        return switch(this) {case MINE -> mine; case CLEAR_AREA -> builder; case NONE -> false;};
    }
}
