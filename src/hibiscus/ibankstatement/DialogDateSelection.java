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
import java.util.Date;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.input.ButtonInput;
import de.willuhn.jameica.gui.input.DateInput;
import de.willuhn.jameica.gui.input.LabelInput;
import de.willuhn.jameica.gui.internal.action.Program;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.SWTUtil;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.jameica.hbci.HBCIProperties;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.util.ApplicationException;

/**
 * Dialog for entering of a date.
 * @author René Mach
 */
public class DialogDateSelection extends AbstractDialog<Date> {
  public static final int TYPE_DATE_END = 0;
  public static final int TYPE_DATE_START = 1;
  
  private static final String[] NAMES = {"End-Datum","Start-Datum"};
  
  private final static int WIDTH = 640;
  private final static int HEIGHT = 270;
  
  private int mType;
  
  private DateInput mDateInput;
  private Date mDate;
  private File mSource;
  private Konto mKonto;
  
  public DialogDateSelection(final int type, final File source, final Date date, final Konto konto) {
    super(POSITION_CENTER);
    
    mType = type;
    mSource = source;
    mDate = date;
    mKonto = konto;
    
    setTitle(NAMES[type]+" eines Kontoauszugs festlegen");
    setSize(WIDTH, HEIGHT);
  }
  
  @Override
  protected void paint(Composite parent) throws Exception {
    final SimpleContainer c = new SimpleContainer(parent);
    
    final ButtonInput auszug = new ButtonInput() {
      private Label mText;
      
      @Override
      public void setValue(Object value) {
      }
      
      @Override
      public Object getValue() {
        return null;
      }
      
      @Override
      public Control getClientControl(Composite parent) {
        mText = GUI.getStyleFactory().createLabel(parent, SWT.NONE);
        mText.setText(mSource.getName());
        addButtonListener(new Listener() {
          @Override
          public void handleEvent(Event event) {
            if (mSource.exists() && mSource.canRead()) {
              try {
                new Program().handleAction(mSource);
              } catch (ApplicationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
            }
          }
        });
        
        return mText;
      }
      
    };
    
    auszug.setButtonImage(SWTUtil.getImage("document-open.png"));
    auszug.setName("Kontoauszug:");
    
    // Taken from de.willuhn.jameica.hbci.gui.input.KontoInput, format(Object bean)
    StringBuffer sb = new StringBuffer();

    sb.append(i18n.tr("Kto. {0}",mKonto.getKontonummer()));

    String blz = mKonto.getBLZ();
    sb.append(" [");
    String bankName = HBCIProperties.getNameForBank(blz);
    if (bankName != null && bankName.length() > 0)
    {
      sb.append(bankName);
    }
    else
    {
      sb.append("BLZ ");
      sb.append(blz);
    }
    sb.append("] ");
    sb.append(mKonto.getName());

    String bez = mKonto.getBezeichnung();
    if (bez != null && bez.length() > 0)
    {
      sb.append(" - ");
      sb.append(bez);
    }
    // End of copy
    
    final LabelInput konto = new LabelInput(sb.toString());
    konto.setName("Konto:");
    
    c.addText("Das {0} eines Kontoauszugs konnte nicht ermittelt werden.\nBitte wählen Sie das {0} aus.".replace("{0}", NAMES[mType]), true);
    c.addText(" ", false);
    c.addInput(konto);
    c.addInput(auszug);
    c.addInput(getDateInput(mDate != null ? mDate : new Date()));
    
    ButtonArea buttons = new ButtonArea();
    buttons.addButton("Abbrechen", new Action() {
      @Override
      public void handleAction(Object context) throws ApplicationException {
        mDate = null;
        close();
      }
    }, null, false, "process-stop.png");
    buttons.addButton("OK", new Action() {
      @Override
      public void handleAction(Object context) throws ApplicationException {
        mDate = (Date)mDateInput.getValue();
        close();
      }
    }, null, false, "ok.png");
    
    c.addButtonArea(buttons);
    
    getShell().setMinimumSize(WIDTH,HEIGHT);
    getShell().addDisposeListener(new DisposeListener() {
      @Override
      public void widgetDisposed(DisposeEvent e)
      {
        Shell shell = getShell();
        if (shell == null || shell.isDisposed())
          return;
      }
    });
  }
  
  private synchronized DateInput getDateInput(final Date date) {
    if(mDateInput == null) {
      mDateInput = new DateInput(date);
      mDateInput.setName(NAMES[mType]+":");
    }
    
    return mDateInput;
  }
  
  @Override
  protected void onEscape() {
    mDate = null;
  }
  
  @Override
  protected Date getData() throws Exception {
    return mDate != null ? mDate : null;
  }
}
