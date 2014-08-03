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

package org.jpos.util;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Wykonuje przekazane zadanie {@code task} po określonym czasie braku aktywności.
 * <p>Aktywacja zadania następuje w sytuacji gdy przez określony czas
 * {@code maxIdleTime} nie nastąpiło wzbudzenie metodą {@link #tick() }
 * <p>Przekazywane zadania powinny być, krótkotwałe aby uniknąć zapchania systemu
 * (wątek który zajmuje sie ich wykonywaniem). W praktycje jest to trudne
 * do zagwarantowania dlatego aktualna implementaja dopuszcza również
 * długotrwałe zadania. Wszystkie zadania realizowane są przez
 * {@link ThreadPoolExecutor}. W sytuacji zablokowania wątku realizującego
 * długotrwałe zadanie ThreadPoolExecutor powołuje kolejne wątki,
 * które po określonym czasie braku aktywności są wyłączane.
 *
 * @author Michał Wiercioch
 * @author Robert Demski
 * @version $Revision: 2861 $ $Date: 2014-06-14 20:48:13 +0200 (sob) $
 */
public class WatchDog2 {

  static Timer    timer   = DefaultTimer.getTimer();;
  static ExecutorService  executor = new ThreadPoolExecutor(
          0,Integer.MAX_VALUE,60,TimeUnit.SECONDS,new SynchronousQueue());

  long     maxIdleTime;
  long     lastTick;
  Runnable task;
  boolean  active;

  public WatchDog2(Runnable task, long maxIdleTime) {
    this.task        = task;
    this.maxIdleTime = maxIdleTime;
    active           = true;
    lastTick         = System.currentTimeMillis();
    timer.schedule(new WatchDogTask(), maxIdleTime);
  }

  public void tick() {
    lastTick = System.currentTimeMillis();
  }

  public long getIdleTime() {
    return System.currentTimeMillis() - lastTick;
  }

  public void deactivate() {
    active = false;
  }

  private class WatchDogTask extends TimerTask {
    @Override
    public void run() {
      if(!active)
        return;
      long idleTime = getIdleTime();
      long schedDelay;
      if(idleTime >= maxIdleTime) {
        executor.execute(task);
        tick();
        schedDelay = maxIdleTime;
      } else {
        schedDelay = maxIdleTime - idleTime;
      }

      timer.schedule(new WatchDogTask(), schedDelay);
    }
  }
}
