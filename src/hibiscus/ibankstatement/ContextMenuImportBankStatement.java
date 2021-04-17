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

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.extension.Extendable;
import de.willuhn.jameica.gui.extension.Extension;
import de.willuhn.jameica.gui.parts.CheckedContextMenuItem;
import de.willuhn.jameica.gui.parts.ContextMenu;
import de.willuhn.jameica.gui.parts.ContextMenuItem;
import de.willuhn.jameica.hbci.rmi.Konto;
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
        BankStatementImporter.getInstance().openConfig(context);
      }
    }));
    
    menu.addItem(new MyContextMenuItem("Kontoauszüge importieren...", new Action() {
      @Override
      public void handleAction(Object context) throws ApplicationException {
        BankStatementImporter.getInstance().handleAction(null);
      }
    }));
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
