/*
 * jPOS Project [http://jpos.org]
 * Copyright (C) 2000-2014 Alejandro P. Revilla
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.jpos.q2.iso;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Date;
import java.util.Observable;
import java.util.Observer;
import org.jdom.Element;
import org.jpos.core.ConfigurationException;
import org.jpos.iso.ISOChannel;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOServer;
import org.jpos.space.Space;
import org.jpos.space.SpaceUtil;
import org.jpos.util.Loggeable;
import org.jpos.util.NameRegistrar;
import org.jpos.util.WatchDog2;

/**
 * Introduce to QServer additional support for sending SignOn, SignOff, Echo
 * messages, idle time measurment and "${server-name}.ready" flag service (for QMUX isConnected())
 *
 * @author Robert Demski
 * @version $Revision: 2880 $ $Date: 2014-07-02 11:04:12 +0200 (śro) $
 */
public class ManagedQServer extends QServer
    implements ManagedQServerMBean, Loggeable {

    ServerChannelObserver   chanSrvObs;
    ISOServer               server;

    @Override
    public void startService() {

        super.startService();

        server = (ISOServer)NameRegistrar.getIfExists("server."+getName());
        chanSrvObs = new ServerChannelObserver( sp, getPersist() );
        if (server != null)
            server.addObserver(chanSrvObs);

    }

    @Override
    public void stopService() {
        if (server != null){
            chanSrvObs.onDisconnect();
            server.deleteObserver(chanSrvObs);
        }
        // QServer adds himself to the TSpace listeners at startService but he
        // doesnt remove himself at stopService.
        // Undeployed MonitorQServer was TSpace listener and consumed messages.
        String inQueue = getPersist().getChildText("in");
        if (inQueue != null)
            sp.removeListener(inQueue, this);
        super.stopService();
    }

    private static String grabString(Element e, String def) {
        if ( e == null )
            return def;
        String childValue = e.getTextTrim();
        return childValue != null ? childValue:def;
    }

    private static long grabLong(Element e, long def) {
        return Long.parseLong(grabString(e,Long.toString(def)));
    }

    @Override
    public void sendSession() {
        chanSrvObs.sendSession();
    }

    @Override
    public void sendSignOn() {
        chanSrvObs.sendSignOn();
    }

    @Override
    public void sendSignOff() {
        chanSrvObs.sendSignOff();
    }

    @Override
    public void sendEcho() {
        chanSrvObs.sendEcho();
    }

    @Override
    public long getIdleTimeInMillis() {
        return chanSrvObs!=null?chanSrvObs.getIdleTimeInMilis():-1L;
    }

    @Override
    public long getLastTxnTimestampInMillis() {
        return chanSrvObs!=null?chanSrvObs.getLastTxnTimestampInMillis():0L;
    }

    @Override
    public boolean isSigned() {
        return chanSrvObs!=null?chanSrvObs.isSigned():false;
    }

    @Override
    public void setSigned(boolean signed ) {
        if (signed)
            sendSignOn();
        else
            sendSignOff();
    }

    @Override
    public void dump(PrintStream p, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("connections=").append(server!=null?server.getConnectionCount():"");
        sb.append(", signed=").append(isSigned());
        sb.append(", idletime=").append(getIdleTimeInMillis());
        p.println (indent + sb.toString());
    }    

  private class ServerChannelObserver implements Observer {

      ISOServer     server;
      NetMngHelper  hlp;
      Space         sp;
      Element       persist;
      //Channel properties
      WatchDog2     watchdog;
      long          lastTxn;

      ServerChannelObserver(Space sp, Element persist) {

          this.sp        = sp;
          this.persist   = persist;

          try {
              String mux = persist.getChildTextTrim("mux");
              if (mux == null)
                  throw new ConfigurationException("'mux' tag is not set");

              hlp = new NetMngHelper(mux);
              hlp.initBSH(getServer(), getLog(), getPersist());
              String[] acptRCs = cfg.getAll("net-manage-acpt-rc");
              hlp.setAcceptedRCs(Arrays.asList(acptRCs));
              hlp.initMessages(persist.getChild ("messages"));

          } catch (Exception e) {
              getLog().warn ("error starting service", e);
          }

      }

      private String getReadyQueueName() {
          return server.getName()+".ready";
      }

      @Override
      public void update(Observable o, Object arg) {

          if ( arg != null )
              if ( arg instanceof ISOMsg && watchdog!=null) {
                  //Just tick activity for message
                  watchdog.tick();
                  lastTxn = System.currentTimeMillis();
                  return;
              }

          server = (ISOServer)o;
          ISOChannel ch = server.getLastConnectedISOChannel();
          if ( ch == null )
              return; //It's shouldn't happend
          synchronized(this) {
              boolean wasConnected = sp.rdp(getReadyQueueName())!=null;
              if ( ch.isConnected() && !wasConnected )
                  onConnect();
              else if ( !ch.isConnected() && wasConnected )
                  onDisconnect();
          }
      }

      void onConnect() {
          if ( sp.rdp (getReadyQueueName()) == null )
              //Mark muxed channel as conected
              sp.out (getReadyQueueName(), new Date());
          log.info("Channel "+server.getName()+" marked as connected");

          watchdog = new WatchDog2 (
              new Runnable() {
                  @Override
                  public void run() {
                      sendEcho();
                  }
              }, grabLong(persist.getChild("max-idle-time"), 900000L)
          );

          if (hlp.containsSession())
              sendSession();
          else
              sendSignOn();
      }

      void onDisconnect() {
          //Mark muxed channel as disconected
          if (server != null) {
              SpaceUtil.wipe(sp, getReadyQueueName());
              hlp.setSigned(false);
              watchdog.deactivate();
              log.info("Channel "+server.getName()+" marked as disconnected");
          }
      }

      long getIdleTimeInMilis() {
          return watchdog==null?0:watchdog.getIdleTime();
      }

      long getLastTxnTimestampInMillis() {
          return lastTxn;
      }

      public boolean isSigned() {
          return hlp.isSigned();
      }

      public void sendSession() {
          hlp.sendSession();
      }

      public void sendSignOn() {
          hlp.sendSignOn();
      }

      public void sendSignOff() {
          hlp.sendSignOff();
      }

      public void sendEcho() {
          hlp.sendEcho();
      }
    }

}
