package com.duy.java8.util;

import java.util.List;
import java.util.function.Consumer;

public class DList {
    public static <E> void forEach(List<E> list, Consumer<? super E> action) {
        for (E e : list) {
            action.accept(e);
        }
    }
}
