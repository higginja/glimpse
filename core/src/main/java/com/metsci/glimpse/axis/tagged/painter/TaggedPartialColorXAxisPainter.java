/*
 * Copyright (c) 2016, Metron, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Metron, Inc. nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL METRON, INC. BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.metsci.glimpse.axis.tagged.painter;

import static com.metsci.glimpse.axis.tagged.Tag.TEX_COORD_ATTR;
import static javax.media.opengl.GL.GL_ARRAY_BUFFER;
import static javax.media.opengl.GL.GL_DYNAMIC_DRAW;
import static javax.media.opengl.GL.GL_TRIANGLES;

import java.nio.FloatBuffer;
import java.util.List;

import javax.media.opengl.GL;
import javax.media.opengl.GL3;

import com.metsci.glimpse.axis.Axis1D;
import com.metsci.glimpse.axis.painter.label.AxisLabelHandler;
import com.metsci.glimpse.axis.tagged.Tag;
import com.metsci.glimpse.axis.tagged.TaggedAxis1D;
import com.metsci.glimpse.context.GlimpseBounds;
import com.metsci.glimpse.context.GlimpseContext;
import com.metsci.glimpse.gl.GLStreamingBuffer;
import com.metsci.glimpse.gl.util.GLUtils;

/**
 * A horizontal (x) axis painter which displays positions of tags in addition
 * to tick marks and labels. A color scale is also displayed which stretches
 * between specified tags.
 *
 * Tags which are to act as anchor points for the color scale must be given
 * the {@link com.metsci.glimpse.axis.tagged.Tag#TEX_COORD_ATTR} attribute.
 * The value of this attribute should be between 0.0 and 1.0 and indicates
 * where along the color scale this tag should be anchored. For example, a
 * tag with value 20.0 and TEX_COORD_ATTR attribute value 0.5 would indicate
 * that the color half-way along the color scale should map to value 20.0
 * on the axis.
 *
 * This axis must be added to a
 * {@link com.metsci.glimpse.layout.GlimpseAxisLayout1D} whose associated
 * axis is a {@link com.metsci.glimpse.axis.tagged.TaggedAxis1D}.
 *
 * @author ulman
 */
public class TaggedPartialColorXAxisPainter extends TaggedColorXAxisPainter
{
    protected GLStreamingBuffer vertexCoords;
    protected GLStreamingBuffer textureCoords;

    public TaggedPartialColorXAxisPainter( AxisLabelHandler ticks )
    {
        super( ticks );

        this.vertexCoords = new GLStreamingBuffer( GL_ARRAY_BUFFER, GL_DYNAMIC_DRAW, 20 );
        this.textureCoords = new GLStreamingBuffer( GL_ARRAY_BUFFER, GL_DYNAMIC_DRAW, 20 );
    }

    @Override
    protected void paintColorScale( GlimpseContext context )
    {
        Axis1D axis = getAxis1D( context );

        if ( colorTexture != null && axis instanceof TaggedAxis1D )
        {
            TaggedAxis1D taggedAxis = ( TaggedAxis1D ) axis;
            GlimpseBounds bounds = getBounds( context );
            GL3 gl = context.getGL( ).getGL3( );

            int height = bounds.getHeight( );
            int width = bounds.getWidth( );

            int count = updateCoordinateBuffers( gl, taggedAxis, width, height );

            float y1 = getColorBarMinY( height );
            float y2 = getColorBarMaxY( height );

            pathOutline.clear( );
            pathOutline.addRectangle( 0.5f, y1, width, y2 );

            GLUtils.enableStandardBlending( gl );
            try
            {
                if ( count > 0 )
                {
                    // draw color scale
                    progTex.begin( gl );
                    try
                    {
                        progTex.setPixelOrtho( gl, bounds );

                        progTex.draw( gl, GL_TRIANGLES, colorTexture, vertexCoords, textureCoords, 0, count );
                    }
                    finally
                    {
                        progTex.end( gl );
                    }
                }

                // draw outline box
                progOutline.begin( gl );
                try
                {
                    progOutline.setPixelOrtho( gl, bounds );
                    progOutline.setViewport( gl, bounds );

                    progOutline.draw( gl, style, pathOutline );
                }
                finally
                {
                    progOutline.end( gl );
                }
            }
            finally
            {
                gl.glDisable( GL.GL_BLEND );
            }
        }
    }

    protected int updateCoordinateBuffers( GL gl, TaggedAxis1D taggedAxis, int width, int height )
    {
        List<Tag> tags = taggedAxis.getSortedTags( );
        int size = tags.size( );

        if ( size <= 1 ) return 0;

        FloatBuffer v = vertexCoords.mapFloats( gl, 12 * ( size - 1 ) );
        FloatBuffer t = textureCoords.mapFloats( gl, 6 * ( size - 1 ) );

        float y1 = getColorBarMinY( height );
        float y2 = getColorBarMaxY( height );

        float prevVertexCoord = 0;
        float prevTextureCoord = 0;
        boolean init = false;

        int count = 0;
        for ( Tag tag : tags )
        {
            if ( tag.hasAttribute( TEX_COORD_ATTR ) )
            {
                float textureCoord = tag.getAttributeFloat( TEX_COORD_ATTR );
                float vertexCoord = ( float ) taggedAxis.valueToScreenPixel( tag.getValue( ) );

                if ( init )
                {
                    v.put( prevVertexCoord ).put( y1 );
                    v.put( vertexCoord ).put( y2 );
                    v.put( vertexCoord ).put( y1 );

                    v.put( vertexCoord ).put( y2 );
                    v.put( prevVertexCoord ).put( y2 );
                    v.put( prevVertexCoord ).put( y1 );

                    t.put( prevTextureCoord );
                    t.put( textureCoord );
                    t.put( textureCoord );

                    t.put( textureCoord );
                    t.put( prevTextureCoord );
                    t.put( prevTextureCoord );

                    count += 6;
                }

                prevVertexCoord = vertexCoord;
                prevTextureCoord = textureCoord;
                init = true;
            }
        }

        vertexCoords.seal( gl );
        textureCoords.seal( gl );

        return count;
    }
}
