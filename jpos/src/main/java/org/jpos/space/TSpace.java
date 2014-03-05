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

package org.jpos.space;
import org.jpos.util.Loggeable;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TSpace implementation
 * @author Alejandro Revilla
 * @version $Revision$ $Date$
 * @since !.4.9
 */

public class TSpace<K,V> implements LocalSpace<K,V>, Loggeable, Runnable {
    protected Map<Object,List> entries;
    protected TSpace<Object,List> sl;    // space listeners
    protected final Lock lock = new ReentrantLock();
    protected Condition chEntry = lock.newCondition();
    public static final long GCDELAY = 5*1000;
    private static final long GCLONG = 60*1000;
    private static final long NRD_RESOLUTION = 500L;
    private Set<K>[] expirables;
    private long lastLongGC = System.currentTimeMillis();

    private void signal() {
      lock.lock();
      try {
          chEntry.signalAll();
      } catch (IllegalStateException ignored) {
      } finally {
          lock.unlock();
      }
    }

    public TSpace () {
        super();
        entries = new ConcurrentHashMap ();
        expirables = new Set[] { new HashSet<K>(), new HashSet<K>() };
        SpaceFactory.getGCExecutor().scheduleAtFixedRate(this, GCDELAY, GCDELAY, TimeUnit.MILLISECONDS);
    }
    public void out (K key, V value) {
        if (key == null || value == null)
            throw new NullPointerException ("key=" + key + ", value=" + value);

        List l = getList(key);
        synchronized (l) {
            l.add(value);
            signal();
            notifyListeners(key, value);
        }
    }
    public void out (K key, V value, long timeout) {
        if (key == null || value == null)
            throw new NullPointerException ("key=" + key + ", value=" + value);
        Object v = value;
        if (timeout > 0) {
            v = new Expirable (value, System.currentTimeMillis() + timeout);
        }

        List l = getList(key);
        synchronized (l) {
            if (timeout > 0)
                registerExpirable(key, timeout);
            l.add(v);
            signal();
            notifyListeners(key, value);
        }
    }
    public V rdp (Object key) {
        if (key instanceof Template)
            return (V) getObject ((Template) key, false);
        return (V) getHead (key, false);
    }
    public V inp (Object key) {
        if (key instanceof Template)
            return (V) getObject ((Template) key, true);
        return (V) getHead (key, true);
    }
    public V in (Object key) {
        V obj = null;
        lock.lock();
        try {
            while ((obj = inp (key)) == null)
                chEntry.await();
        } catch (InterruptedException e) {
        } finally {
          lock.unlock();
        }
        return obj;
    }
    public V in  (Object key, long timeout) {
        V obj = null;
        long now = System.currentTimeMillis();
        long end = now + timeout;
        lock.lock();
        try {
            while ((obj = inp (key)) == null &&
                    ((now = System.currentTimeMillis()) < end))
                chEntry.await(end - now, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        } finally {
            lock.unlock();
        }
        return obj;
    }
    public V rd  (Object key) {
        V obj = null;
        lock.lock();
        try {
            while ((obj = rdp (key)) == null)
                chEntry.await();
        } catch (InterruptedException e) {
        } finally {
            lock.unlock();
        }
        return obj;
    }
    public V rd  (Object key, long timeout) {
        V obj = null;
        long now = System.currentTimeMillis();
        long end = now + timeout;
        lock.lock();
        try {
            while ((obj = rdp (key)) == null &&
                    ((now = System.currentTimeMillis()) < end))
                chEntry.await(end - now, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        } finally {
            lock.unlock();
        }
        return obj;
    }
    public void nrd  (Object key) {
        lock.lock();
        try {
            while (rdp (key) != null)
                chEntry.await(NRD_RESOLUTION, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        } finally {
            lock.unlock();
        }
    }
    public V nrd  (Object key, long timeout) {
        Object obj = null;
        long now = System.currentTimeMillis();
        long end = now + timeout;
        lock.lock();
        try {
            while ((obj = rdp (key)) != null &&
                    ((now = System.currentTimeMillis()) < end))
                chEntry.await(Math.min(NRD_RESOLUTION, end - now), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        } finally {
            lock.unlock();
        }
        return (V) obj;
    }
    public void run () {
        try {
            gc();
        } catch (Exception e) {
            e.printStackTrace(); // this should never happen
        }
    }
    public void gc () {
        gc(0);
        if (System.currentTimeMillis() - lastLongGC > GCLONG) {
            gc(1);
            lastLongGC = System.currentTimeMillis();
        }
    }
    private void gc (int generation) {
        Set<K> exps = expirables[generation];
        lock.lock();
        try {
            expirables[generation] = new HashSet<K>();
            for (K k : exps) {
                if (rdp(k) != null) {
                    expirables[generation].add(k);
                }
                Thread.yield ();
            }
        } finally {
            lock.unlock();
        }
    }

    public int size (Object key) {
        int size = 0;
        List l = entries.get(key);
        lock.lock();
        try {
            if (l != null)
                size = l.size();
        } finally {
            lock.unlock();
        }
        return size;
    }
    public void addListener (Object key, SpaceListener listener) {
        getSL().out (key, listener);
    }
    public void addListener
        (Object key, SpaceListener listener, long timeout) 
    {
        getSL().out (key, listener, timeout);
    }
    public void removeListener
        (Object key, SpaceListener listener) 
    {
        if (sl == null || sl.isEmpty())
          return;
        synchronized (sl) {
            if (sl == null || sl.isEmpty())
              return;
            sl.inp (new ObjectTemplate (key, listener));
        }
    }
    public boolean isEmpty() {
        return entries.isEmpty();
    }
    public Set getKeySet() {
        return entries.keySet();
    }
    public String getKeysAsString () {
        StringBuilder sb = new StringBuilder();
        for (Object key: entries.keySet()) {
            if (sb.length()>0)
                sb.append (' ');
            sb.append (key);
        }
        return sb.toString();
    }
    public void dump(PrintStream p, String indent) {
        Set keys = entries.keySet();
        for (Object key : keys) {
            p.printf("%s<key count='%d'>%s</key>\n", indent, size(key), key);
        }
        p.println(indent+"<keycount>"+(keys.size()-1)+"</keycount>");
        int exp0, exp1;
        lock.lock();
        try {
            exp0 = expirables[0].size();
            exp1 = expirables[1].size();
        } finally {
            lock.unlock();
        }
        p.println(String.format("%s<gcinfo>%d,%d</gcinfo>\n", indent, exp0, exp1));
    }
    private void notifyListeners (Object key, Object value) {
        if (sl == null || sl.isEmpty())
            return;
        List l = sl.entries.get (key);
        if (l==null)
            return;
        for (Object o :l) {
            if (o instanceof Expirable)
                o = ((Expirable) o).getValue();
            if (o instanceof SpaceListener)
                ((SpaceListener) o).notify(key, value);
        }
    }
    public void push (K key, V value) {
        if (key == null || value == null)
            throw new NullPointerException ("key=" + key + ", value=" + value);

        List l = getList(key);
        synchronized (l) {
            l.add(0, value);
            signal();
            notifyListeners(key, value);
        }
    }

    public void push (K key, V value, long timeout) {
        if (key == null || value == null)
            throw new NullPointerException ("key=" + key + ", value=" + value);
        Object v = value;
        if (timeout > 0) {
            v = new Expirable (value, System.currentTimeMillis() + timeout);
        }

        List l = getList(key);
        synchronized (l) {
            if (timeout > 0)
                registerExpirable(key, timeout);
            l.add(0, v);
            signal();
            notifyListeners(key, value);
        }
    }

    public void put (K key, V value) {
        if (key == null || value == null)
            throw new NullPointerException ("key=" + key + ", value=" + value);

        List l = new LinkedList();
        entries.put(key, l);
        synchronized (l) {
            l.add(value);
            signal();
            notifyListeners(key, value);
        }
    }
    public void put (K key, V value, long timeout) {
        if (key == null || value == null)
            throw new NullPointerException ("key=" + key + ", value=" + value);
        Object v = value;
        if (timeout > 0) {
            v = new Expirable (value, System.currentTimeMillis() + timeout);
        }
        List l = new LinkedList();
        entries.put(key, l);
        synchronized (l) {
            if (timeout > 0)
                registerExpirable(key, timeout);
            l.add(v);
            signal();
            notifyListeners(key, value);
        }
    }
    public boolean existAny (K[] keys) {
        for (K key : keys) {
            if (rdp(key) != null)
                return true;
        }
        return false;
    }
    public boolean existAny (K[] keys, long timeout) {
        long now = System.currentTimeMillis();
        long end = now + timeout;
        lock.lock();
        try {
            while (((now = System.currentTimeMillis()) < end))
                if (existAny (keys))
                    return true;
                    chEntry.await(end - now, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        } finally {
            lock.unlock();
        }
        return false;
    }
    /**
     * unstandard method (required for space replication) - use with care
     * @return underlying entry map
     */
    public Map getEntries () {
        return entries;
    }
    /**
     * unstandard method (required for space replication) - use with care
     * @param entries underlying entry map
     */
    public void setEntries (Map entries) {
        this.entries = entries;
    }
    private List getList (Object key) {
        List l = entries.get(key);
        if (l == null)
            entries.put (key, l = new LinkedList());
        return l;
    }
    private Object getHead (Object key, boolean remove) {
        Object obj = null;
        if (key==null)
          return obj;
        List l = entries.get (key);
        if (l==null)
          return obj;

        boolean wasExpirable = false;
        synchronized (l) {
            while (obj == null && !l.isEmpty()) {
                obj = l.get(0);
                if (obj instanceof Expirable) {
                    obj = ((Expirable) obj).getValue();
                    wasExpirable = true;
                }
                if (obj == null) {
                    l.remove (0);
                    if (l.isEmpty()) {
                        entries.remove (key);
                    }
                }
            }
            if (obj != null && remove) {
                l.remove (0);
                if (l.isEmpty()) {
                    entries.remove (key);
                    if (wasExpirable)
                        unregisterExpirable(key);
                }
            }
        }
        return obj;
    }
    private Object getObject (Template tmpl, boolean remove) {
        Object obj = null;
        List l = entries.get (tmpl.getKey());
        if (l == null)
            return obj;
        synchronized (l) {
            Iterator iter = l.iterator();
            while (iter.hasNext()) {
                obj = iter.next();
                if (obj instanceof Expirable) {
                    obj = ((Expirable) obj).getValue();
                    if (obj == null) {
                        iter.remove();
                        continue;
                    }
                }
                if (tmpl.equals (obj)) {
                    if (remove)
                        iter.remove();
                    break;
                } else
                    obj = null;
            }
        }
        return obj;
    }
    private TSpace getSL() {
        if (sl == null)
            sl = new TSpace();
        return sl;
    }
    private void registerExpirable(K k, long t) {
        expirables[t > GCLONG ? 1 : 0].add(k);
    }
    private void unregisterExpirable(Object k) {
        for (Set<K> s : expirables)
            s.remove((K)k);
    }
    static class Expirable implements Comparable {
        Object value;
        long expires;

        public Expirable (Object value, long expires) {
            super();
            this.value = value;
            this.expires = expires;
        }
        public boolean isExpired () {
            return expires < System.currentTimeMillis ();
        }
        public String toString() {
            return getClass().getName() 
                + "@" + Integer.toHexString(hashCode())
                + ",value=" + value.toString()
                + ",expired=" + isExpired ();
        }
        public Object getValue() {
            return isExpired() ? null : value;
        }
        public int compareTo (Object obj) {
            Expirable other = (Expirable) obj;
            long otherExpires = other.expires;
            if (otherExpires == expires)
                return 0;
            else if (expires < otherExpires)
                return -1;
            else 
                return 1;
        }
    }
}
