#ifndef AURAM_WEATHER_INCLUDED
#define AURAM_WEATHER_INCLUDED

#if defined AURAM_WEATHER_BACKEND && defined OVERWORLD_SHADER && !defined ATUM_SHADER && !defined TWILIGHT_SHADER && !defined MIDNIGHT_SHADER && !defined AETHER_SHADER
#define AURAM_LOCAL_WEATHER
uniform sampler2D auramWeather;
uniform bool auramWeatherActive;
uniform vec3 auramWeatherGrid;
uniform vec4 auramWeatherBounds;
uniform vec3 auramWeatherRoof;
uniform float auramWeatherTime;

vec4 auramSampleWeather(vec2 worldPosition) {
    if (!auramWeatherActive) return vec4(0.0);
    vec2 chunk = worldPosition / 16.0;
    vec2 boundary = min(chunk - auramWeatherBounds.xy, auramWeatherBounds.zw - chunk);
    float edge = smoothstep(0.0, 2.0, min(boundary.x, boundary.y));
    if (edge <= 0.0) return vec4(0.0);
    float side = auramWeatherGrid.z;
    vec2 cell = clamp(chunk - auramWeatherGrid.xy, vec2(0.5), vec2(side - 0.5));
    vec2 scale = vec2(max(128.0, side), side * 3.0 + 128.0);
    // The timing plane follows the nearest cell; field amplitudes interpolate across cell centers.
    vec2 clockCell = floor(cell / 16.0) * 16.0 + 8.5;
    vec2 timing = texture2DLod(auramWeather, (clockCell + vec2(0.0, side * 2.0)) / scale, 0.0).rg;
    float blend = clamp((auramWeatherTime - timing.x) * timing.y, 0.0, 1.0);
    vec4 previous = texture2DLod(auramWeather, cell / scale, 0.0);
    vec4 latest = texture2DLod(auramWeather, (cell + vec2(0.0, side)) / scale, 0.0);
    return mix(previous, latest, blend) * edge;
}

bool auramRainRoof(vec3 position) {
    vec2 offset = floor(position.xz - auramWeatherRoof.xy);
    if (auramWeatherRoof.z == 0.0 || any(lessThan(offset, vec2(0.0))) || any(greaterThanEqual(offset, vec2(128.0)))) {
        return false;
    }
    float side = auramWeatherGrid.z;
    vec2 uv = (offset + vec2(0.5, side * 3.0 + 0.5)) / vec2(max(128.0, side), side * 3.0 + 128.0);
    return position.y < texture2DLod(auramWeather, uv, 0.0).r;
}

vec3 auramWeatherWaving(vec3 base, vec3 position, float time) {
    vec2 wind = auramSampleWeather(position.xz).xy;
    float strength = length(wind);
    vec2 phase = position.xz * vec2(0.75, 0.53) + position.y * 0.23;
    float gust = 0.80 + 0.20 * sin(time * (4.4 + 1.9 * strength) + phase.x + phase.y);
    return base * (1.0 + strength * 0.9) + vec3(wind.x, 0.0, wind.y) * (0.22 * gust);
}

float auramRainHash(vec2 cell) {
    vec3 p = fract(vec3(cell.x, cell.y, cell.x) * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float auramRainVolume(vec3 origin, vec3 ray, float distance) {
    if (!auramWeatherActive || distance < 0.9) return 0.0;
    float opacity = 0.0;
    // Each band represents a longer stretch of air as distance grows.
    for (int band = 0; band < 7; band++) {
        float layer = float(band);
        float along = 1.5 * pow(2.20, layer);
        if (along >= distance - 0.15) break;
        vec3 position = origin + ray * along;
        vec4 weather = auramSampleWeather(position.xz);
        bool snow = weather.w < 0.0;
        float intensity = abs(weather.w);
        if (intensity < 0.015 || auramRainRoof(position)) continue;

        float scale = max(0.32, along * 0.012);
        vec2 tilted = (position.xz + position.y * weather.xy * (snow ? 0.72 : 0.48)) / scale;
        if (snow) {
            tilted += vec2(sin(position.y * 0.27 + auramWeatherTime), cos(position.y * 0.33)) * 0.28;
        }
        vec2 cell = floor(tilted);
        float rng = auramRainHash(cell + vec2(layer * 37.0, 0.0));
        vec2 offset = vec2(rng, auramRainHash(cell - vec2(0.0, 51.0 + layer))) * 0.54 + 0.23;
        float dropRadius = snow ? 0.12 : 0.056;
        float drop = 1.0 - smoothstep(dropRadius * 0.40, dropRadius, length(fract(tilted) - offset));
        float falling = fract((position.y + auramWeatherTime * (snow ? 1.7 : 10.0)) / (scale * 9.0) + rng);
        float streak = smoothstep(0.03, 0.07, falling)
                * (1.0 - smoothstep(snow ? 0.11 : 0.25, snow ? 0.17 : 0.39, falling));
        float visible = 1.0 - smoothstep(intensity * 0.85, intensity * 0.85 + 0.15, rng);
        opacity += drop * streak * visible * mix(0.24, 0.095, layer / 6.0);
    }
    return clamp(opacity, 0.0, 0.75);
}
#endif

#endif
