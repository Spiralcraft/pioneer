//
// Copyright (c) 1998,2008 Michael Toth
// Spiralcraft Inc., All Rights Reserved
//
// This package is part of the Spiralcraft project and is licensed under
// a multiple-license framework.
//
// You may not use this file except in compliance with the terms found in the
// SPIRALCRAFT-LICENSE.txt file at the top of this distribution, or available
// at http://www.spiralcraft.org/licensing/SPIRALCRAFT-LICENSE.txt.
//
// Unless otherwise agreed to in writing, this software is distributed on an
// "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
//
/**
 * A data structure composed of a List and several Maps. 
 * Each Map is associated with a Translator which derives
 *   a key from a value object. When objects are added and
 *   removed from the list, an entry in each of the Maps is
 *   adjusted accordingly. 
 * Since it is possible for multiple items in the list to
 *   have the same key, the Maps actually store a List of
 *   values for a key, thus, Map.get(Object key) will always
 *   return a List.  The ListMap interface provides a 
 *   getFirst(Object key) method that returns the first match,
 *   for convenience.
 * Maps can be set to be unique, through ListMap.setUnique().
 *   This will ensures that there is only one value for a 
 *   key by removing other values that share the key from the
 *   entire data structure.
 */
package spiralcraft.pioneer.util;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.SortedMap;
import java.util.Comparator;
import java.lang.UnsupportedOperationException;



public class MappedList
  implements List
{

  public class MapView implements ListMap 
  {
    protected Map m_map;
    protected Translator m_key;
    protected int m_numValues;
    protected boolean _unique;


    public MapView(Map map,Translator key)
    {
      m_map=map;
      m_key=key;
    }

    public void setUnique(boolean unique)
    { _unique=unique;
    }

    public void clear()
    { throw new UnsupportedOperationException();
    }

    public void clearLocal()
    {
      m_map.clear();
      m_numValues=0;
    }

    public boolean containsKey(Object key)
    { return m_map.containsKey(key);
    }

    public boolean containsValue(Object value)
    {
      List list
        =(List) m_map.get(m_key.translate(value));

      if (list!=null)
      { return list.contains(value);
      }
      else
      { return false;
      }
    }
/*
    public Set entries()
    {
      return m_map.entries();
    }
*/

    public Set entrySet()
    { 
      // XXX Should create a new set which maps to actual
      //   list items.
      return m_map.entrySet();
    }

    public boolean equals(Object val)
    {
      // XXX Do the comparison
      return false;
    }

    /** 
     *
     */
    public Object get(Object key)
    { 
      List list=(List) m_map.get(key);
      if (list!=null && list.size()>0)
      { return list;
      }
      else
      { return null;
      }
    }

    /**
     * Return the first value for the given key
     */
    public Object getFirst(Object key)
    {
      final List list=(List) m_map.get(key);
      if (list!=null && list.size()>0)
      { return list.get(0);
      }
      else
      { return null;
      }
    }

    public int hashCode()
    { return m_map.hashCode();
    }

    public boolean isEmpty()
    { return m_map.isEmpty();
    }

    public Set keySet()
    { return m_map.keySet();
    }

    /**
     * put() is not supported
     */
    public Object put(Object key,Object value)
    { throw new UnsupportedOperationException(); 
    }
      
    /**
     * putAll() is not supported
     */
    public void putAll(Map map)
    { throw new UnsupportedOperationException(); 
    }

    /**
     * Remove all values with the specified key
     */
    public Object remove(Object key)
    {
      List list=(List) m_map.get(key);
      if (list!=null)
      { removeAll(list);
      }
      return list;
    }

    public int size()
    { return m_map.size();
    }

    public Collection values()
    { return m_list;
    }

    public int numValues()
    { return m_numValues;
    }

    public void add(Object val)
    { 
      final Object keyval=m_key.translate(val);
      List list=(List) m_map.get(keyval);
      if (list==null)
      {
        list=new LinkedList();
        m_map.put(keyval,list);
      }
      if (_unique)
      {
        Object oldval=null;
        if (list.size()>0)
        { oldval=list.set(0,val);
        }
        else
        {
          list.add(val);
          m_numValues++;
        }
        if (oldval !=null && oldval!=val)
        { m_list.remove(oldval);
        }
      }
      else
      {
        list.add(val);
        m_numValues++;
      }
    }

    public void removeValue(Object val)
    {
      Object key=m_key.translate(val);
      List list=(List) m_map.get(key);
      if (list!=null)
      {
        ListIterator i=list.listIterator();
        boolean removed=false;
        while (i.hasNext())
        { 
          
          if (i.next()==val)
          {
            i.remove();
            m_numValues--;
            removed=true;
            break;
          }
        }
        
        if (list.size()==0)
        { m_map.remove(key);
        }
      }
    }
  }

  public class SortedMapView
    extends MapView
    implements SortedListMap
  {

    private Comparator m_comparator;

    public SortedMapView(SortedMap map,Translator key,Comparator comp)
    {
      super(map,key);
      m_comparator=comp;
    }

    public Comparator comparator()
    { return ((SortedMap) m_map).comparator();
    }

    public SortedMap subMap(Object first,Object last)
    {
      return new SortedMapView
        ( ((SortedMap) m_map).subMap(first,last)
        , m_key
        , m_comparator
        );
    }

    public SortedMap headMap(Object first)
    {
      return new SortedMapView
        ( ((SortedMap) m_map).headMap(first)
        , m_key
        , m_comparator
        );

    }

    public SortedMap tailMap(Object first)
    {
      return new SortedMapView
        ( ((SortedMap) m_map).tailMap(first)
        , m_key
        , m_comparator
        );

    }

    public Object firstKey()
    { return ((SortedMap) m_map).firstKey();
    }

    public Object lastKey()
    { return ((SortedMap) m_map).lastKey();
    }

    public Collection values()
    { return new MultiMapCollection();
    }

    class MultiMapCollection
      implements Collection
    {
      public boolean add(Object o)
      { throw new UnsupportedOperationException();
      }

      public boolean addAll(Collection c)
      { throw new UnsupportedOperationException();
      }

      public void clear()
      { throw new UnsupportedOperationException();
      }

      public boolean remove(Object O)
      { throw new UnsupportedOperationException();
      }

      public boolean removeAll(Collection c)
      { throw new UnsupportedOperationException();
      }

      public boolean retainAll(Collection c)
      { throw new UnsupportedOperationException();
      }

      public int size()
      { return numValues();
      }

      public boolean isEmpty()
      { return m_map.isEmpty();
      }

      public boolean contains(Object o)
      { return containsValue(o);
      }

      public Iterator iterator()
      { return new MultiMapIterator(m_map.values().iterator());
      }

      public Object toArray()[]
      {
        List l=new LinkedList();
        Iterator it=iterator();
        while (it.hasNext())
        { l.add(it.next());
        }
        return l.toArray();
      }

      public Object toArray(Object[] a)[]
      {
      
        Iterator it=iterator();
        int i=0;
        while (it.hasNext() && i++<size())
        { a[i]=it.next();
        }
        return a;
      }

      public boolean containsAll(Collection c)
      {
        Iterator it=c.iterator();
        while (it.hasNext())
        {
          if (!contains(it.next()))
          { return false;
          }
        }
        return true;
      }
    }

  }


  class MultiMapIterator
    implements Iterator
  {
    Iterator m_lists;
    Iterator m_values;

    public MultiMapIterator(Iterator iter)
    {
      m_lists=iter;
    }

    public boolean hasNext()
    {
      if (m_values==null)
      { return m_lists.hasNext();
      }
      else
      { return m_values.hasNext();
      }
    }

    public Object next()
    {
      if (m_values==null)
      { m_values=((List) m_lists.next()).iterator();
      }
      Object ret=m_values.next();
      if (!m_values.hasNext())
      { m_values=null;
      }
      return ret;
    }

    public void remove()
    { throw new UnsupportedOperationException();
    }

  }

  class Iter implements Iterator
  {
    private Iterator m_iterator;
    private Object m_lastVal=null;

    public Iter(Iterator iterator)
    { m_iterator=iterator;
    }

    public boolean hasNext()
    { return m_iterator.hasNext();
    }

    public Object next()
    {
      m_lastVal=m_iterator.next();
      return m_lastVal;
    }

    public void remove()
    { 
      m_iterator.remove();
      removeValueFromMaps(m_lastVal);
      m_lastVal=null;
    }

  }

  class ListIter
    implements ListIterator
  {
    private ListIterator m_it;
    private Object m_lastVal=null;

    public ListIter(ListIterator iterator)
    { m_it=iterator;
    }

    public boolean hasNext()
    { return m_it.hasNext();
    }

    public Object next()
    {
      m_lastVal=m_it.next();
      return m_lastVal;
    }

    public boolean hasPrevious()
    { return m_it.hasPrevious();
    }

    public Object previous()
    {
      m_lastVal=m_it.previous();
      return m_lastVal;
    }

    public int nextIndex()
    { return m_it.nextIndex();
    }

    public int previousIndex()
    { return m_it.previousIndex();
    }

    public void add(Object o)
    {
      m_it.add(o);
      addValueToMaps(o);
    }

    public void set(Object o)
    {
      m_it.set(o);
      removeValueFromMaps(m_lastVal);
      addValueToMaps(o);
      m_lastVal=o;
    }
    
    public void remove()
    { 
      m_it.remove();
      removeValueFromMaps(m_lastVal);
      m_lastVal=null;
    }
  }

  private HashMap m_views=new HashMap();
  private List m_list;
  private boolean m_unique=false;
  private MapView[] _viewArray=new MapView[0];

  /**
   * Construct a new MappedList using the specified list for
   *   the storage implementation
   */
  public MappedList(List list)
  { m_list=list;
  }

  /**
   * Add a Map view to the collection, using the given Map
   *   as the storage implementation. The supplied Map will
   *   be cleared before use.
   *
   * The Map will be used to store indexes into the List.
   *
   *@param name The name to refer to the map view.
   *@param map  The actual Map implementation to use for storage.
   *@param key  A Translation that generates a map key for objects
   *              in the collection.
   */ 
  public synchronized ListMap addMapView(String name,Map map,Translator key)
  {
    MapView view=new MapView(map,key);
    Iterator it=m_list.iterator();
    while (it.hasNext())
    { view.add(it.next());
    }
    m_views.put(name,view);
    MapView[] viewArray=new MapView[_viewArray.length+1];
    System.arraycopy(_viewArray,0,viewArray,0,_viewArray.length);
    viewArray[viewArray.length-1]=view;
    _viewArray=viewArray;
    return view;
  }

  public synchronized SortedListMap addSortedMapView
    (String name
    ,SortedMap map
    ,Translator key
    ,Comparator comp
    )
  {
    SortedMapView view=new SortedMapView(map,key,comp);
    Iterator it=m_list.iterator();
    while (it.hasNext())
    { view.add(it.next());
    }
    m_views.put(name,view);
    MapView[] viewArray=new MapView[_viewArray.length+1];
    System.arraycopy(_viewArray,0,viewArray,0,_viewArray.length);
    viewArray[viewArray.length-1]=view;
    _viewArray=viewArray;
    return view;
  
  }

  /**
   * Retrieve the given map view from the collection
   */
  public ListMap getMapView(String name)
  { return (ListMap) m_views.get(name);
  }

  /**
   * Remove all entries
   */
  public void clear()
  {
    for (int i=0;i<_viewArray.length;i++)
    { _viewArray[i].clearLocal();
    }
    m_list.clear();
  }

  public Object get(int index)
  { return m_list.get(index);
  }

  public Object set(int index,Object val)
  {
    Object oldval=m_list.get(index);
    removeValueFromMaps(oldval);
    m_list.set(index,val);
    addValueToMaps(val);
    return oldval;
  }

  protected void removeValueFromMaps(Object oldval)
  { 
    for (int i=0;i<_viewArray.length;i++)
    { _viewArray[i].removeValue(oldval);
    }
  }

  protected void addValueToMaps(Object newval)
  { 
    for (int i=0;i<_viewArray.length;i++)
    { _viewArray[i].add(newval);
    }
  }


  public void add(int index,Object val)
  {
    m_list.add(index,val);
    addValueToMaps(val);
  }

  public boolean add(Object o)
  {
    if (m_list.add(o))
    { 
      addValueToMaps(o);
      return true;
    }
    else
    { return false;
    }
  }

  public boolean addAll(int i,Collection c)
  { throw new UnsupportedOperationException();
  }

  public boolean addAll(Collection c)
  { 
    boolean changed=false;
    Iterator i=c.iterator();
    while (i.hasNext())
    { changed= add(i.next()) || changed;
    }
    return changed;
  }

  public boolean contains(Object o)
  { return indexOf(o)>=0;
  }

  public boolean containsAll(Collection c)
  {
    Iterator i=c.iterator();
    while (i.hasNext())
    {
      if (!contains(i.next()))
      { return false;
      }
    }
    return true;
  }

  public int indexOf(Object o)
  { return m_list.indexOf(o);
  }

  public boolean isEmpty()
  { return m_list.isEmpty();
  }

  public int size()
  { return m_list.size();
  }

  public Iterator iterator()
  { return new Iter(m_list.iterator());
  }

  public int lastIndexOf(Object o)
  { return m_list.lastIndexOf(o);
  }


  public List subList(int first,int to)
  { return m_list.subList(first,to);
  }

  public ListIterator listIterator()
  { return new ListIter(m_list.listIterator());
  }

  public ListIterator listIterator(int i)
  { return new ListIter(m_list.listIterator(i));
  }

  public Object remove(int i)
  {
    Object oldval=m_list.remove(i);
    removeValueFromMaps(oldval);
    return oldval;
  }

  public boolean remove(Object o)
  {
    boolean removed=m_list.remove(o);
    if (removed)
    { removeValueFromMaps(o);
    }
    return removed;
  }

  public boolean removeAll(Collection c)
  { 
    Object[] o=c.toArray();
  	boolean changed=false;
    for (int i=0;i<o.length;i++)
    { changed=  remove(o[i]) || changed;
 		} 
 		return changed;
  }

  public void removeRange(int lo,int hi)
  {
  	for (int i=lo;i<hi;i++)
  	{ remove(i);
  	}
  }

  public boolean retainAll(Collection c)
  { throw new UnsupportedOperationException();
  }

  /**
   * Obtain an array of all the values in the
   *   list.
   */
  public Object[] toArray()
  { return m_list.toArray();
  }

  /**
   * Not in official interface docs.
   */
  public Object[] toArray(Object[] a)
  {
    Object[] a2=m_list.toArray();
    System.arraycopy(a2,0,a,0,Math.min(a2.length,a.length));
    return a;
  }
  
  /**
   * Ensure that all maps contain only one key-value
   *   reference. This may cause some values not to
   *   be mapped in all maps views.
   */
  public void setUnique(boolean u)
  { m_unique=u;
  }

	public void remap(Object value)
	{
		removeValueFromMaps(value);
		addValueToMaps(value);
	}
	
  /**
   * Obtain a string representation of all the entries in the list
   */
  public String toString()
  { return m_list.toString();
  }
}