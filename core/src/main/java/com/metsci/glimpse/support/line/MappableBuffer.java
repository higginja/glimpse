package com.metsci.glimpse.support.line;

import static com.jogamp.common.nio.Buffers.SIZEOF_FLOAT;
import static com.metsci.glimpse.gl.util.GLUtils.genBuffer;
import static java.lang.Math.max;
import static javax.media.opengl.GL.GL_MAP_UNSYNCHRONIZED_BIT;
import static javax.media.opengl.GL.GL_MAP_WRITE_BIT;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import javax.media.opengl.GL;

public class MappableBuffer
{

    public final int target;

    /**
     * Passed to {@link GL#glBufferData(int, long, java.nio.Buffer, int)}
     * when allocating buffer space
     */
    public final int usage;

    /**
     * How many times larger than the map size to allocate, when we
     * have to allocate new space for the buffer
     */
    protected final int blockSizeFactor;

    /**
     * Assigned only once, the first time {@link #map(GL, long)} is
     * called
     */
    protected int buffer;

    /**
     * The byte size of the space currently allocated for the buffer
     */
    protected long blockSize;

    /**
     * The byte offset of the most recently sealed range
     */
    protected long sealedOffset;

    /**
     * When mapped: the byte offset of the mapped range
     * When sealed: the byte offset of the next range to be mapped
     */
    protected long mappedOffset;

    /**
     * When mapped: the byte size of the mapped range
     * When sealed: zero
     */
    protected long mappedSize;


    public MappableBuffer( int target, int usage, int blockSizeFactor )
    {
        this.target = target;
        this.usage = usage;
        this.blockSizeFactor = blockSizeFactor;

        this.buffer = 0;
        this.blockSize = 0;
        this.sealedOffset = -1;
        this.mappedOffset = 0;
        this.mappedSize = 0;
    }

    /**
     * Returns the buffer handle, as created by e.g. {@link GL#glGenBuffers(int, java.nio.IntBuffer)}.
     * <p>
     * Returns zero if none of the {@code map} methods (e.g. {@link #mapBytes(GL, long)}) have been
     * called yet.
     */
    public int buffer( )
    {
        return buffer;
    }

    /**
     * Returns the offset into {@link #buffer()} of the most recently sealed range -- e.g. for use
     * with {@link javax.media.opengl.GL2ES2#glVertexAttribPointer(int, int, int, boolean, int, long)}.
     * <p>
     * Returns -1 if {@link #seal(GL)} has not been called yet.
     */
    public long sealedOffset( )
    {
        return sealedOffset;
    }

    public FloatBuffer mapFloats( GL gl, long numFloats )
    {
        return mapBytes( gl, numFloats * SIZEOF_FLOAT ).asFloatBuffer( );
    }

    public ByteBuffer mapBytes( GL gl, long numBytes )
    {
        if ( mappedSize != 0 )
        {
            throw new RuntimeException( "Buffer is already mapped -- must be sealed before being mapped again" );
        }

        if ( buffer == 0 )
        {
            this.buffer = genBuffer( gl );
        }

        gl.glBindBuffer( target, buffer );

        // Seems recommended to map in multiples of 64 ... I guess for alignment reasons?
        this.mappedSize = nextMultiple( numBytes, 64 );

        if ( mappedOffset + mappedSize > blockSize )
        {
            // Allocate a block large enough that we don't have to allocate too frequently
            this.blockSize = max( blockSize, blockSizeFactor * mappedSize );

            // Allocate new space, and orphan the old space
            gl.glBufferData( target, blockSize, null, usage );

            // Start at the beginning of the new space
            this.mappedOffset = 0;
        }

        return gl.glMapBufferRange( target, mappedOffset, mappedSize, GL_MAP_WRITE_BIT | GL_MAP_UNSYNCHRONIZED_BIT );
    }

    /**
     * Returns the smallest multiple of b that is greater than or equal to a.
     */
    protected static long nextMultiple( long a, long b )
    {
        return ( b * ( ( ( a - 1 ) / b ) + 1 ) );
    }

    /**
     * Unmaps the currently mapped range. After this, the sealed range can be read by GL calls.
     */
    public void seal( GL gl )
    {
        if ( mappedSize == 0 )
        {
            throw new RuntimeException( "Buffer is not currently mapped" );
        }

        gl.glBindBuffer( target, buffer );
        gl.glUnmapBuffer( target );

        this.sealedOffset = mappedOffset;
        this.mappedOffset += mappedSize;
        this.mappedSize = 0;
    }

}
