#version 150

uniform float FEATHER_THICKNESS_PX;
uniform vec4 RGBA;
uniform int STIPPLE_ENABLE;
uniform float STIPPLE_FACTOR;
uniform int STIPPLE_PATTERN;

in float gLineThickness_PX;
in vec2 gPosInQuad_PX;

out vec4 outRgba;

void main( )
{
    float tFeatherStart_PX = 0.5*( gLineThickness_PX - FEATHER_THICKNESS_PX );
    float fade = clamp( ( abs( gPosInQuad_PX.t ) - tFeatherStart_PX ) / FEATHER_THICKNESS_PX, 0.0, 1.0 );

    if ( STIPPLE_ENABLE != 0 )
    {
        float bitNum = mod( gPosInQuad_PX.s / STIPPLE_FACTOR, 16 );
        int bitMask = ( 0x1 << int( bitNum ) );
        if ( ( STIPPLE_PATTERN & bitMask ) == 0 )
        {
            discard;
            return;
        }
    }

    float alpha = ( 1.0 - fade ) * RGBA.a;
    outRgba.rgb = RGBA.rgb * alpha;
    outRgba.a = alpha;
}