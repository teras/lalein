package com.panayotis.lalein;

interface TriFunction<R, K, V> {
    R apply(R data, K key, V value);
}
