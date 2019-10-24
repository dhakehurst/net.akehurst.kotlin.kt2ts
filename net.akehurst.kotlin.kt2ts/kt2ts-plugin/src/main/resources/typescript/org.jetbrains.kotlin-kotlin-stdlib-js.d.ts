export declare namespace kotlin {
    interface Pair<F, S> {
    }

    interface Comparable<T = any> {
    }
}
export declare namespace kotlin.collections {

    // Mutable collections have no representation in kotlin.js

    interface Collection<E> {
        size: number;

        toTypedArray(): E[];

        add(element: E);

        clear();

        remove(element: E);
    }

    interface List<E> extends Collection<E> {
        get(index: number): E;
    }

    interface Set<E> extends Collection<E> {
    }

    interface Map<K, V> {
        size: number;
        values: Collection<V>;
        entries: Set<Map.Entry<K, V>>;

        containsKey(key: K): boolean

        get(key: K,): V;

        put(key: K, value: V);

        clear();

        remove(key: K);
    }
}

export declare namespace kotlin.collections.Map {
    interface Entry<K, V> {
        key: K;
        value: V;
    }
}