package com.metsci.glimpse.timing;

import static com.metsci.glimpse.support.FrameUtils.*;
import static javax.media.opengl.GL.*;
import static javax.media.opengl.fixedfunc.GLMatrixFunc.*;
import static javax.swing.WindowConstants.*;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAnimatorControl;
import javax.media.opengl.GLProfile;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.metsci.glimpse.context.GlimpseBounds;
import com.metsci.glimpse.context.GlimpseContext;
import com.metsci.glimpse.painter.base.GlimpsePainterBase;
import com.metsci.glimpse.painter.decoration.BackgroundPainter;
import com.metsci.glimpse.plot.EmptyPlot2D;
import com.metsci.glimpse.support.settings.SwingLookAndFeel;
import com.metsci.glimpse.support.swing.NewtSwingEDTGlimpseCanvas;
import com.metsci.glimpse.support.swing.SwingEDTAnimator;


public class ImmediateModeTimingTest
{

    public static void main( String[] args )
    {
        final EmptyPlot2D plot = new EmptyPlot2D( );
        plot.addPainter( new BackgroundPainter( ) );
        plot.addPainter( new TestPainter( ) );
        plot.addPainter( new FpsPrinter( ) );

        SwingUtilities.invokeLater( new Runnable( )
        {
            public void run( )
            {
                NewtSwingEDTGlimpseCanvas canvas = new NewtSwingEDTGlimpseCanvas( GLProfile.GL2 );
                canvas.addLayout( plot );
                canvas.setLookAndFeel( new SwingLookAndFeel( ) );

                GLAnimatorControl animator = new SwingEDTAnimator( 1000 );
                animator.add( canvas.getGLDrawable( ) );
                animator.start( );

                JFrame frame = newFrame( "ImmediateModeTimingTest", canvas, DISPOSE_ON_CLOSE );
                stopOnWindowClosing( frame, animator );
                disposeOnWindowClosing( frame, canvas );
                showFrameCentered( frame );
            }
        } );
    }

    protected static class TestPainter extends GlimpsePainterBase
    {
        protected static final int numIterations = 10000;
        protected static final int verticesPerIteration = 4;

        public TestPainter( )
        { }

        @Override
        public void doPaintTo( GlimpseContext context )
        {
            GlimpseBounds bounds = getBounds( context );

            GL2 gl = context.getGL( ).getGL2( );
            gl.glColor4f( 0, 0, 0, 1 );

            gl.glMatrixMode( GL_PROJECTION );
            gl.glLoadIdentity( );
            gl.glOrtho( -0.5, bounds.getWidth( ) + 0.5, -0.5, bounds.getHeight( ) + 0.5, -1, 1 );

            for ( int i = 0; i < numIterations; i++ )
            {
                gl.glBegin( GL_POINTS );

                for ( int v = 0; v < verticesPerIteration; v++ )
                {
                    gl.glVertex2f( v, v );
                }

                gl.glEnd( );
            }
        }

        @Override
        protected void doDispose( GlimpseContext context )
        { }
    }

}