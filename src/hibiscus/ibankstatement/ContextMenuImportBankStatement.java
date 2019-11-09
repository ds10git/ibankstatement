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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.jollyday.HolidayCalendar;
import de.jollyday.HolidayManager;
import de.jollyday.ManagerParameters;
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
                
                Pattern p = Pattern.compile(pattern.replace("{konto}", konto.getKontonummer()));
                int type[] = null;
                
                {
                  String[] parts = matchingGroups.split(",");
                  
                  for(int i = 0; i < parts.length; i++) {
                    if(!parts[i].trim().isEmpty()) {
                      Placeholder placeholder = Placeholder.get(parts[i]);
                      
                      if(placeholder != null) {
                        if(placeholder.isPlaceholder(Placeholder.TYPE_MONTH) ||
                            placeholder.isPlaceholder(Placeholder.TYPE_DAY)) {
                          type = placeholder.getEndType(parts[i]);
                          
                          if(type != null) {
                            break;
                          }
                        }
                      }
                    }
                  }
                  
                  if(type == null) {
                    type = new int[2];
                    type[0] = Placeholder.TYPE_END_DEFAULT;
                    type[1] = -1;
                  }
                }
                
                final ArrayList<FileInfo> filesFound = new ArrayList<>();
                
                //handle all found bank statements for current Konto
                for(File pdfFile : pdfFiles) {
                  Matcher m = p.matcher(pdfFile.getName());
                  
                  if(m.find()) {
                    final FileInfo info = new FileInfo();
                    
                    String[] parts = matchingGroups.split(",");
                    
                    for(int i = 0; i < parts.length; i++) {
                      if(!parts[i].trim().isEmpty()) {
                        Placeholder placeholder = Placeholder.get(parts[i]);
                        
                        if(placeholder != null) {
                          switch(placeholder.getType()) {
                            case Placeholder.TYPE_YEAR: info.mYear = placeholder.getValue(parts[i], m.group(i+1));break;
                            case Placeholder.TYPE_MONTH: info.mMonth = placeholder.getValue(parts[i], m.group(i+1));break;
                            case Placeholder.TYPE_DAY: info.mDay = placeholder.getValue(parts[i], m.group(i+1));break;
                            case Placeholder.TYPE_NUMBER: info.mNumber = placeholder.getValue(parts[i], m.group(i+1));break;
                          }
                        }
                      }
                    }
                    
                    if(info.mYear != null && (info.mMonth != null || info.mNumber != null)) {
                      info.mPdfFile = pdfFile;
                      filesFound.add(info);
                    }
                  }                  
                }
                
                if(!filesFound.isEmpty()) {
                  // we have to sort the files in order of the bank statement
                  // so the import order matches the order of the bank statements
                  Collections.sort(filesFound);
  
                  for(FileInfo info : filesFound) {
                    // open import of bank statement for current Konto and current pdfFile
                    importKontoauszug(info.mPdfFile, konto, info.mYear, info.mMonth, info.mDay, info.mNumber, konto.getMeta(DialogConfigBankStatement.KEY_RENAME_PREFIX, ""), type[0], type[1]);
                  }
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
  
  private static final Calendar setCalendarDate(Date date) {
    Calendar cal = Calendar.getInstance();
    cal.setTime(date);
    
    return cal;
  }
  
  private void importKontoauszug(final File inFile, final Konto konto, final String year, String month, String day, String number, String renamePrefix, final int typeEndDate, final int endDayOfWeek) throws ApplicationException {
    try {
      if(month == null) {
        Calendar tmp = Calendar.getInstance();
        
        if(number != null) {
          try {
            int yearValue = Integer.parseInt(year);
            int test = Integer.parseInt(number);
            
            if(test > 0 && test < 13) {
              tmp = setCalendarDate(tmp, yearValue, test-1, 1);
              tmp.set(Calendar.DAY_OF_MONTH, tmp.getActualMaximum(Calendar.DAY_OF_MONTH));
            }
            
          }catch(NumberFormatException nfe) {
            nfe.printStackTrace();
          }
        }
        
        DialogDateSelection config = new DialogDateSelection(DialogDateSelection.TYPE_DATE_END,inFile,tmp.getTime(),konto);
        
        try {
          config.open();
          
          if(config.getData() != null) {
            tmp.setTime(config.getData());
            month = String.valueOf(tmp.get(Calendar.MONTH)+1);
            day = String.valueOf(tmp.get(Calendar.DAY_OF_MONTH));
          }
          else {
            return;
          }
        } catch (Exception e) {
          e.printStackTrace();
          return;
        }
      }
      
      final Calendar cal = setCalendarDate(Integer.parseInt(year), Calendar.JANUARY, 1);
      
      final Calendar cal2 = setCalendarDate(Integer.parseInt(year), Calendar.DECEMBER, 31);
      
      Kontoauszug last = null;
      
      GenericIterator<?> items = KontoauszugPdfUtil.getList(konto,cal.getTime(),cal2.getTime(),false,true);
      
      // find last Kontoauszug for year matched of inFile
      while(items.hasNext()) {
        Kontoauszug test = (Kontoauszug)items.next();
        
        // known Kontoauszug, no need to add
        if(test.getDateiname().endsWith(inFile.getName())) {
          return;
        }
        
        if(last == null || (test.getNummer() > last.getNummer() && test.getVon() != null && last.getVon() != null && last.getVon().compareTo(test.getVon()) <= 0)) {
          last = test;
        }
      }
      
      Date bisLast = null;
      Integer nummerLast = new Integer(0);
      
      // if last was found, use it's bis date for calculating von date of next Kontoauszug
      // and get it's number for calculation of next number
      if(last != null) {
        bisLast = last.getBis();
        
        if(last.getVon() == null || setCalendarDate(last.getVon()).get(Calendar.YEAR) == Integer.parseInt(year)) {
          nummerLast = last.getNummer();
        }
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
        if(month != null) {
          try {
            int monthVal = Integer.parseInt(month);
            
            if(monthVal < 1) {
              month = "1";
            }else if(monthVal > 12) {
              month = "12";
            }
          }catch(NumberFormatException nfe) {}
        }
        
        renamePrefix = Placeholder.replace(Placeholder.get(Placeholder.TYPE_YEAR), yearInt, renamePrefix);
        renamePrefix = Placeholder.replace(Placeholder.get(Placeholder.TYPE_NUMBER), num, renamePrefix);
        
        Date endDate = null;
        Date startDate = null;
        
        if(bisLast != null) {
          cal.setTime(bisLast);
          cal.add(Calendar.DAY_OF_YEAR, 1);
          startDate = cal.getTime();
        }
        else if(month != null && day != null) {
          int dayVal = Integer.parseInt(day);
          setCalendarDate(cal, yearInt, Integer.parseInt(month)-1, 1);
          
          if(dayVal < 1) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
          }
          else if(dayVal > cal.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            cal.add(Calendar.DAY_OF_YEAR, 1);
          }
          else {
            cal.set(Calendar.DAY_OF_MONTH, dayVal);
          }
          
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
          int dayVal = Integer.parseInt(day);
          setCalendarDate(cal, yearInt, Integer.parseInt(month)-1, 1);
          
          if(dayVal < 1) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
          }
          else if(dayVal > cal.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            cal.add(Calendar.DAY_OF_YEAR, 1);
          }
          else {
            cal.set(Calendar.DAY_OF_MONTH, dayVal);
          }
          
          endDate = cal.getTime();
          renamePrefix = Placeholder.replace(Placeholder.get(Placeholder.TYPE_MONTH), cal.get(Calendar.MONTH)+1, renamePrefix);
          renamePrefix = Placeholder.replace(Placeholder.get(Placeholder.TYPE_DAY), cal.get(Calendar.DAY_OF_MONTH), renamePrefix);
        }
        else if(month != null) {
          setCalendarDate(cal, yearInt, Integer.parseInt(month)-1, 1);
          cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
          endDate = cal.getTime();
          renamePrefix = Placeholder.replace(Placeholder.get(Placeholder.TYPE_MONTH), cal.get(Calendar.MONTH)+1, renamePrefix);
        }
        
        if(startDate != null && endDate != null && startDate.equals(endDate)) {
           DialogDateSelection config = new DialogDateSelection(DialogDateSelection.TYPE_DATE_START,inFile,startDate,konto);
           
           try {  
            config.open();
            startDate = config.getData();
          }catch(Exception e) {
            e.printStackTrace();
          }
        }
        
        Kontoauszug auszug = Settings.getDBService().createObject(Kontoauszug.class,null);
        auszug.setKonto(konto);
        auszug.setJahr(yearInt);
        auszug.setNummer(num);
        
        if(startDate != null) {
          if(endDate != null && startDate.after(endDate)) {
            auszug.setVon(endDate);
          }
          else {
            auszug.setVon(startDate);
          }
        }
        if(endDate != null) {
          if(typeEndDate == Placeholder.TYPE_END_LAST_WEEKDAY_OF_MONTH || konto.getMeta(DialogConfigBankStatement.LEGACY_KEY_ALWAYS_ON_WEEKDAY_END, "false").equals("true")) {
            cal.setTime(endDate);
            
            if(cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
              cal.add(Calendar.DAY_OF_MONTH, -1);
            }
            else if(cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
              cal.add(Calendar.DAY_OF_MONTH, -2);
            }
            
            HolidayManager m = HolidayManager.getInstance(ManagerParameters.create(HolidayCalendar.GERMANY));
            
            while(m.isHoliday(cal)) {
              cal.add(Calendar.DAY_OF_MONTH, -1);
            }
            
            endDate = cal.getTime();
          }
          else if(typeEndDate == Placeholder.TYPE_END_DAY_OF_WEEK) {
            cal.setTime(endDate);
            
            int diff = 0;
            
            diff = endDayOfWeek - cal.get(Calendar.DAY_OF_WEEK);
            
            if(endDayOfWeek == Calendar.SUNDAY && diff != 0) {
              diff = 8 - cal.get(Calendar.DAY_OF_WEEK);
            }
            
            cal.add(Calendar.DAY_OF_YEAR, diff);
            
            endDate = cal.getTime();
          }
          
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
  
  private static final class FileInfo implements Comparable<FileInfo> {
    private File mPdfFile;
    
    private String mYear;
    private String mMonth;
    private String mDay;
    private String mNumber;
    
    private int getYear() {
      return Integer.parseInt(mYear);
    }

    private Integer getMonth() {
      if(mMonth != null) {
        return Integer.parseInt(mMonth);
      }
      
      return null;
    }
    
    private Integer getDay() {
      if(mDay != null) {
        return Integer.parseInt(mDay);
      }
      
      return null;
    }
    
    private Integer getNumber() {
      if(mNumber != null) {
        return Integer.parseInt(mNumber);
      }
      
      return null;
    }
    
    @Override
    public int compareTo(FileInfo o) {
      Integer month = getMonth();
      Integer oMonth = o.getMonth();
      
      Integer day = getDay();
      Integer oDay = o.getDay();

      Integer number = getNumber();
      Integer oNumber = o.getNumber();

      if(getYear() < o.getYear()) {
        return -1;
      }
      else if(getYear() > o.getYear()) {
        return 1;
      }
      else if(month != null && oMonth != null && day != null && oDay != null && number != null && oNumber != null) {
        if(month < oMonth) {
          return -1;
        }
        else if(month > oMonth) {
          return 1;
        }
        else if(day < oDay) {
          return -1;
        }
        else if(day > oDay) {
          return 1;
        }
        else if(number < oNumber) {
          return -1;
        }
        else if(number > oNumber) {
          return 1;
        }
        else if(mPdfFile.lastModified() > o.mPdfFile.lastModified()) {
          return -1;
        }
        else if(mPdfFile.lastModified() < o.mPdfFile.lastModified()) {
          return 1;
        }
        else {
          return 0;
        }
      }
      else if(month != null && oMonth != null && day != null && oDay != null) {
        if(month < oMonth) {
          return -1;
        }
        else if(month > oMonth) {
          return 1;
        }
        else if(day < oDay) {
          return -1;
        }
        else if(day > oDay) {
          return 1;
        }
        else if(mPdfFile.lastModified() > o.mPdfFile.lastModified()) {
          return -1;
        }
        else if(mPdfFile.lastModified() < o.mPdfFile.lastModified()) {
          return 1;
        }
        else {
          return 0;
        }
      }
      else if(month != null && oMonth != null && number != null && oNumber != null) {
        if(month < oMonth) {
          return -1;
        }
        else if(month > oMonth) {
          return 1;
        }
        else if(number < oNumber) {
          return -1;
        }
        else if(number > oNumber) {
          return 1;
        }
        else if(mPdfFile.lastModified() > o.mPdfFile.lastModified()) {
          return -1;
        }
        else if(mPdfFile.lastModified() < o.mPdfFile.lastModified()) {
          return 1;
        }
        else {
          return 0;
        }
      }
      else if(number != null && oNumber != null) {
        if(number < oNumber) {
          return -1;
        }
        else if(number > oNumber) {
          return 1;
        }
        else if(mPdfFile.lastModified() > o.mPdfFile.lastModified()) {
          return -1;
        }
        else if(mPdfFile.lastModified() < o.mPdfFile.lastModified()) {
          return 1;
        }
        else {
          return 0;
        }
      }
      else if(mPdfFile.lastModified() > o.mPdfFile.lastModified()) {
        return -1;
      }
      else if(mPdfFile.lastModified() < o.mPdfFile.lastModified()) {
        return 1;
      }
      
      return 0;
    }
  }
}
