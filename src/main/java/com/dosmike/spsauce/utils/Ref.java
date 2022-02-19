package com.dosmike.spsauce.utils;

/**
 * wrapper object that will hold the reference to an object.
 * due to how java works, if the object is primitive or a string it will copy on write,
 * meaning that you'll effectively hold a reference to a copy.
 * this is still enough to implement output arguments and byref-like primitive/string arguments.
 */
public class Ref<T> {
    public T it;

    @Override
    public String toString() {
        return it.toString();
    }

    public static <E> Ref<E> of(E e) { Ref<E> r = new Ref<>(); r.it=e; return r; }

}
