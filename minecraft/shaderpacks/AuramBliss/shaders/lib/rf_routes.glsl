#ifndef AURAM_RF_ROUTES_GLSL
#define AURAM_RF_ROUTES_GLSL

int rfFeedingFace(uint rates[6], int otherFace) {
    int bestFace = -1;
    uint bestRate = 0u;
    for (int face = 0; face < 6; face++) {
        uint rate = rates[face];
        if (face == otherFace || rate == 0u) continue;
        if (rate > bestRate || (rate == bestRate && face == (otherFace ^ 1))) {
            bestRate = rate;
            bestFace = face;
        }
    }
    return bestFace;
}

int rfFindRoutes(uvec3 data, out ivec4 routes[12]) {
    uint incoming[6], outgoing[6];
    for (int axis = 0; axis < 3; axis++) {
        uint traffic = data[axis];
        incoming[axis * 2] = (traffic >> 8u) & 0xffu;
        outgoing[axis * 2] = traffic & 0xffu;
        incoming[axis * 2 + 1] = (traffic >> 16u) & 0xffu;
        outgoing[axis * 2 + 1] = traffic >> 24u;
    }

    // The field stores traffic per face. At forks, start each outgoing strand
    // from the strongest compatible input and also draw any remaining inputs.
    int count = 0;
    int usedInputs = 0;
    for (int outFace = 0; outFace < 6; outFace++) {
        if (outgoing[outFace] == 0u) continue;
        int inFace = rfFeedingFace(incoming, outFace);
        uint inRate = 0u;
        if (inFace >= 0) {
            usedInputs |= 1 << inFace;
            inRate = incoming[inFace];
        }
        routes[count++] = ivec4(inFace, int(inRate), outFace, int(outgoing[outFace]));
    }
    for (int inFace = 0; inFace < 6; inFace++) {
        if (incoming[inFace] == 0u || (usedInputs & (1 << inFace)) != 0) continue;
        int outFace = rfFeedingFace(outgoing, inFace);
        uint outRate = outFace < 0 ? 0u : outgoing[outFace];
        routes[count++] = ivec4(inFace, int(incoming[inFace]), outFace, int(outRate));
    }
    return count;
}

#endif
