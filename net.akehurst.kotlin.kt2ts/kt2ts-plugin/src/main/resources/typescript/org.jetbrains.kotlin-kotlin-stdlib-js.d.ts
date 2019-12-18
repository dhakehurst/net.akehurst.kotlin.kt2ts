export declare namespace kotlin {
    interface Pair<F, S> {
    }
    interface Triple<F, S, T> {
    }
    interface Comparable<T = any> {
    }

    interface Long {
        low: number;
        high: number;

        toNumber() : number;
    }

    interface Function1<P1, R> {
    }

    interface Function2<P1,P2,R> {
    }

    interface Function3<P1,P2,P3,R> {
    }

    interface Function4<P1,P2,P3,P4,R> {
    }

    interface Function5<P1,P2,P3,P4,P5,R> {
    }

}
export declare namespace kotlin.collections {

    // Mutable collections have no representation in kotlin.js
    // the methods (with parameters) declared here do not work at runtime because kotlin mangles the names !

    interface Collection<E> {
        size: number;

        toArray(): E[];

        add(element: E);

        clear();

        remove(element: E);

        isEmpty(): boolean;
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

        isEmpty(): boolean;
    }
}

export declare namespace kotlin.collections.Map {
    interface Entry<K, V> {
        key: K;
        value: V;
    }
}

export declare namespace kotlin.coroutines {
    interface Continuation<R> {
    }
    interface CoroutineContext {
    }
}

export declare namespace kotlin.coroutines.CoroutineContext {
    interface Element {
    }
}
export declare namespace kotlin.properties {
    interface ReadOnlyProperty<R,T> {
    }
    interface ReadWriteProperty<R,T> {
    }
}

export declare namespace kotlin.reflect {
    interface KCallable<R> {
    }
    interface KFunction<R> {
    }
    interface KProperty<R> {
    }
}

export declare namespace kotlin.text {
    interface Regex {
    }
}

export declare namespace kotlin.time {
    interface Duration {
    }
}

