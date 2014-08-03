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

/**
 * MBean interface.
 * @author Robert Demski
 * @version $Revision: 1464 $ $Date: 2011-08-03 16:56:39 +0200 (śro) $
 */
public interface ManagedChannelAdaptorMBean extends ChannelAdaptorMBean {
  
  public void sendSession();
  public void sendSignOn();
  public void sendSignOff();
  public void sendEcho();
  public boolean isSigned();
  public void setSigned(boolean signed);
  public void reconnect() throws IOException;

}
