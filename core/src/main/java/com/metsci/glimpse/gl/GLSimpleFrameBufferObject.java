/*
 * Copyright (c) 2012, Metron, Inc.
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
package com.metsci.glimpse.gl;

import static com.metsci.glimpse.util.logging.LoggerUtils.logWarning;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLContext;

import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;
import com.metsci.glimpse.support.texture.ExternalTextureProjected2D;
import com.metsci.glimpse.support.texture.TextureProjected2D;

public class GLSimpleFrameBufferObject
{
    private static final Logger logger = Logger.getLogger( GLSimpleFrameBufferObject.class.getName( ) );

    private GLContext context;
    private boolean initialized;

    private int width;
    private int height;

    private boolean useStencil;
    private boolean useDepth;

    private int[] textureId;
    private int[] renderBufferId;
    private int[] frameBufferId;

    private List<GLSimpleFboListenerEntry> listeners;

    private ReentrantLock lock;

    public GLSimpleFrameBufferObject( int width, int height, GLContext context )
    {
        this( width, height, true, false, context );
    }

    public GLSimpleFrameBufferObject( int width, int height, boolean useDepth, boolean useStencil, GLContext context )
    {
        this.context = context;
        this.width = width;
        this.height = height;
        this.useDepth = useDepth;
        this.useStencil = useStencil;

        this.listeners = new CopyOnWriteArrayList<GLSimpleFboListenerEntry>( );
        this.lock = new ReentrantLock( );
    }

    public void resize( int width, int height )
    {
        this.initialized = false;
        this.width = width;
        this.height = height;
    }

    // XXX: I have no idea if this is the right name for the method.
    public Object glSyncExec( final GLRunnable runnable )
    {
        context.makeCurrent( );
        Object result = ( new GLRunnable( )
        {
            @Override
            public Object run( GLContext context )
            {
                lock.lock( );
                try
                {
                    bind( context );
                    try
                    {
                        return runnable.run( context );
                    }
                    finally
                    {
                        context.getGL( ).glFlush( );
                        unbind( context );
                    }
                }
                finally
                {
                    lock.unlock( );
                }
            }
        } ).run( context );
        context.release( );

        return result;
    }

    public void draw( )
    {
        final Rectangle bounds = getBounds( );
        final List<GLSimpleFboListenerEntry> entries = new ArrayList<GLSimpleFboListenerEntry>( listeners );

        glSyncExec( new GLRunnable( )
        {
            @Override
            public Object run( GLContext context )
            {
                for ( GLSimpleFboListenerEntry l : entries )
                    l.draw( context, bounds );

                return null;
            }
        } );
    }

    public void bind( GLContext context )
    {
        GL gl = context.getGL( );

        if ( !initialized )
        {
            if ( textureId != null )
            {
                gl.glDeleteTextures( 1, textureId, 0 );
            }

            // check if the video card supports this size of texture
            int[] maxTextureSize = new int[1];
            gl.glGetIntegerv( GL2.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0 );
            if ( maxTextureSize[0] < width || maxTextureSize[0] < height )
            {
                logWarning( logger, "Texture (%dx%d) has dimensions larger than maximum supported (%d)", width, height, maxTextureSize[0] );
            }

            // create a texture object
            textureId = new int[1];
            gl.glGenTextures( 1, textureId, 0 );
            gl.glBindTexture( GL2.GL_TEXTURE_2D, textureId[0] );
            gl.glTexParameterf( GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR );
            gl.glTexParameterf( GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR_MIPMAP_LINEAR );
            gl.glTexParameterf( GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP_TO_EDGE );
            gl.glTexParameterf( GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP_TO_EDGE );
            gl.glTexParameteri( GL2.GL_TEXTURE_2D, GL2.GL_GENERATE_MIPMAP, GL2.GL_TRUE ); // automatic mipmap
            //            gl.glTexImage2D( GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA8, width, height, 0, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, null );
            gl.glTexImage2D( GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA, width, height, 0, GL2.GL_RGBA, GL2.GL_FLOAT, null );
            gl.glBindTexture( GL2.GL_TEXTURE_2D, 0 );

            if ( renderBufferId != null )
            {
                gl.glDeleteRenderbuffers( 2, renderBufferId, 0 );
            }

            // create a renderbuffer objects
            renderBufferId = new int[2];
            if ( useDepth || useStencil )
            {
                gl.glDeleteRenderbuffers( 2, renderBufferId, 0 );
            }

            // initialize renderbuffer storing depth info
            if ( useDepth )
            {
                gl.glBindRenderbuffer( GL2.GL_RENDERBUFFER, renderBufferId[0] );
                gl.glRenderbufferStorage( GL2.GL_RENDERBUFFER, GL2.GL_DEPTH_COMPONENT, width, height );
            }

            // initialize renderbuffer storing stencil info
            if ( useStencil )
            {
                gl.glBindRenderbuffer( GL2.GL_RENDERBUFFER, renderBufferId[1] );
                gl.glRenderbufferStorage( GL2.GL_RENDERBUFFER, GL2.GL_STENCIL_INDEX16, width, height );
            }

            gl.glBindRenderbuffer( GL2.GL_RENDERBUFFER, 0 );

            if ( frameBufferId != null )
            {
                gl.glDeleteFramebuffers( 1, frameBufferId, 0 );
            }

            // create a framebuffer object
            frameBufferId = new int[1];
            gl.glGenFramebuffers( 1, frameBufferId, 0 );
            gl.glBindFramebuffer( GL2.GL_FRAMEBUFFER, frameBufferId[0] );

            // attach the texture to FBO color attachment point
            gl.glFramebufferTexture2D( GL2.GL_FRAMEBUFFER, GL2.GL_COLOR_ATTACHMENT0, GL2.GL_TEXTURE_2D, textureId[0], 0 );

            // attach the renderbuffer to depth attachment point
            if ( useDepth )
            {
                gl.glFramebufferRenderbuffer( GL2.GL_FRAMEBUFFER, GL2.GL_DEPTH_ATTACHMENT, GL2.GL_RENDERBUFFER, renderBufferId[0] );
            }

            // attach the renderbuffer to stencil attachment point
            if ( useStencil )
            {
                gl.glFramebufferRenderbuffer( GL2.GL_FRAMEBUFFER, GL2.GL_STENCIL_ATTACHMENT, GL2.GL_RENDERBUFFER, renderBufferId[1] );
            }

            // check FBO status
            int status = gl.glCheckFramebufferStatus( GL2.GL_FRAMEBUFFER );
            if ( status != GL2.GL_FRAMEBUFFER_COMPLETE )
            {
                logWarning( logger, "Framebuffer not initialized (status=%d)", status );
            }

            initialized = true;
        }
        else
        {
            gl.glBindFramebuffer( GL2.GL_FRAMEBUFFER, frameBufferId[0] );
        }
    }

    public void unbind( GLContext context )
    {
        GL gl = context.getGL( );

        // switch back to window-system-provided framebuffer
        gl.glBindFramebuffer( GL2.GL_FRAMEBUFFER, 0 );

        // trigger mipmaps generation explicitly
        // NOTE: If GL_GENERATE_MIPMAP is set to GL_TRUE, then glCopyTexSubImage2D()
        // triggers mipmap generation automatically. However, the texture attached
        // onto a FBO should generate mipmaps manually via glGenerateMipmapEXT().
        gl.glBindTexture( GL2.GL_TEXTURE_2D, textureId[0] );
        gl.glGenerateMipmap( GL2.GL_TEXTURE_2D );
        gl.glBindTexture( GL2.GL_TEXTURE_2D, 0 );
    }

    public void addListener( GLSimpleListener listener )
    {
        listeners.add( new GLSimpleFboListenerEntry( listener ) );
    }

    public Dimension getDimension( )
    {
        return new Dimension( width, height );
    }

    public Rectangle getBounds( )
    {
        return new Rectangle( 0, 0, width, height );
    }

    public GLContext getGLContext( )
    {
        return context;
    }

    public int getTextureId( )
    {
        return textureId[0];
    }

    public boolean isInitialized( )
    {
        return initialized;
    }

    public Texture getOpenGLTexture( )
    {
        return TextureIO.newTexture( textureId[0] );
    }

    public TextureProjected2D getGlimpseTexture( )
    {
        return new ExternalTextureProjected2D( getTextureId( ), width, height, false );
    }

    public void dispose( GLContext context )
    {
        GL gl = context.getGL( );

        for ( GLSimpleFboListenerEntry entry : listeners )
        {
            entry.dispose( context );
        }

        if ( textureId != null ) gl.glDeleteTextures( 1, textureId, 0 );

        if ( renderBufferId != null ) gl.glDeleteRenderbuffers( 2, renderBufferId, 0 );

        if ( frameBufferId != null ) gl.glDeleteFramebuffers( 1, frameBufferId, 0 );
    }
}
