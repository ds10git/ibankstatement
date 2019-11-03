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
import java.io.FileFilter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.willuhn.datasource.GenericIterator;
import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.extension.Extendable;
import de.willuhn.jameica.gui.extension.Extension;
import de.willuhn.jameica.gui.parts.CheckedContextMenuItem;
import de.willuhn.jameica.gui.parts.ContextMenu;
import de.willuhn.jameica.gui.parts.ContextMenuItem;
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.gui.filter.KontoFilter;
import de.willuhn.jameica.hbci.messaging.ImportMessage;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Kontoauszug;
import de.willuhn.jameica.hbci.server.KontoUtil;
import de.willuhn.jameica.hbci.server.KontoauszugPdfUtil;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;

/**
 * A class to provide the context menu entries for Hibiscus Ibankstatement.
 * @author René Mach
 */
public class ContextMenuImportBankStatement implements Extension {
  private de.willuhn.jameica.system.Settings mSettings = new de.willuhn.jameica.system.Settings(ContextMenuImportBankStatement.class);
  
  @Override
  public void extend(Extendable extendable) {
    if (extendable == null || !(extendable instanceof ContextMenu))
    {
      Logger.warn("invalid extendable, skipping extension");
      return;
    }
    
    ContextMenu menu = (ContextMenu) extendable;
    menu.addItem(ContextMenuItem.SEPARATOR);
    
    menu.addItem(new CheckedContextMenuItem("Importieren von Kontoauszügen konfigurieren...", new Action() {
      @Override
      public void handleAction(final Object context) throws ApplicationException {
        //open config dialog for Konto the context menu was opened for
        DialogConfigBankStatement config = new DialogConfigBankStatement(mSettings, (Konto)context);
        try {
          config.open();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }));
    
    menu.addItem(new MyContextMenuItem("Kontoauszüge importieren...", new Action() {
      @Override
      public void handleAction(Object context) throws ApplicationException {
        final String[] ids = mSettings.getList(DialogConfigBankStatement.KEY_PROPERTY_KONTEN, null);
        List<Konto> konten = null;
        
        try {
          // find Konten to import bank statement for
          konten = KontoUtil.getKonten(new KontoFilter() {
            @Override
            public boolean accept(Konto konto) throws RemoteException {
              boolean result = false;
              
              for(String id : ids) {
                String[] parts = id.split(";");
                
                result = konto.getKontonummer().equals(parts[0]) && konto.getBLZ().equals(parts[1]);
                
                if(result) {
                  break;
                }
              }
              
              return result;
            }
          });
        } catch (RemoteException e1) {
          e1.printStackTrace();
        }
        
        if(konten != null) {
          for(Konto konto : konten) {
            try {
              String pattern = konto.getMeta(DialogConfigBankStatement.KEY_PATTERN_NAME, "");
              String matchingGroups = konto.getMeta(DialogConfigBankStatement.KEY_MATCH_ORDER, "");
              
              if(!pattern.trim().isEmpty() && !matchingGroups.trim().isEmpty()) {
                String search = pattern.replace("{konto}", konto.getKontonummer());
                
                //find all files that match the bank statement pattern for the current Konto
                File[] pdfFiles = new File(konto.getMeta(DialogConfigBankStatement.KEY_DOWNLOAD_PATH, System.getProperty("user.home")+File.separator+"Downloads")).listFiles((FileFilter)file -> {
                  return Pattern.matches(search, file.getName());
                });
                
                Arrays.sort(pdfFiles);
                
                //handle all found bank statements for current Konto
                for(File pdfFile : pdfFiles) {
                  Pattern p = Pattern.compile(pattern.replace("{konto}", konto.getKontonummer()));
                  Matcher m = p.matcher(pdfFile.getName());

                  String year = null;
                  String month = null;
                  String day = null;
                  String number = null;
                  
                  if(m.find()) {
                    String[] parts = matchingGroups.split(",");
                    
                    for(int i = 0; i < parts.length; i++) {
                      
                      if(!parts[i].trim().isEmpty()) {
                        Placeholder placeholder = Placeholder.get(parts[i]);
                        
                        if(placeholder != null) {
                          switch(placeholder.getType()) {
                            case Placeholder.TYPE_YEAR: year = m.group(i+1);break;
                            case Placeholder.TYPE_MONTH: month = m.group(i+1);break;
                            case Placeholder.TYPE_DAY: day = m.group(i+1);break;
                            case Placeholder.TYPE_NUMBER: number = m.group(i+1);break;
                          }
                        }
                        else {
                          switch(i) {
                            case 0: year = m.group(Integer.parseInt(parts[i]));break;
                            case 1: month = m.group(Integer.parseInt(parts[i]));break;
                            case 2: day = m.group(Integer.parseInt(parts[i]));break;
                            case 3: number = m.group(Integer.parseInt(parts[i]));break;
                          }
                        }
                      }
                    }
                  }
                  
                  // open import of bank statement for current Konto and current pdfFile
                  importKontoauszug(pdfFile, konto, year, month, day, number, konto.getMeta(DialogConfigBankStatement.KEY_RENAME_PREFIX, ""));
                }
              }
            } catch (RemoteException e1) {
              // TODO Auto-generated catch block
              e1.printStackTrace();
            }
           
          }
        }
      }
    }));
  }
  
  private static final Calendar setCalendarDate(final int year, final int month, final int day) {
    return setCalendarDate(null, year, month, day);
  }
  
  private static final Calendar setCalendarDate(Calendar cal, final int year, final int month, final int day) {
    if(cal == null) {
      cal = Calendar.getInstance();
    }
    
    cal.set(Calendar.YEAR, year);
    cal.set(Calendar.MONTH, month);
    cal.set(Calendar.DAY_OF_MONTH, day);
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    
    return cal;
  }
  
  private void importKontoauszug(final File inFile, final Konto konto, final String year, final String month, final String day, String number, String renamePrefix) throws ApplicationException {
    try {
      final Calendar cal = setCalendarDate(Integer.parseInt(year), Calendar.JANUARY, 1);
      
      Kontoauszug last = null;
      
      GenericIterator<?> items = KontoauszugPdfUtil.getList(konto,cal.getTime(),null,false,false);
      
      // find last Kontoauszug for year matched of inFile
      while(items.hasNext()) {
        Kontoauszug test = (Kontoauszug)items.next();
        
        if(last == null || test.getNummer() > last.getNummer()) {
          last = test;
        }
      }
      
      Date bisLast = null;
      Integer nummerLast = new Integer(0);
      
      // if last was found, use it's bis date for calculating von date of next Kontoauszug
      // and get it's number for calculation of next number
      if(last != null) {
        bisLast = last.getBis();
        nummerLast = last.getNummer();
      }
      
      // if last for matched year was not found, we are in a new year,
      // so only use the bis date for calcualation and not the number.
      if(last == null) {
        last = KontoauszugPdfUtil.getNewestWithNumber(konto);
        
        if(last != null) {
          bisLast = last.getBis();
        }
      }
      
      // if matched number is null, either use last number to calculate
      // next number or use the month if nothing else works
      if(number == null && (day == null || bisLast != null)) {
        number = String.valueOf(nummerLast.intValue()+1);
      }
      else if(number == null) {
        number = month;
      }
      
      int num = Integer.parseInt(number);
      int yearInt = Integer.parseInt(year);
      
      boolean known = false;
      
      items.begin();
      
      while(items.hasNext()) {
        last = (Kontoauszug)items.next();
        
        if(last.getJahr() == yearInt && last.getNummer() == num) {
          known = true;
          break;
        }
      }
      
      // only add unknown bank statements
      if(!known) {
        renamePrefix = Placeholder.replace(Placeholder.get(Placeholder.TYPE_YEAR), year, renamePrefix);
        renamePrefix = Placeholder.replace(Placeholder.get(Placeholder.TYPE_NUMBER), number, renamePrefix);
        
        Date endDate = null;
        Date startDate = null;
        
        if(bisLast != null) {
          cal.setTime(bisLast);
          cal.add(Calendar.DAY_OF_YEAR, 1);
          startDate = cal.getTime();
        }
        else if(month != null && day != null) {
          setCalendarDate(cal, yearInt, Integer.parseInt(month)-1, Integer.parseInt(day));
          if(cal.get(Calendar.DAY_OF_MONTH) < 10) {
            cal.add(Calendar.MONTH, -1);
            cal.add(Calendar.DAY_OF_YEAR, cal.get(Calendar.DAY_OF_MONTH)-1);
          }
          
          startDate = cal.getTime();
        }
        else if(month != null) {
          setCalendarDate(cal, yearInt, Integer.parseInt(month)-1, 1);
          startDate = cal.getTime();
        }
        
        if(day != null && month != null) {
          setCalendarDate(cal, yearInt, Integer.parseInt(month)-1, Integer.parseInt(day));
          endDate = cal.getTime();
          renamePrefix = Placeholder.replace(Placeholder.get(Placeholder.TYPE_MONTH), month, renamePrefix);
          renamePrefix = Placeholder.replace(Placeholder.get(Placeholder.TYPE_DAY), day, renamePrefix);
        }
        else if(month != null) {
          setCalendarDate(cal, yearInt, Integer.parseInt(month)-1, 1);
          cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
          endDate = cal.getTime();
          renamePrefix = Placeholder.replace(Placeholder.get(Placeholder.TYPE_MONTH), month, renamePrefix);
        }
        
        Kontoauszug auszug = Settings.getDBService().createObject(Kontoauszug.class,null);
        auszug.setKonto(konto);
        auszug.setJahr(yearInt);
        auszug.setNummer(num);
        
        if(startDate != null) {
          auszug.setVon(startDate);
        }
        if(endDate != null) {
          auszug.setBis(endDate);
        }
        
        String source = konto.getMeta(DialogConfigBankStatement.KEY_DOWNLOAD_PATH, "");
        String path = konto.getMeta(DialogConfigBankStatement.KEY_TARGET_PATH, "");
        
        // move file only if the target path is available and doesn't equals the source path
        if(!path.trim().isEmpty() && !source.equals(path)) {
          File target = new File(path,renamePrefix+inFile.getName());
          
          Files.move(inFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
          
          if(target.isFile()) {
            auszug.setDateiname(target.getName());
            auszug.setPfad(target.getParent());
          }
          else {
            auszug.setDateiname(inFile.getName());
            auszug.setPfad(inFile.getParent());
          }
        }
        else {
          auszug.setDateiname(inFile.getName());
          auszug.setPfad(inFile.getParent());
        }
        
        auszug.store();
        Application.getMessagingFactory().sendMessage(new StatusBarMessage("Kontoauszug angelegt",StatusBarMessage.TYPE_SUCCESS));
        Application.getMessagingFactory().sendMessage(new ImportMessage(auszug));
      }
      
    }catch(Exception e) {e.printStackTrace();}
  }
  
  /**
   * Hilfsklasse, um den Menupunkt zu deaktivieren, wenn die Buchung bereits zugeordnet ist.
   */
  private class MyContextMenuItem extends CheckedContextMenuItem
  {
    /**
     * ct.
     * @param text
     * @param a
     */
    public MyContextMenuItem(String text, Action a)
    {
      super(text, a);
      
    }

    /**
     * @see de.willuhn.jameica.gui.parts.CheckedContextMenuItem#isEnabledFor(java.lang.Object)
     */
    public boolean isEnabledFor(Object o)
    {
      boolean result = false;
      
      // Wenn wir eine ganze Liste von Buchungen haben, pruefen
      // wir nicht jede einzeln, ob sie schon in SynTAX vorhanden
      // ist. Die werden dann beim Import (weiter unten) einfach ausgesiebt.
      if (o instanceof Konto) {
        try {
          final String[] ids = mSettings.getList(DialogConfigBankStatement.KEY_PROPERTY_KONTEN, null);
          
          for(String id : ids) {
            result = id.startsWith(((Konto) o).getKontonummer()) && id.endsWith(((Konto) o).getBLZ());
            
            if(result) {
              break;
            }
          }
        } catch (Exception e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
      
      return result;
    }
    
  }
}
