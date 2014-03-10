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
import java.io.PrintStream;
import org.jpos.util.Loggeable;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TSpace implementation
 * @author Alejandro Revilla
 * @version $Revision$ $Date$
 * @since !.4.9
 */

public class TSpace<K,V> implements LocalSpace<K,V>, Loggeable, Runnable {
    protected ConcurrentMap<Object,List> entries;
    protected AtomicReference<TSpace<Object,List>> sl;    // space listeners
    public static final long GCDELAY = 5*1000;
    private static final long GCLONG = 60*1000;
    private static final long NRD_RESOLUTION = 500L;
    private final Set<K>[] expirables;
    private long lastLongGC = System.currentTimeMillis();

    public TSpace () {
        super();
        entries = new ConcurrentHashMap ();
        sl = new AtomicReference();
        expirables = new Set[] { Collections.newSetFromMap(new ConcurrentHashMap()), Collections.newSetFromMap(new ConcurrentHashMap()) };
        SpaceFactory.getGCExecutor().scheduleAtFixedRate(this, GCDELAY, GCDELAY, TimeUnit.MILLISECONDS);
    }
    public void out (K key, V value) {
        if (key == null || value == null)
            throw new NullPointerException ("key=" + key + ", value=" + value);

        final List l = getList(key);
        synchronized (l) {
            l.add (value);
            if (l.size() == 1)
                l.notifyAll();
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
        final List l = getList(key);
        synchronized (l) {
            if (timeout > 0)
                registerExpirable(key, timeout);
            l.add(v);
            if (l.size() == 1)
                l.notifyAll();
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
    private void removeList(Object key, List l) {
        if ( l.isEmpty() )
            entries.remove(key);
    }
    public V in (Object key) {
        V obj;
        List l = getList(key);
        synchronized (l) {
            while ((obj = inp (key)) == null)
                try {
                    l.wait();
                } catch (InterruptedException e) {}
            removeList(key, l);
         }
        return obj;
    }
    public V in (Object key, long timeout) {
        V obj;
        long now = System.currentTimeMillis();
        long end = now + timeout;
        final List l = getList(key);
        synchronized (l) {
            while ((obj = inp (key)) == null &&
                    ((now = System.currentTimeMillis()) < end))
                try {
                    l.wait(end - now);
                } catch (InterruptedException e) {}
            removeList(key, l);
        }
        return obj;
    }
    public V rd  (Object key) {
        V obj;
        final List l = getList(key);
        synchronized (l) {
            while ((obj = rdp (key)) == null)
                try {
                    l.wait ();
                } catch (InterruptedException e) {}
            removeList(key, l);
        }
        return obj;
    }
    public V rd  (Object key, long timeout) {
        V obj;
        long now = System.currentTimeMillis();
        long end = now + timeout;
        final List l = getList(key);
        synchronized (l) {
            while ((obj = rdp (key)) == null &&
                    ((now = System.currentTimeMillis()) < end))
                try {
                    l.wait (end - now);
                } catch (InterruptedException e) { }
            removeList(key, l);
        }
        return obj;
    }
    public void nrd  (Object key) {
        final List l = getList(key);
        synchronized (l) {
            while (rdp (key) != null)
                try {
                    l.wait(NRD_RESOLUTION);
                } catch (InterruptedException ignored) { }
            removeList(key, l);
        }
    }
    public V nrd  (Object key, long timeout) {
        V obj;
        long now = System.currentTimeMillis();
        long end = now + timeout;
        final List l = getList(key);
        synchronized (l) {
            while ((obj = rdp (key)) != null &&
                    ((now = System.currentTimeMillis()) < end))
                try {
                    l.wait (Math.min(NRD_RESOLUTION, end - now));
                } catch (InterruptedException ignored) { }
            removeList(key, l);
        }
        return obj;
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
        for (K k : exps) {
            if (rdp(k) == null)
                expirables[generation].remove(k);

            Thread.yield ();
        }
    }

    public int size (Object key) {
        int size = 0;
        List l = entries.get(key);
        if (l != null)
            size = l.size();
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
        Space sp = sl.get();
        if (sp != null) {
            sp.inp (new ObjectTemplate (key, listener));
        }
        sl.compareAndSet(new TSpace<Object,List>(), null);
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
        exp0 = expirables[0].size();
        exp1 = expirables[1].size();
        p.println(String.format("%s<gcinfo>%d,%d</gcinfo>\n", indent, exp0, exp1));
    }
    private void notifyListeners (Object key, Object value) {
        TSpace sp = sl.get();
        if (sp == null || sp.isEmpty())
            return;
        List l = (List)sp.entries.get (key);
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
            boolean wasEmpty = l.isEmpty();
            l.add (0, value);
            if (wasEmpty)
                l.notifyAll();
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
            boolean wasEmpty = l.isEmpty();
            l.add (0, v);
            if (wasEmpty)
                l.notifyAll();
            notifyListeners(key, value);
        }
    }

    public void put (K key, V value) {
        if (key == null || value == null)
            throw new NullPointerException ("key=" + key + ", value=" + value);

        List l = new LinkedList();
        synchronized (l) {
            l.add (value);
            entries.put(key, l);
            l.notifyAll();
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
        List l = getList(key);
        synchronized (l) {
            if (timeout > 0)
                registerExpirable(key, timeout);
            l.add (v);
            entries.put(key, l);
            l.notifyAll();
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
    public boolean existAny (K[] keys, final long timeout) {
        ExecutorService es = new ThreadPoolExecutor(keys.length, keys.length+2
                    ,timeout, TimeUnit.MILLISECONDS, new SynchronousQueue());
        ((ThreadPoolExecutor) es).prestartAllCoreThreads();
        List<Callable<Boolean>> cl = new ArrayList(keys.length);
        for (final K key : keys)
            cl.add(new Callable(){
                @Override
                public Boolean call() {
                    return rd(key, timeout) != null;
                }
            });
        try {
          return es.invokeAny(cl, timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
        } catch (ExecutionException ex) {
        } catch (TimeoutException ex) {
        } finally {
          es.shutdown();
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
    public void setEntries (ConcurrentMap entries) {
        this.entries = entries;
    }
    private List getList (Object key) {
        List l = new LinkedList();
        List lo = entries.putIfAbsent(key, l);
        return lo == null ? l : lo;
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
        TSpace sp = sl.get();
        TSpace spn = new TSpace();
        if (sl.compareAndSet(null, spn))
          sp = spn;
        return sp;
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
