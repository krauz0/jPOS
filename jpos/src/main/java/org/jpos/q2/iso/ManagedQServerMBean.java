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

/**
 * MBean interface.
 * @author Robert Demski
 * @version $Revision: 2650 $ $Date: 2013-11-22 16:46:52 +0100 (pią) $
 */
public interface ManagedQServerMBean extends QServerMBean {
  
  public void sendSession();
  public void sendSignOn();
  public void sendSignOff();
  public void sendEcho();
  public long getIdleTimeInMillis();
  public long getLastTxnTimestampInMillis();
  public boolean isSigned();
  public void setSigned(boolean loggin) throws Exception;

}
