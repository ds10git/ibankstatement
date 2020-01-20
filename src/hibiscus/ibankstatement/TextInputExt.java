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

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;

import de.willuhn.jameica.gui.input.TextInput;
import de.willuhn.jameica.gui.util.Color;
import hibiscus.ibankstatement.DialogConfigBankStatement.InputValidator;

/**
 * An extended text input with support for validity check
 * and marking of invalid text with error color.
 * 
 * @author René Mach
 */
public class TextInputExt extends TextInput {
  private Text mInput;
  private InputValidator mValidator;
  
  public TextInputExt(String value) {
    super(value);
  }

  /**
   * Sets the input validator to use for the validity check.
   * 
   * @param validator The validator to use for validity check.
   */
  public void setValidator(InputValidator validator) {
    mValidator = validator;
  }
  
  @Override
  public Control getControl() {
    Text c = (Text) super.getControl();
    
    if(mInput == null || !mInput.equals(c)) {
      mInput = c;
    }
    
    return mInput;
  }
  
  /**
   * Checks for validity and marks invalid
   * text with error color.
   * 
   * @param checkEmpty If emptiness should also be checked for validity.
   * @return <code>true</code> if input value is valid, <code>false</code> otherwise.
   */
  public boolean isValid(boolean checkEmpty) {
    boolean result = true;
    
    if(mValidator != null) {
      result = mValidator.isValid(this, checkEmpty);
    }
    else if(checkEmpty) {
      result = !mInput.getText().trim().isEmpty();
    }
    
    if(mInput != null && !mInput.isDisposed()) {
      if(result) {
        mInput.setForeground(Color.FOREGROUND.getSWTColor());
      }
      else {
        mInput.setForeground(Color.ERROR.getSWTColor());
      }
    }
    
    return result;
  }
}
