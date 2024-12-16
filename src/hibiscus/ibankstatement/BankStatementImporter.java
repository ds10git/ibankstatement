package hibiscus.ibankstatement;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
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
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.gui.filter.KontoFilter;
import de.willuhn.jameica.hbci.messaging.ImportMessage;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Kontoauszug;
import de.willuhn.jameica.hbci.server.KontoUtil;
import de.willuhn.jameica.hbci.server.KontoauszugPdfUtil;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.messaging.TextMessage;
import de.willuhn.jameica.system.Application;
import de.willuhn.util.ApplicationException;
import hibiscus.ibankstatement.Placeholder.DateConfiguration;


/**
 * A class that does the importing of bank statements.
 * 
 * @author René Mach
 */
public class BankStatementImporter {
  private de.willuhn.jameica.system.Settings mSettings = new de.willuhn.jameica.system.Settings(ContextMenuImportBankStatement.class);
  private static BankStatementImporter INSTANCE;
  
  private BankStatementImporter() {
    INSTANCE = this;
  }
  
  public static synchronized BankStatementImporter getInstance() {
    if(INSTANCE == null) {
      new BankStatementImporter();
    }
    
    return INSTANCE;
  }
  
  public void openConfig(Object context) {
    //open config dialog for Konto the context menu was opened for
    DialogConfigBankStatement config = new DialogConfigBankStatement(mSettings, (Konto)context);
    try {
      config.open();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  public void handleMessage(TextMessage message) {
    final String[] parts = message.getText().split("\n");
    final ArrayList<Import> imports = new ArrayList<>();
    
    for(String part : parts) {
      Import i = Import.getImport(part);
      
      if(i != null && mSettings.getString(i.mFile.getAbsolutePath(), null) == null) {
        imports.add(i);
        mSettings.setAttribute(i.mFile.getAbsolutePath(), "1");
      }
    }
    
    if(!imports.isEmpty()) {
      handleAction(imports);
    }
  }
  
  public void handleAction(ArrayList<Import> imports) {
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
            final String search = Placeholder.get(Placeholder.TYPE_ID_USER).replaceKontoDaten(konto.getKundennummer(), Placeholder.get(Placeholder.TYPE_ACCOUNT).replaceKontoDaten(konto.getKontonummer(), pattern));
            
            final ArrayList<Import> pdfFiles = new ArrayList<>();
            
            //find all files that match the bank statement pattern for the current Konto
            if(imports == null || imports.isEmpty()) {
              File[] files = new File(konto.getMeta(DialogConfigBankStatement.KEY_DOWNLOAD_PATH, System.getProperty("user.home")+File.separator+"Downloads")).listFiles((FileFilter)file -> {
                return Pattern.matches(search, file.getName());
              });
              
              if(files != null) {
                Arrays.sort(files);
                
                for(File f : files) {
                  Import i = new Import();
                  i.mFile = f;
                  i.mMove = true;
                  
                  pdfFiles.add(i);
                }
              }
            }
            else {
              for(int i = imports.size()-1; i >= 0; i--) {
                if(imports.get(i).matches(konto,search)) {
                  pdfFiles.add(0,imports.get(i));
                  imports.remove(i);
                }
              }
            }
            
            if(pdfFiles == null || pdfFiles.isEmpty()) {
              continue;
            }
            
            Pattern p = Pattern.compile(search);
            Placeholder.DateConfiguration dateConfiguration = null;
            
            {
              String[] parts = matchingGroups.split(",");
              
              for(int i = 0; i < parts.length; i++) {
                if(!parts[i].trim().isEmpty()) {
                  Placeholder placeholder = Placeholder.get(parts[i]);
                  
                  if(placeholder != null) {
                    if(placeholder.isPlaceholder(Placeholder.TYPE_MONTH) ||
                        placeholder.isPlaceholder(Placeholder.TYPE_NUMBER) ||
                        placeholder.isPlaceholder(Placeholder.TYPE_DAY)) {
                      dateConfiguration = placeholder.getDateConfiguration(parts[i]);
                      
                      if(!dateConfiguration.isDefaultType()) {
                        break;
                      }
                    }
                  }
                }
              }
            }
            
            final ArrayList<FileInfo> filesFound = new ArrayList<>();
            
            //handle all found bank statements for current Konto
            for(Import pdfFile : pdfFiles) {
              Matcher m = p.matcher(pdfFile.mFile.getName());
              
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
                        case Placeholder.TYPE_NUMBER_YEAR: info.mNumberYear = placeholder.getValue(parts[i], m.group(i+1));break;
                      }
                    }
                  }
                }
                
                if((info.mYear != null || info.mNumberYear != null) && (info.mMonth != null || info.mNumber != null)) {
                  info.mPdfFile = pdfFile.mFile;
                  info.mMove = pdfFile.mMove;
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
                importKontoauszug(info.mPdfFile, konto, info, konto.getMeta(DialogConfigBankStatement.KEY_RENAME_PREFIX, ""), dateConfiguration, info.mMove);
              }
            }
          }
        } catch (Exception e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        }
       
      }
    }
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
  
  private void importKontoauszug(final File inFile, final Konto konto, FileInfo info, String renamePrefix, final DateConfiguration dateConfiguration, boolean allowedToMove) throws ApplicationException {
    String year = info.mYear != null ? info.mYear : info.mNumberYear;
    String month = info.mMonth;
    String day = info.mDay;
    String number = info.mNumber;
    
    try {
      if(dateConfiguration.isEndType(Placeholder.TYPE_END_DAY_OF_MONTH)) {
        day = String.valueOf(dateConfiguration.getEndDayValue());
      }
      
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
      Integer nummerLast = 0;
      
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
      int yearInt = Integer.parseInt(info.mNumberYear != null ? info.mNumberYear : year);
      
      items.begin();
      
      while(items.hasNext()) {
        last = (Kontoauszug)items.next();
        
        if(last.getJahr() == yearInt && last.getNummer() == num) {
          num++;
          break;
        }
      }
      
      yearInt = Integer.parseInt(year);
      
      if(month != null) {
        try {
          int monthVal = Integer.parseInt(month);
          
          if(monthVal < 1) {
            month = "12";
            yearInt = Integer.parseInt(year)-1;
            year = String.valueOf(yearInt);
            
            if(last != null) {
              num = last.getNummer()+1;
            }
          }else if(monthVal > 12) {
            month = "1";
            yearInt = Integer.parseInt(year)+1;
            year = String.valueOf(yearInt);
            num = 1;
          }
        }catch(NumberFormatException nfe) {}
      }
      
      renamePrefix = Placeholder.replace(Placeholder.get(Placeholder.TYPE_YEAR), yearInt, renamePrefix);
      renamePrefix = Placeholder.replace(Placeholder.get(Placeholder.TYPE_NUMBER), num, renamePrefix);
      
      Date endDate = null;
      Date startDate = null;
      
      if(bisLast != null) {
        cal.setTime(bisLast);
        
        if(!dateConfiguration.isStartType(Placeholder.TYPE_START_ON_LAST_DATE)) {
          cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        
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
          
          if(!dateConfiguration.isEndType(Placeholder.TYPE_END_DAY_OF_MONTH)) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
          }
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
      
      if((startDate == null || (startDate != null && endDate != null && startDate.equals(endDate))) && !dateConfiguration.isStartType(Placeholder.TYPE_START_DAY_OF_MONTH)) {
         DialogDateSelection config = new DialogDateSelection(DialogDateSelection.TYPE_DATE_START,inFile,endDate,konto);
         
         try {  
          config.open();
          startDate = config.getData();
        }catch(Exception e) {
          e.printStackTrace();
        }
      }
      
      Kontoauszug auszug = Settings.getDBService().createObject(Kontoauszug.class,null);
      auszug.setKonto(konto);
      
      auszug.setJahr(info.getNumberYear() != null ? info.getNumberYear() : yearInt);
      auszug.setNummer(info.getNumber() != null ? info.getNumber() : num);
      
      if(endDate != null && dateConfiguration.isStartType(Placeholder.TYPE_START_DAY_OF_MONTH)) {
        cal.setTime(endDate);
        cal.add(Calendar.DAY_OF_YEAR, -cal.get(Calendar.DAY_OF_MONTH)-1);
        
        if(dateConfiguration.getStartDayValue() <= cal.getActualMaximum(Calendar.DAY_OF_MONTH)) {
          cal.set(Calendar.DAY_OF_MONTH, dateConfiguration.getStartDayValue());
          
          if((endDate.getTime()-cal.getTimeInMillis()) > 31 * 24 * 60 * 60000l+1) {
            cal.setTime(endDate);
            
            if(dateConfiguration.getStartDayValue() <= cal.getActualMaximum(Calendar.DAY_OF_MONTH)) {
              cal.set(Calendar.DAY_OF_MONTH, dateConfiguration.getStartDayValue());
              
              startDate = cal.getTime();
            }
          }
          else {
            startDate = cal.getTime();
          }
        }
      }
      
      if(startDate != null) {
        if(endDate != null && startDate.after(endDate)) {
          auszug.setVon(endDate);
        }
        else {
          auszug.setVon(startDate);
        }
      }
      if(endDate != null) {
        if(dateConfiguration.isEndType(Placeholder.TYPE_END_LAST_WEEKDAY_OF_MONTH) || konto.getMeta(DialogConfigBankStatement.LEGACY_KEY_ALWAYS_ON_WEEKDAY_END, "false").equals("true")) {
          cal.setTime(endDate);
          
          if(cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
          }
          else if(cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_MONTH, -2);
          }
          
          HolidayManager m = HolidayManager.getInstance(ManagerParameters.create(HolidayCalendar.GERMANY));
          
          while(m.isHoliday(cal) || (cal.get(Calendar.MONTH) == Calendar.DECEMBER) && (cal.get(Calendar.DAY_OF_MONTH) == 24 || cal.get(Calendar.DAY_OF_MONTH) == 31)) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
          }
          
          endDate = cal.getTime();
        }
        else if(dateConfiguration.isEndType(Placeholder.TYPE_END_DAY_OF_WEEK)) {
          cal.setTime(endDate);
          
          int diff = 0;
          
          diff = dateConfiguration.getEndDayValue() - cal.get(Calendar.DAY_OF_WEEK);
          
          if(dateConfiguration.getEndDayValue() == Calendar.SUNDAY && diff != 0) {
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
        
        items.begin();
        
        // again check if Kontoauszug is known
        while(items.hasNext()) {
          Kontoauszug test = (Kontoauszug)items.next();
          
          // known Kontoauszug, no need to add
          if(test.getDateiname().endsWith(target.getName())) {
            return;
          }
        }
        
        if(!target.getParentFile().exists()) {
          target.getParentFile().mkdirs();
        }
        
        if(allowedToMove) {
          Files.move(inFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        else {
          Files.copy(inFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        
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
    }catch(Exception e) {e.printStackTrace();}
  }
  
  private static final class FileInfo implements Comparable<FileInfo> {
    private File mPdfFile;
    
    private String mYear;
    private String mMonth;
    private String mDay;
    private String mNumber;
    private String mNumberYear;
    private boolean mMove;
    
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
    
    private Integer getNumberYear() {
      if(mNumberYear != null) {
        return Integer.parseInt(mNumberYear);
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

      Integer numberYear = getNumberYear();
      Integer oNumberYear = o.getNumberYear();
      
      boolean next = false;
      
      if(getYear() < o.getYear()) {
        return -1;
      }
      else if(getYear() > o.getYear()) {
        return 1;
      }
      else if(numberYear != null && oNumberYear != null && number != null && oNumber != null) {
        if(numberYear < oNumberYear) {
          return -1;
        }
        else if(numberYear > oNumberYear) {
          return 1;
        }
        else if(number < oNumber) {
          return -1;
        }
        else if(number > oNumber) {
          return 1;
        }
        else {
          next = true;
        }
      }
      
      if(next) {
        if(month != null && oMonth != null && day != null && oDay != null && number != null && oNumber != null) {
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
          else if(mPdfFile.lastModified() < o.mPdfFile.lastModified()) {
            return -1;
          }
          else if(mPdfFile.lastModified() > o.mPdfFile.lastModified()) {
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
          else if(mPdfFile.lastModified() < o.mPdfFile.lastModified()) {
            return -1;
          }
          else if(mPdfFile.lastModified() > o.mPdfFile.lastModified()) {
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
          else if(mPdfFile.lastModified() < o.mPdfFile.lastModified()) {
            return -1;
          }
          else if(mPdfFile.lastModified() > o.mPdfFile.lastModified()) {
            return 1;
          }
          else {
            return 0;
          }
        }
        else if(month != null && oMonth != null) {
          if(month < oMonth) {
            return -1;
          }
          else if(month > oMonth) {
            return 1;
          }
          else if(mPdfFile.lastModified() < o.mPdfFile.lastModified()) {
            return -1;
          }
          else if(mPdfFile.lastModified() > o.mPdfFile.lastModified()) {
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
          else if(mPdfFile.lastModified() < o.mPdfFile.lastModified()) {
            return -1;
          }
          else if(mPdfFile.lastModified() > o.mPdfFile.lastModified()) {
            return 1;
          }
          else {
            return 0;
          }
        }
        else if(mPdfFile.lastModified() < o.mPdfFile.lastModified()) {
          return -1;
        }
        else if(mPdfFile.lastModified() > o.mPdfFile.lastModified()) {
          return 1;
        }
      }
      
      return 0;
    }
    
    @Override
    public String toString() {
      return mPdfFile != null ? mPdfFile.getName() : "NO_FILE" + " Year: " +getYear() + " Month: " + getMonth()+" Day: "+getDay()+" Number: "+getNumber();
    }
  }
  
  private static final class Import {
    private String mIBAN;
    private String mKontoNummer;
    private String mBIC;
    private File mFile;
    private boolean mMove;
    
    private Import() {}
    
    private static Import getImport(String text) {
      Import result = new Import();
      
      String[] parts = text.split(";");
      
      int kIndex = -1;
      int pIndex = -1;
      int aIndex = -1;
      
      if(parts.length == 1) {
        pIndex = 0;
      }
      else if(parts.length == 3) {
        kIndex = 0;
        pIndex = 1;
        aIndex = 2;
      }
      else if(parts.length == 2) {
        if(parts[0].toLowerCase().endsWith(".pdf")) {
          pIndex = 0;
          aIndex = 1;
        }
        else if(parts[1].toLowerCase().endsWith(".pdf")) {
          kIndex = 0;
          pIndex = 1;
        }
      }
      
      if(kIndex != -1) {
        if(parts[kIndex].contains(":")) {
          String[] subParts = parts[kIndex].split(":");
          
          result.mBIC = subParts[0];
          result.mKontoNummer = subParts[1];
        }
        else {
          result.mIBAN = parts[kIndex].replaceAll("\\s+", "");
        }
      }
      
      if(aIndex != -1) {
        result.mMove = parts[aIndex].toLowerCase().equals("m");
      }
      else {
        result.mMove = false;
      }
      
      if(pIndex != -1) {
        result.mFile = new File(parts[pIndex]);
      }
      
      if(result.mFile == null || !result.mFile.isFile() || !result.mFile.getName().toLowerCase().endsWith(".pdf")) {
        result = null;
      }
      
      return result;
    }
    
    boolean matches(Konto k, String search) throws RemoteException {
      boolean result = (mIBAN == null && mBIC == null && mKontoNummer == null) 
          || (mIBAN != null && k.getIban().replaceAll("\\s+", "").equals(mIBAN))
          || (mBIC != null && mKontoNummer != null && k.getBic().equals(mBIC) && k.getKontonummer().equals(mKontoNummer));
      
      if(result && search != null) {
        result = Pattern.matches(search, mFile.getName());
      }
      return result;
    }
  }
}
