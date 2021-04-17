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
    BankStatementImporter.getInstance().handleMessage((TextMessage)message);
  }

  @Override
  public boolean autoRegister() {
    return false;
  }

}
