package hibiscus.ibankstatement;

import de.willuhn.jameica.messaging.Message;
import de.willuhn.jameica.messaging.MessageConsumer;
import de.willuhn.jameica.messaging.TextMessage;
/**
 * A class that consumes TextMessages of the named queue hibiscus.ibankstatement  
 * 
 * @author René Mach
 */
public class BankStatementMessageConsumer implements MessageConsumer {

  @SuppressWarnings("rawtypes")
  @Override
  public Class[] getExpectedMessageTypes() {
    return new Class[]{TextMessage.class};
  }

  @Override
  public void handleMessage(Message message) throws Exception {
    if(message == null) {
      return;
    }
    
    // TextMessage needs to contain a list of file paths separated by \n
    // with the oldest bank statement at the top. It's possible to restrict
    // the import to only a specific account, therefor the path has to be preceded
    // by the IBAN or a combination of BIC:Kontonummer separated by ; from the path.
    // It's also possible to control if the file should be moved instead of copied,
    // to move the file path has to be succeeded by m separated by ; from the path.
    //
    // Examples:
    // new TextMessage("/tmp/Kontoauszug_01_2021.pdf\n/tmp/Kontoauszug_02_2021.pdf")
    // new TextMessage("DE07123412341234123412;/tmp/Kontoauszug_01_2021.pdf;m\nBELADEBEXXX:123456789;/tmp/Kontoauszug_02_2021.pdf")
    BankStatementImporter.getInstance().handleMessage((TextMessage)message);
  }

  @Override
  public boolean autoRegister() {
    return false;
  }

}
