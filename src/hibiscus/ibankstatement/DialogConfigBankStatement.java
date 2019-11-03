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
import java.rmi.RemoteException;
import java.util.ArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import de.willuhn.datasource.GenericObject;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.input.DirectoryInput;
import de.willuhn.jameica.gui.input.Input;
import de.willuhn.jameica.gui.input.SelectInput;
import de.willuhn.jameica.gui.input.TextInput;
import de.willuhn.jameica.gui.parts.Button;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.system.Settings;
import de.willuhn.util.ApplicationException;

/**
 * A class to configure the profile for importing of bank statement for a Konto.
 * @author René Mach
 */
public class DialogConfigBankStatement extends AbstractDialog {
  private static final String[] mAttributes = {"name"};
  
  static final String KEY_DOWNLOAD_PATH = ContextMenuImportBankStatement.class.getCanonicalName()+":Download_Path";
  static final String KEY_TARGET_PATH = ContextMenuImportBankStatement.class.getCanonicalName()+":Target_Path";
  static final String KEY_PATTERN_NAME = ContextMenuImportBankStatement.class.getCanonicalName()+":Name_Pattern";
  static final String KEY_MATCH_ORDER = ContextMenuImportBankStatement.class.getCanonicalName()+":Match_Order";
  static final String KEY_RENAME_PREFIX = ContextMenuImportBankStatement.class.getCanonicalName()+":Name_rename-prefix";
  static final String KEY_PROPERTY_KONTEN = ContextMenuImportBankStatement.class.getCanonicalName()+":Konten";
  
  private static final String VALUE_DEFAULT_PATTERN_NAME = "({\\d{4})-({\\d{2})-({\\d{2})_{konto}_({\\d{2}})\\.pdf";
  
  private final static int WIDTH = 850;
  private final static int HEIGHT = 470;
  
  private Konto mKonto;
  private Settings mSettings;
  
  private SelectInput mPredefined;
  
  private TextInput mPatternFileName;
  private TextInput mMatchOrder;
  private TextInput mRenamePrefix;
  
  private DirectoryInput mDirectorySource;
  private DirectoryInput mDirectoryTarget;
  
  private Button mSave;
  private Button mDelete;
  
  public DialogConfigBankStatement(final Settings settings, final Konto konto) {
    super(POSITION_CENTER);
    mKonto = konto;
    mSettings = settings;
    try {
      setTitle("Konfiguriere Import von Kontoauszügen für: " + konto.getBezeichnung());
    } catch (RemoteException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    setSize(WIDTH, HEIGHT);
  }
  
  /**
   * Checks if the input has not an empty value and for DirectoryInput if the entered path is a directory.
   * @param input The input to check.
   * @return <code>true</code> if the Input is valid.
   */
  private static boolean isValid(Input input) {
    return isValid(input, true);
  }
  
  /**
  * Checks if the input has not an empty value and for DirectoryInput if the entered path is a directory.
  * @param input The input to check.
  * @param checkEmpty If <code>true</code> the emptiness of the value of the input is not checked.
  * So an Input with an empty value is still valid. 
  * @return <code>true</code> if the Input is valid.
  */
  private static boolean isValid(Input input, boolean checkEmpty) {
    boolean result = !String.valueOf(input.getValue()).trim().isEmpty();
    
    if(input instanceof DirectoryInput) {
      if(!result) {
        result = !checkEmpty;
      }
      else {
        result = new File(String.valueOf(input.getValue()).trim()).isDirectory();
      }
    }
    
    return result;
  }

  /**
   * Create the dialog inputs.
   */
  @Override
  protected void paint(Composite parent) throws Exception {
    Container c1 = new SimpleContainer(parent);
    
    c1.addText("Der Datei-Pattern-Eintrag erlaubt den Platzhalter {konto}, unter Matching-Groups ist die Reihenfolge der Matching-Groups im Datei-Pattern einzutragen: JAHR, MONAT, TAG, NUMMER, nicht vorkommende Werte werden leer gelassen. Fehlt beispielsweise der Tag wird Jahr,Monat,,Nummer eingetragen. Mit dem Umbenenne-Prefix lässt sich ein Prefix festlegen, der dem Dateiname des Kontoauszugs voran gestellt wird. Mögliche Platzhalter: {year}, {month}, {day} und {number}\n", true);
    c1.addInput(getPredefinedInput());
    c1.addSeparator();
    
    Container c = new SimpleContainer(parent);
    c.addInput(getPatternFileName());
    c.addInput(getMatchOrder());
    c.addInput(getRenamePrefix());
    c.addInput(getDirectorySource());
    c.addInput(getDirectoryTarget());
    
    final Listener l = new Listener() {      
      @Override
      public void handleEvent(Event event) {
        try {
          boolean isValid = isValid(getPatternFileName()) && isValid(getMatchOrder()) && isValid(getDirectorySource()) && isValid(getDirectoryTarget(), false);
          
          if(isValid) {
            isValid = String.valueOf(getPatternFileName().getValue()).contains("(") && String.valueOf(getPatternFileName().getValue()).contains(")");
          }
          
          if(isValid) {
            final String[] parts = String.valueOf(getMatchOrder().getValue()).split(",");
            
            for(String part : parts) {
              if(!part.trim().isEmpty()) {
                try {
                  Integer.parseInt(part);
                }catch(NumberFormatException nfe) {
                  isValid = false;
                  break;
                }
              }
            }
          }
          
          getSaveButton().setEnabled(isValid);
        } catch (RemoteException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    };
    
    getPatternFileName().getControl().addListener(SWT.Modify, l);
    getMatchOrder().getControl().addListener(SWT.Modify, l);
    getDirectoryTarget().getControl().addPaintListener(new PaintListener() {
      @Override
      public void paintControl(PaintEvent e) {
        l.handleEvent(null);
      }
    });
    getDirectorySource().getControl().addPaintListener(new PaintListener() {
      @Override
      public void paintControl(PaintEvent e) {
        l.handleEvent(null);
      }
    });
    
    Container c2 = new SimpleContainer(parent);
    ButtonArea buttons = new ButtonArea();
    buttons.addButton(getDeleteButton());
    buttons.addButton("Abbrechen", new Action() {
      @Override
      public void handleAction(Object context) throws ApplicationException {
        close();
      }
    }, null, false, "process-stop.png");
    buttons.addButton(getSaveButton());
    c2.addButtonArea(buttons);
    
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
    
    l.handleEvent(null);
  }
  
  private synchronized Button getSaveButton() {
    if(mSave == null) {
      mSave = new Button("Speichern und Zurück", new Action() {
        @Override
        public void handleAction(Object context) throws ApplicationException {
          try {
            String[] current = mSettings.getList(KEY_PROPERTY_KONTEN, new String[0]);
            
            boolean found = false;
            
            final String id = mKonto.getKontonummer()+";"+mKonto.getBLZ();
            
            for(String konto : current) {
              if(konto.equals(id)) {
                found = true;
              }
            }
            
            if(!found) {
              String[] result = new String[current.length+1];
              
              if(result.length > 1) {
                System.arraycopy(current, 0, result, 0, current.length);
              }
              
              result[result.length-1] = id;
              
              mSettings.setAttribute(KEY_PROPERTY_KONTEN, result);
            }
            
            mKonto.setMeta(KEY_DOWNLOAD_PATH, String.valueOf(getDirectorySource().getValue()));
            mKonto.setMeta(KEY_TARGET_PATH, String.valueOf(getDirectoryTarget().getValue()));
            mKonto.setMeta(KEY_PATTERN_NAME, String.valueOf(getPatternFileName().getValue()));
            mKonto.setMeta(KEY_MATCH_ORDER, String.valueOf(getMatchOrder().getValue()));
            mKonto.setMeta(KEY_RENAME_PREFIX, String.valueOf(getRenamePrefix().getValue()));
          } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
          
          close();
        }
      }, null, true, "document-save.png");
      mSave.setEnabled(false);
    }
    
    return mSave;
  }
  
  private synchronized Button getDeleteButton() {
    if(mDelete == null) {
      mDelete = new Button("Profil löschen", new Action() {
        @Override
        public void handleAction(Object context) throws ApplicationException {
          try {
            String[] current = mSettings.getList(KEY_PROPERTY_KONTEN, new String[0]);
            ArrayList<String> newList = new ArrayList<>();
            
            final String id = mKonto.getKontonummer()+";"+mKonto.getBLZ();
            
            for(String konto : current) {
              if(!konto.equals(id)) {
                newList.add(konto);
              }
            }
            
            mSettings.setAttribute(KEY_PROPERTY_KONTEN, newList.toArray(new String[0]));
            
            mKonto.setMeta(KEY_DOWNLOAD_PATH, null);
            mKonto.setMeta(KEY_TARGET_PATH, null);
            mKonto.setMeta(KEY_PATTERN_NAME, null);
            mKonto.setMeta(KEY_MATCH_ORDER, null);
            mKonto.setMeta(KEY_RENAME_PREFIX, null);
            
            close();
          } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }
      }, null, false, "user-trash-full.png");
    }
    
    return mDelete;
  }
  
  private synchronized SelectInput getPredefinedInput() {
    if(mPredefined == null) {
      ArrayList<KontoData> list = new ArrayList<>();
      
      list.add(new KontoData("comdirect bank", ".*?Finanzreport_Nr\\._(\\d{2})_per_(\\d{2})\\.(\\d{2})\\.(\\d{4}).*?\\.pdf", "4,3,2,1", "20041133","20041144","20041155"));
      list.add(new KontoData("Consorsbank", ".*?KONTOAUSZUG_.*?KONTO_{konto}_dat(\\d{4})(\\d{2})(\\d{2})_id.*?\\.pdf", "1,2,3", "76030080","70120400"));
      list.add(new KontoData("Rabodirect", ".*?Kontoauszug_(\\d{4})(\\d{2})_DE\\d{2}50210212{konto}\\.pdf", "1,2", "50210212"));
      
      mPredefined = new SelectInput(list, null);
      mPredefined.setName("Bank-Profile:");
      mPredefined.setPleaseChoose("<Bekannte Bank-Profile zur Auswahl>");
      mPredefined.setAttribute(mAttributes[0]);
      mPredefined.addListener(new Listener() {
        @Override
        public void handleEvent(Event event) {
          KontoData data = (KontoData)mPredefined.getValue();
          
          if(data != null) {
            try {
              getPatternFileName().setValue(data.mFilePattern);
              getMatchOrder().setValue(data.mMatchOrder);
            } catch (RemoteException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }
          }
        }
      });
      
      try {
        if(((String)getPatternFileName().getValue()).equals(VALUE_DEFAULT_PATTERN_NAME) ||
            !isValid(getPatternFileName()) || !isValid(getMatchOrder())) {
          for(KontoData kontoData : list) {
            if(kontoData.hasBLZ(mKonto)) {
              mPredefined.setPreselected(kontoData);
              break;
            }
          }
        }
      } catch (RemoteException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    
    return mPredefined;
  }
  
  private synchronized DirectoryInput getDirectorySource() {
    if(mDirectorySource == null) {
      mDirectorySource = new DirectoryInput(System.getProperty("user.home")+File.separator+"Downloads");
      mDirectorySource.setName("Download-Ordner:");
      mDirectorySource.setMandatory(true);
    }
    
    return mDirectorySource;
  }
  
  private synchronized DirectoryInput getDirectoryTarget() throws RemoteException {
    if(mDirectoryTarget == null) {
      mDirectoryTarget = new DirectoryInput(mKonto.getMeta(KEY_TARGET_PATH, ""));
      mDirectoryTarget.setName("Ziel-Ordner:");
    }
    
    return mDirectoryTarget;
  }
  
  private synchronized TextInput getPatternFileName() throws RemoteException {
    if(mPatternFileName == null) {
      mPatternFileName = new TextInput(mKonto.getMeta(KEY_PATTERN_NAME, VALUE_DEFAULT_PATTERN_NAME));
      mPatternFileName.setName("Datei-Pattern:");
      mPatternFileName.setMandatory(true);
    }
    
    return mPatternFileName;
  }

  private synchronized TextInput getMatchOrder() throws RemoteException {
    if(mMatchOrder == null) {
      mMatchOrder = new TextInput(mKonto.getMeta(KEY_MATCH_ORDER, ""));
      mMatchOrder.setName("Match-Reihenfolge:");
      mMatchOrder.setHint("Reihenfolge der Matching-Groups für Jahr, Monat, Tag, Nummer, z.B.: 1,2,3,4");
      mMatchOrder.setMandatory(true);
    }
    
    return mMatchOrder;
  }
  
  private synchronized TextInput getRenamePrefix() throws RemoteException {
    if(mRenamePrefix == null) {
      mRenamePrefix = new TextInput(mKonto.getMeta(KEY_RENAME_PREFIX, ""));
      mRenamePrefix.setHint("Ein Prefix der beim Verschieben der Datei vorangestellt wird.");
      mRenamePrefix.setName("Umbenennen-Prefix:");
    }
    
    return mRenamePrefix;
  }
  
  @Override
  protected Object getData() throws Exception {
    return null;
  }
  
  @Override
  protected void onEscape() {
    //do nothing
  }
  
  /**
   * A class for the known profile selection entries.
   * @author René Mach
   */
  private static final class KontoData implements GenericObject {
    private String[] mBLZ;
    private String mName;
    private String mFilePattern;
    private String mMatchOrder;
    
    private KontoData(final String name, final String filePattern, final String matchOrder, final String... BLZ) {
      mBLZ = BLZ;
      mName = name;
      mFilePattern = filePattern;
      mMatchOrder = matchOrder;
    }
    
    @Override
    public boolean equals(GenericObject object) throws RemoteException {
      boolean result = false;
      
      if(object instanceof KontoData) {
        
      }
      
      return result;
    }

    @Override
    public Object getAttribute(String attribute) throws RemoteException {
      Object result = null;
      
      if(attribute != null && attribute.equals(getPrimaryAttribute())) {
        result = mName;
      }
      
      return result;
    }

    @Override
    public String[] getAttributeNames() throws RemoteException {
      return mAttributes;
    }

    @Override
    public String getID() throws RemoteException {
      return null;
    }

    @Override
    public String getPrimaryAttribute() throws RemoteException {
      return mAttributes[0];
    }
    
    private boolean hasBLZ(final Konto konto) throws RemoteException {
      boolean result = false;
      
      if(mBLZ != null) {
        for(String blz : mBLZ) {
          if(blz.equals(konto.getBLZ())) {
            result = true;
            break;
          }
        }
      }
      
      return result;
    }
  }
}
