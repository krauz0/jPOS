package org.jpos.util;

import org.jpos.iso.ISOUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Testy dla implementacji {@link pl.visiona.hi.util.WatchDog}.
 * @see pl.visiona.hi.util.WatchDog
 * 
 * @author Michał Bożek
 * @version $Revision: 2622 $ $Date: 2013-10-15 15:08:14 +0200 (wto) $
 */
public class WatchDog2Test {

  static final int TASK_DURATION = 100;
  static final int TASK_PERIOD   = 50;

  TestTask testTask;

  class TestTask implements Runnable {

    int duration;
    int checkCNT = 0;

    TestTask(int duration) {
      this.duration = duration;
    }

    @Override
    public void run() {
      ISOUtil.sleep(duration);
      checkCNT++;
    }

    int getCheckValue() {
      return checkCNT;
    }
  }

  @Before
  public void setUp() {
    testTask = new TestTask(TASK_DURATION);
  }

  @After
  public void tearDown() {
    testTask = null;
  }

  /**
   * Test testExecuteSingleTask.
   * 
   * <p>Test ma za zadanie sprawdzić czy po starcie {@link pl.visiona.hi.util.WatchDog}
   * odczekaniu czasu po jakim uruchomi się zadanie {@link #TASK_PERIOD} oraz
   * czasu trwania zadania {@link #TASK_DURATION} zadanie wykona się przynajmniej raz.
   */
  @Test
  public void testExecuteSingleTask() throws Exception {
    WatchDog2 wd = new WatchDog2(testTask, TASK_PERIOD);
    ISOUtil.sleep(3*TASK_DURATION);
    wd.deactivate();
    assertTrue(testTask.getCheckValue() > 1);
  }

  /**
   * Test testExecuteParallelTask.
   * 
   * <p>Test ma za zadanie sprawdzić czy po starcie wielu instancji
   * {@link pl.visiona.hi.util.WatchDog}. Zadania zostaną wykonane równolegle,
   * aby tak było każdy task z każdego WatchDog musi wykonać się conajmniej raz.
   */
  @Test
  public void testExecuteParallelTask() throws Exception {
    TestTask t2 = new TestTask(TASK_PERIOD);
    WatchDog2 wd = new WatchDog2(testTask,TASK_PERIOD);
    WatchDog2 wd2 = new WatchDog2(t2,TASK_PERIOD);
    ISOUtil.sleep(3*TASK_DURATION);
    wd.deactivate();
    wd2.deactivate();
    assertTrue(testTask.getCheckValue() > 1);
    assertTrue(t2.getCheckValue() > 1);
  }

  /**
   * Test testImmediateDeactivate.
   * 
   * <p>Test ma za zadanie sprawdzić czy po starcie {@link pl.visiona.hi.util.WatchDog}
   * i niezwłocznym wykonaniu metody {@link  pl.visiona.hi.util.WatchDog#deactivate()}
   * zadania zostaną anulowane i nie wykonają się ani razu.
   */
  @Test
  public void testImmediateDeactivate() throws Exception {
    WatchDog2 wd = new WatchDog2(testTask, TASK_PERIOD);
    wd.deactivate();
    ISOUtil.sleep(3*TASK_DURATION);
    assertTrue(testTask.getCheckValue() < 1);
  }

  /**
   * Test testDelayDeactivate.
   * 
   * <p>Test ma za zadanie sprawdzić czy po starcie {@link pl.visiona.hi.util.WatchDog}
   * odczekaniu takiego czasu abu uruchomiło się zadanie
   * (czas &gt {@link #TASK_PERIOD} && czas &lt {@link #TASK_PERIOD})
   * zadanie wykona się dokładnie jeden raz.
   */
  @Test
  public void testDelayDeactivate() throws Exception {
    int delay = (TASK_PERIOD >> 1) + TASK_PERIOD;
    WatchDog2 wd = new WatchDog2(testTask, TASK_PERIOD);
    ISOUtil.sleep(delay);
    wd.deactivate();
    ISOUtil.sleep(2*TASK_PERIOD);
    assertEquals(1, testTask.getCheckValue());
  }

}
