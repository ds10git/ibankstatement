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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;

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
import de.willuhn.jameica.gui.input.LabelInput;
import de.willuhn.jameica.gui.input.SelectInput;
import de.willuhn.jameica.gui.input.TextInput;
import de.willuhn.jameica.gui.parts.Button;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.Settings;
import de.willuhn.util.ApplicationException;

/**
 * A class to configure the profile for importing of bank statement for a Konto.
 * @author René Mach
 */
public class DialogConfigBankStatement extends AbstractDialog<Object> {
  private static final String[] mAttributes = {"name"};
  
  static final String KEY_DOWNLOAD_PATH = ContextMenuImportBankStatement.class.getCanonicalName()+":Download_Path";
  static final String KEY_TARGET_PATH = ContextMenuImportBankStatement.class.getCanonicalName()+":Target_Path";
  static final String KEY_PATTERN_NAME = ContextMenuImportBankStatement.class.getCanonicalName()+":Name_Pattern";
  static final String KEY_MATCH_ORDER = ContextMenuImportBankStatement.class.getCanonicalName()+":Match_Order";
  static final String KEY_RENAME_PREFIX = ContextMenuImportBankStatement.class.getCanonicalName()+":Name_rename-prefix";
  
  static final String LEGACY_KEY_ALWAYS_ON_WEEKDAY_END = ContextMenuImportBankStatement.class.getCanonicalName()+":Ends_Always_On_Week_Day";
  
  static final String KEY_PROPERTY_KONTEN = ContextMenuImportBankStatement.class.getCanonicalName()+":Konten";
  
  private static final String VALUE_DEFAULT_PATTERN_NAME = "({\\d{4})-({\\d{2})-({\\d{2})_{konto}_({\\d{2}})\\.pdf";
  
  private final static int WIDTH = 1000;
  private final static int HEIGHT = 640;
  
  private HashMap<Input, InputValidator> mValidationMap;
  
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
    
    mValidationMap = new HashMap<>(0);
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
  private boolean isValid(Input input) {
    return isValid(input, true);
  }
  
  /**
  * Checks if the input has not an empty value and for DirectoryInput if the entered path is a directory.
  * @param input The input to check.
  * @param checkEmpty If <code>false</code> the emptiness of the value of the input is not checked.
  * So an Input with an empty value is still valid. 
  * @return <code>true</code> if the Input is valid.
  */
  private boolean isValid(Input input, boolean checkEmpty) {
    final InputValidator test = mValidationMap.get(input);
    
    if(test != null) {
      return test.isValid(input, checkEmpty);
    }
    
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
    
    c1.addText("Der Datei-Pattern-Eintrag erlaubt die Platzhalter {konto} und {kennung}, unter Matching-Groups ist die Reihenfolge der Matching-Groups im Datei-Pattern einzutragen z.B.: {jahr},{monat},{tag},{nummer}, nicht vorkommende Werte werden weggelassen. Mit dem Umbenennen-Prefix lässt sich ein Prefix festlegen, der dem Dateiname des Kontoauszugs voran gestellt wird. Mögliche Platzhalter: {jahr}, {monat}, {tag}, {nummer} und {nummerJahr}\n", true);
    c1.addInput(getPredefinedInput());
    c1.addSeparator();
    
    LabelInput help = new LabelInput("Rechenoperationen im Platzhalter:\n      Addition/Subtraktion z.B.: {monat_+1},{tag_-4}\n      ACHTUNG: Keine Plausibilitätsprüfung der berechneten Werte.\nSteuerbefehle im Platzhalter:\n      Auszug endet immer an einem Tag der Woche z.B.: {tag_{0}7}=Sonntag, {tag_{0}1}=Montag\n      Auszug endet am letzten Wochentag des Monats: {monat_{1}}\n      Auszug endet immer am gleichen Tag des Monats: z.B.: {monat_{2}28}=28. des Monats\n      Auszug beginnt am End-Datum des vorigen Auszugs: {monat_{3}} oder {nummer_{3}}\n      Auszug beginnt immer am gleichen Tag des Monats: z.B.: {monat_{4}13}=13. des Monats\n      WICHTIG: Es kann nur ein Platzhalter Steuerbefehle enthalten, aber durchaus mehrere, z.B. {monat_{4}1;{2}31}".replace("{0}", Placeholder.KEY_END_DAY_OF_WEEK).replace("{1}", Placeholder.KEY_END_LAST_WEEKDAY_OF_MONTH).replace("{2}", Placeholder.KEY_END_DAY_OF_MONTH).replace("{3}", Placeholder.KEY_START_ON_LAST_DATE).replace("{4}", Placeholder.KEY_START_DAY_OF_MONTH));
    help.setName(" ");
    
    Container c = new SimpleContainer(parent);
    c.addInput(getPatternFileName());
    c.addInput(getMatchOrder());
    c.addInput(help);
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
              if(!part.trim().isEmpty() && !Placeholder.contains(part)) {
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
            
            mKonto.setMeta(LEGACY_KEY_ALWAYS_ON_WEEKDAY_END, null);
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
            mKonto.setMeta(LEGACY_KEY_ALWAYS_ON_WEEKDAY_END, null);
            
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
      
      // altes Format 4=Nummer, 1=Jahr, 2=Monat, 3=Tag
      
      try(BufferedReader read = new BufferedReader(new InputStreamReader(Application.getPluginLoader().getManifest(Plugin.class).getClassLoader().getResourceAsStream("csv/known_institutes.csv"), "UTF-8")) ) {
        String line = read.readLine();
        
        String[] parts = line.split(";");
        
        int indexName = -1;
        int indexPattern = -1;
        int indexOrder = -1;
        int indexBLZ = -1;
        int indexBIC = -1;
        
        for(int i = 0; i < parts.length; i++) {
          if("name".equals(parts[i])) {
            indexName = i;
          }
          else if("pattern".equals(parts[i])) {
            indexPattern = i;
          }
          else if("order".equals(parts[i])) {
            indexOrder = i;
          }
          else if("BLZ".equals(parts[i])) {
            indexBLZ = i;
          }
          else if("BIC".equals(parts[i])) {
            indexBIC = i;
          }
        }
        
        while((line = read.readLine()) != null) {
          parts = line.split(";",-1);
          
          list.add(new KontoData(parts[indexName], parts[indexPattern], parts[indexOrder], parts[indexBLZ].split(","), parts[indexBIC].split(",")));
        }
        
      }catch(IOException ioe) {
        ioe.printStackTrace();
      }
      
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
            if(kontoData.hasBIC(mKonto) || kontoData.hasBLZ(mKonto)) {
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
  
  private synchronized DirectoryInput getDirectorySource() throws RemoteException {
    if(mDirectorySource == null) {
      mDirectorySource = new DirectoryInput(mKonto.getMeta(KEY_DOWNLOAD_PATH, System.getProperty("user.home")+File.separator+"Downloads"));
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
      
      mValidationMap.put(mPatternFileName, new InputValidator() {
        @Override
        public boolean isValid(Input input, boolean checkEmpty) {
          String text = (String)input.getValue();
          
          int openingBrackets = 0;
          int closingBrackets = 0;
          int dCount = 0;
          
          char lastChar = 'a';
          char beforeLast = 'a';
          
          for(int i = 0; i < text.length(); i++) {
            if(lastChar != '\\') {
              if(text.charAt(i) == '(') {
                openingBrackets++;
              }
              else if(text.charAt(i) == ')') {
                closingBrackets++;
              }
            }
            else if(beforeLast == '(' && text.charAt(i) == 'd') {
              dCount++;
            }
            
            beforeLast = lastChar;
            lastChar = text.charAt(i);
          }
          
          return openingBrackets >= 2 && closingBrackets >= 2 && openingBrackets == closingBrackets && dCount >= 2;
        }
      });
    }
    
    return mPatternFileName;
  }

  private synchronized TextInput getMatchOrder() throws RemoteException {
    if(mMatchOrder == null) {
      String value = mKonto.getMeta(KEY_MATCH_ORDER, "");
      
      if(mKonto.getMeta(LEGACY_KEY_ALWAYS_ON_WEEKDAY_END, "false").equals("true")) {
        value = Placeholder.replace(Placeholder.get(Placeholder.TYPE_MONTH), "{monat_"+Placeholder.KEY_END_LAST_WEEKDAY_OF_MONTH+"}", value);
      }
      
      mMatchOrder = new TextInput(value);
      mMatchOrder.setName("Match-Reihenfolge:");
      mMatchOrder.setHint("Reihenfolge der Matching-Groups für {jahr},{monat},{tag},{nummer},{nummerJahr}, z.B.: {jahr},{monat},{nummer}");
      mMatchOrder.setMandatory(true);
      
      mValidationMap.put(mMatchOrder, new InputValidator() {
        @Override
        public boolean isValid(final Input input, final boolean checkEmpty) {
          String text = (String) input.getValue();
          boolean hasEndsOnLastDayOfMonth = text.contains("_"+Placeholder.KEY_END_LAST_WEEKDAY_OF_MONTH) || text.contains(";"+Placeholder.KEY_END_LAST_WEEKDAY_OF_MONTH);
          boolean hasEndsDayOfWeek = text.matches(".*?_"+Placeholder.KEY_END_DAY_OF_WEEK+"\\d{1}.*?") || text.matches(".*?;"+Placeholder.KEY_END_DAY_OF_WEEK+"\\d{1}.*?");
          boolean hasEndsOnDayOfMonth = text.matches(".*?_"+Placeholder.KEY_END_DAY_OF_MONTH+"\\d{1,2}.*?") || text.matches(".*?;"+Placeholder.KEY_END_DAY_OF_MONTH+"\\d{1,2}.*?");
          
          boolean hasStartsOnLastDate = text.contains("_"+Placeholder.KEY_START_ON_LAST_DATE) || text.contains(";"+Placeholder.KEY_START_ON_LAST_DATE);
          boolean hasStartsOnDayOfMonth = text.matches(".*?_"+Placeholder.KEY_START_DAY_OF_MONTH+"\\d{1,2}.*?") || text.matches(".*?;"+Placeholder.KEY_START_DAY_OF_MONTH+"\\d{1,2}.*?");
          
          return (Placeholder.get(Placeholder.TYPE_YEAR).textContainsMe(text) || Placeholder.get(Placeholder.TYPE_NUMBER_YEAR).textContainsMe(text)) && (Placeholder.get(Placeholder.TYPE_MONTH).textContainsMe(text) || Placeholder.get(Placeholder.TYPE_NUMBER).textContainsMe(text)) &&
              ((!hasEndsDayOfWeek || !hasEndsOnLastDayOfMonth) && (!hasEndsDayOfWeek || !hasEndsOnDayOfMonth) && (!hasEndsOnDayOfMonth || !hasEndsOnLastDayOfMonth)) &&
              (!hasStartsOnLastDate || !hasStartsOnDayOfMonth);
              
        }
      });
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
    private String[] mBIC;
    private String mName;
    private String mFilePattern;
    private String mMatchOrder;
    
    private KontoData(final String name, final String filePattern, final String matchOrder, final String[] BLZ, final String[] BIC) {
      mBLZ = BLZ;
      mBIC = BIC;
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
        final String kontoBLZ = konto.getBLZ();
        
        for(String blz : mBLZ) {
          if(!blz.trim().isEmpty() && blz.equals(kontoBLZ)) {
            result = true;
            break;
          }
        }
      }
      
      return result;
    }
    
    private boolean hasBIC(final Konto konto) throws RemoteException {
      boolean result = false;
      
      if(mBIC != null) {
        final String kontoBIC = konto.getBic();
        
        for(String bic : mBIC) {
          if(!bic.trim().isEmpty() && kontoBIC.startsWith(bic)) {
            result = true;
          }
        }
      }
      
      return result;
    }
  }
  
  private static interface InputValidator {
    public boolean isValid(final Input input, boolean checkEmpty);
  }
}
