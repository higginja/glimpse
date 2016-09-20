#version 150

vec2 axisMin( vec4 axisRect )
{
    // Swizzle (xMin, yMin) out of (xMin, xMax, yMin, yMax)
    return axisRect.xz;
}

vec2 axisMax( vec4 axisRect )
{
    // Swizzle (xMax, yMax) out of (xMin, xMax, yMin, yMax)
    return axisRect.yw;
}

vec2 axisSize( vec4 axisRect )
{
    return ( axisMax( axisRect ) - axisMin( axisRect ) );
}

vec4 axisXyToNdc( vec2 xy_AXIS, vec4 axisRect )
{
    vec2 xy_FRAC = ( xy_AXIS - axisMin( axisRect ) ) / axisSize( axisRect );
    return vec4( 2 * xy_FRAC - 1, 0.0, 1.0 );
}

uniform vec4 AXIS_RECT;

in vec2 inXy;
in vec2 inS;

out vec2 vS;

void main( )
{
      gl_Position.xy = axisXyToNdc( inXy, AXIS_RECT );
      vS = inS;
}
