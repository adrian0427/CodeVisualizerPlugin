package edu.calpoly.csc.codevisualizerplugin.diagram;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import javax.swing.ImageIcon;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PlantUmlRenderer {
    public ImageIcon renderPng(String plantUml) throws IOException {
        SourceStringReader reader = new SourceStringReader(plantUml);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        reader.outputImage(output, new FileFormatOption(FileFormat.PNG));
        return new ImageIcon(output.toByteArray());
    }
}
