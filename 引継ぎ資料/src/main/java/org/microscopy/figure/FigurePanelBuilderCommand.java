package org.microscopy.figure;

import javax.swing.SwingUtilities;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, menuPath = "Plugins>Figure Panel Builder")
public class FigurePanelBuilderCommand implements Command {
  public void run() {
    SwingUtilities.invokeLater(() -> new FigurePanelBuilderDialog().setVisible(true));
  }
}
