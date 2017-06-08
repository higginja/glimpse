package com.metsci.glimpse.wizard.page;

import java.io.IOException;
import java.net.URL;

import javax.swing.JSeparator;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;

public abstract class DescriptionWizardPage<D> extends SimpleWizardPage<D>
{
    protected String descriptionFile;

    public DescriptionWizardPage( Object parentId, String title, String descriptionFile )
    {
        super( parentId, title );

        this.descriptionFile = descriptionFile;
        this.container.setLayout( new MigLayout( ) );

        JTextPane descriptionArea = new JTextPane( );
        descriptionArea.setEditable( false );
        descriptionArea.setOpaque( false );

        URL url = getClass( ).getClassLoader( ).getResource( descriptionFile );
        try
        {
            descriptionArea.setPage( url );
        }
        catch ( IOException e )
        {
            descriptionArea.setText( String.format( "Error: Unable to load page description from: %s", descriptionFile ) );
        }

        this.container.add( descriptionArea, "split, span, pushx, growx, wrap" );
        this.container.add( new JSeparator( SwingConstants.HORIZONTAL ), "split, span, gap 0 0 10 10, pushx, growx, wrap" );
    }
}