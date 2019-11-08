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

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class with placeholders supported by the matching/renaming.
 * @author René Mach
 */
public class Placeholder {
  static final int TYPE_YEAR = 1;
  static final int TYPE_MONTH = 2;
  static final int TYPE_DAY = 3;
  static final int TYPE_NUMBER = 4;
  
  static final Placeholder[] PLACEHOLDER= {
      new Placeholder(Placeholder.TYPE_YEAR, "{year}", "{jahr}"),
      new Placeholder(Placeholder.TYPE_MONTH, "{month}", "{monat}"),
      new Placeholder(Placeholder.TYPE_DAY, "{day}", "{tag}"),
      new Placeholder(Placeholder.TYPE_NUMBER, "{number}", "{nummer}")
  };
  
  private int mType;
  private String mName;
  private HashSet<String> mAliases;
  
  private Placeholder(final int type, final String name, final String... aliases) {
    mAliases = new HashSet<>();
    mType = type;
    mName = name;
    
    if(aliases != null) {
      for(final String alias : aliases) {
        mAliases.add(alias);
      }
    }
  }
  
  public void addAlias(final String name) {
    mAliases.add(name);
  }
  
  public boolean isPlaceholder(String name) {
    name = name.replaceAll("\\{(\\p{Lower}*?)_.*?\\}", "{$1}");
    
    return mName.equals(name) || mAliases.contains(name);
  }
  
  public boolean isPlaceholder(final int type) {
    return mType == type;
  }
  
  public int getType() {
    return mType;
  }
  
  private String getPattern() {
    StringBuilder b = new StringBuilder("(\\{(?:");
    
    b.append(mName.replace("{", "").replace("}", ""));
    
    for(String test : mAliases) {
      b.append("|");
      b.append(test.replace("{", "").replace("}", ""));
    }
    
    b.append(")(.*?)\\})");
    
    return b.toString();
  }
  
  public String getValue(String subject, String replace) {
    try {
      final Matcher m = Pattern.compile(getPattern()).matcher(subject);
      
      if(m.find()) {
        String group2 = m.group(2);
        
        if(!group2.trim().isEmpty()) {
          try {
            int value = Integer.parseInt(group2.substring(1).trim());
            int toCalc = Integer.parseInt(replace);
            
            replace = String.valueOf(toCalc + value);
          }catch(NumberFormatException nfe) {
            // ignore
          }
        }
      }
      
    }catch(NumberFormatException nfe) {
      nfe.printStackTrace();
    }
    
    return replace;
  }
  
  public static String replace(final Placeholder search, int replace, String subject) {
    final Matcher m = Pattern.compile(search.getPattern()).matcher(subject);
    
    int pos = 0;
    
    String result = subject;
    
    while(m.find(pos)) {
      String group1 = m.group(1);
      String group2 = m.group(2);
      
      if(!group2.trim().isEmpty()) {
        try {
          int value = Integer.parseInt(group2.substring(1).trim());
          
          replace += value;
        }catch(NumberFormatException nfe) {
          nfe.printStackTrace();
        }
      }
      
      result = result.replace(group1, (replace < 10 ? "0" : "") + replace);
      
      pos = m.end();
    }
    
    return result;
  }
    
  public static String replace(final Placeholder search, String replace, String subject) {
    if(replace.length() == 1) {
      replace = "0" + replace;
    }
    
    subject = subject.replaceAll(search.getPattern(), replace);
    
    return subject;
  }
  
  public boolean textContainsMe(String text) {
    text = text.replaceAll("\\{(\\p{Lower}*?)_.*?\\}", "{$1}");
    
    boolean result = text.contains(mName);
    
    if(!result) {
      for(String alias : mAliases) {
        if(text.contains(alias)) {
          result = true;
          break;
        }
      }
    }
    
    return result;
  }
  
  public static boolean contains(String name) {
    name = name.replaceAll("\\{(\\p{Lower}*?)_.*?\\}", "{$1}");
    
    boolean result = false;
    
    for(Placeholder h : PLACEHOLDER) {
      if(h.isPlaceholder(name)) {
        result = true;
        break;
      }
    }
    
    return result;
  }
  
  public static Placeholder get(String name) {
    name = name.replaceAll("\\{(\\p{Lower}*?)_.*?\\}", "{$1}");
    
    Placeholder result = null;
    
    for(Placeholder h : PLACEHOLDER) {
      if(h.isPlaceholder(name)) {
        result = h;
        break;
      }
    }
    
    return result;
  }
  
  public static Placeholder get(final int type) {
    Placeholder result = null;
    
    for(Placeholder h : PLACEHOLDER) {
      if(h.isPlaceholder(type)) {
        result = h;
        break;
      }
    }
    
    return result;
  }
}
