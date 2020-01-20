/*
 * Hibiscus ibankstatement
 * Copyright (C) 2019 René Mach (dev@tvbrowser.org)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package hibiscus.ibankstatement;

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import de.willuhn.jameica.gui.input.DirectoryInput;
import de.willuhn.jameica.gui.util.Color;

/**
 * A directory input extended to directly show
 * error for directory selected is not existing.
 * 
 * @author René Mach
 */
public class DirectoryInputExt extends DirectoryInput {
  private Text mInput;
  
  public DirectoryInputExt(String dir) {
    super(dir);
  }

  @Override
  public void setValue(Object value) {
    super.setValue(value);
    checkValidity();
  }
  
  @Override
  public Control getClientControl(Composite parent) {
    Text c = (Text)super.getClientControl(parent);
    
    if(mInput == null || !mInput.equals(c)) {
      mInput = c;
      mInput.addListener(SWT.Modify, new Listener() {
        @Override
        public void handleEvent(Event event) {
          
        }
      });
      checkValidity();
    }
    
    return mInput;
  }
  
  /**
   * Check if directory is existing.
   * If not mark it with the error color.
   */
  private void checkValidity() {
    if(mInput != null && !mInput.isDisposed()) {
      if(mInput.getText().trim().isEmpty() || new File(mInput.getText()).isDirectory()) {
        mInput.setForeground(Color.FOREGROUND.getSWTColor());
      }
      else {
        mInput.setForeground(Color.ERROR.getSWTColor());
      }
    }
  }
}
