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

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import org.jdom.Element;
import org.jpos.core.ConfigurationException;
import org.jpos.iso.ISOChannel;
import org.jpos.q2.QFactory;
import org.jpos.util.WatchDog2;


/**
 *
 * @author Robert Demski
 * @version $Revision: 2861 $ $Date: 2014-06-14 20:48:13 +0200 (sob) $
 */
public class ManagedChannelAdaptor extends ChannelAdaptor
    implements ManagedChannelAdaptorMBean, Observer {

    ISOChannel    channel;
    NetMngHelper  hlp;
    WatchDog2     watchdog;
    boolean       wasConnected = false;

    @Override
    public void startService() {
        try {
            String mux = getPersist().getChildTextTrim("mux");
            if (mux == null)
                throw new ConfigurationException("'mux' tag is not set");

            hlp = new NetMngHelper(mux);
            hlp.initBSH(getServer(), getLog(), getPersist());
            String[] acptRCs = cfg.getAll("net-manage-acpt-rc");
            hlp.setAcceptedRCs(Arrays.asList(acptRCs));
            hlp.initMessages(getPersist().getChild("messages"));

            watchdog = new WatchDog2 (
                new Runnable() {
                    @Override
                    public void run() {
                        sendEcho();
                    }
                }, grabLong(getPersist().getChild("max-idle-time"),900000L)
            );
        } catch (Exception e) {
            getLog().warn ("error starting service", e);
        }
        super.startService();
    }

    @Override
    public void stopService() {
        super.stopService();
        try {
            watchdog.deactivate();
        } catch (Exception e) {
            getLog().warn ("error stopping service", e);
        }
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
    public ISOChannel newChannel(Element e, QFactory f) 
        throws ConfigurationException
    {
        channel   = super.newChannel(e, f);
        if (channel instanceof Observable)
            ((Observable)channel).addObserver(this);
        return channel;
    }

    @Override
    public void	update(Observable o, Object arg) {
        ISOChannel ch;
        if (!running()) {
            if(o instanceof ISOChannel) {
                getLog().warn("Channel "+((ISOChannel) o).getName()+" marked as disconnected");
                wasConnected = false;
                hlp.setSigned(false);
            }
            return;
        }
        watchdog.tick();
        if (o instanceof ISOChannel && arg == null)
            ch = (ISOChannel)o;
        else
            return;

        if (ch.isConnected() && !isConnected() && !wasConnected) {
            wasConnected = true;
            getLog().warn("Channel "+ch.getName()+" marked as connected");

            if (hlp.containsSession())
                sendSession();
            else
                sendSignOn();
        } else if (!ch.isConnected() && wasConnected) {
            hlp.setSigned(false);
            wasConnected = false;
            getLog().warn("Channel "+ch.getName()+" marked as disconnected");
        }
    }
    
    @Override
    public void sendSession() {
        hlp.sendSession();
    }

    @Override
    public void sendSignOn() {
        hlp.sendSignOn();
    }

    @Override
    public void sendSignOff() {
        hlp.sendSignOff();
    }

    @Override
    public void sendEcho() {
        hlp.sendEcho();
    }

    @Override
    public boolean isSigned() {
        return hlp.isSigned();
    }

    @Override
    public void setSigned(boolean signed) {
        if (signed)
            sendSignOn();
        else
            sendSignOff();
    }

    @Override
    public void reconnect() throws IOException{
        channel.reconnect();
    }
    
    @Override
    public void dump(PrintStream p, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("connected=").append(isConnected());
        sb.append(", signed=").append(isSigned());
        p.println (indent + sb.toString());
        super.dump(p, indent);
    }

}
