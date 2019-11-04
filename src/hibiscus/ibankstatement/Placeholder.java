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
  
  public boolean isPlaceholder(final String name) {
    return mName.equals(name) || mAliases.contains(name);
  }
  
  public boolean isPlaceholder(final int type) {
    return mType == type;
  }
  
  public int getType() {
    return mType;
  }
  
  public static String replace(final Placeholder search, String replace, String subject) {
    if(replace.length() == 1) {
      replace = "0" + replace;
    }
    
    subject = subject.replace(search.mName, replace);
    
    for(String test : search.mAliases) {
      subject = subject.replace(test, replace);
    }
    
    return subject;
  }
  
  public static boolean contains(final String name) {
    boolean result = false;
    
    for(Placeholder h : PLACEHOLDER) {
      if(h.isPlaceholder(name)) {
        result = true;
        break;
      }
    }
    
    return result;
  }
  
  public static Placeholder get(final String name) {
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
