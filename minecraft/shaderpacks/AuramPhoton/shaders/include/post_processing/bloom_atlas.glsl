#ifndef INCLUDE_BLOOM_ATLAS
#define INCLUDE_BLOOM_ATLAS

#define bloom_tile_scale(i) 0.5 * exp2(-(i))
#define bloom_tile_offset(i) \
    vec2(1.0 - exp2(-(i)), float((i) & 1) * (1.0 - 0.5 * exp2(-(i))))

// The regular Gaussian inset needs four texel intervals in the deepest tile.
// Its top-aligned rows round up, while its width can round down.
bool bloom_small_atlas(vec2 resolution) {
    return resolution.x < 256.0 || resolution.y <= 192.0;
}

// The small atlas rounds its raster edges to whole pixels. Each level has
// its own columns and can keep at least one row even at very short heights.
vec4 bloom_tile_edges(int index, vec2 resolution) {
    vec2 offset = bloom_tile_offset(index);
    float scale = bloom_tile_scale(index);
    vec2 lower = floor(resolution * offset + 0.5);
    vec2 upper = floor(resolution * (offset + scale) + 0.5);
    lower.y = min(lower.y, resolution.y - 1.0);
    upper.y = max(upper.y, lower.y + 1.0);
    if (index == 0) upper.x = max(upper.x, 1.0);
    return vec4(lower, upper);
}

int bloom_last_tile(float width) {
    int last = clamp(int(floor(log2(width))) - 1, 0, 5);
    // A level smaller than one nominal pixel can still have a raster column.
    for (int i = last + 1; i <= 5; ++i) {
        vec4 edges = bloom_tile_edges(i, vec2(width, 1.0));
        if (edges.x == edges.z) break;
        last = i;
    }
    return last;
}

vec4 bloom_tile_centers(vec4 edges, vec2 pixel_size) {
    return (edges + vec4(0.5, 0.5, -0.5, -0.5)) * pixel_size.xyxy;
}

int bloom_tile_at_x(float x, float width) {
    int last_tile = bloom_last_tile(width);
    int index = 0;
    for (int i = 1; i <= last_tile; ++i) {
        float left_edge = floor(width * (1.0 - exp2(-i)) + 0.5);
        if (x < left_edge) break;
        index = i;
    }
    return index;
}

vec2 bloom_vertex_position(vec2 vertex, int index, vec2 resolution) {
    if (bloom_small_atlas(resolution)) {
        if (index > bloom_last_tile(resolution.x)) return vec2(-1.0);
        vec4 edges = bloom_tile_edges(index, resolution);
        return mix(edges.xy, edges.zw, vertex) / resolution;
    }
    return vertex * bloom_tile_scale(index) + bloom_tile_offset(index);
}

#endif
