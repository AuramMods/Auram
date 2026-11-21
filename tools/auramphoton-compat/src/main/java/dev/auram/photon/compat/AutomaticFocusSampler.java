package dev.auram.photon.compat;

public interface AutomaticFocusSampler {
    boolean auramPhoton$usesAutomaticFocus();

    void auramPhoton$sampleAutomaticFocus(int preHandDepth, int distantDepth);
}
