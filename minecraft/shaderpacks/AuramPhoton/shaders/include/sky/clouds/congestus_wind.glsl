#if !defined INCLUDE_SKY_CLOUDS_CONGESTUS_WIND
#define INCLUDE_SKY_CLOUDS_CONGESTUS_WIND

const bool clouds_cumulus_congestus_wind_override
    = CLOUDS_CUMULUS_CONGESTUS_WIND_SPEED >= 0.0
    || CLOUDS_CUMULUS_CONGESTUS_WIND_ANGLE >= 0.0;
const float clouds_cumulus_congestus_wind_speed
    = CLOUDS_CUMULUS_CONGESTUS_WIND_SPEED >= 0.0
    ? CLOUDS_CUMULUS_CONGESTUS_WIND_SPEED : CLOUDS_CUMULUS_WIND_SPEED;
const float clouds_cumulus_congestus_wind_angle
    = (CLOUDS_CUMULUS_CONGESTUS_WIND_ANGLE >= 0.0
       ? CLOUDS_CUMULUS_CONGESTUS_WIND_ANGLE : CLOUDS_CUMULUS_WIND_ANGLE) * degree;
const vec2 clouds_cumulus_congestus_wind_velocity
    = clouds_cumulus_congestus_wind_speed
    * vec2(cos(clouds_cumulus_congestus_wind_angle),
           sin(clouds_cumulus_congestus_wind_angle));

#endif
