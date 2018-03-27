package ml.adamsprogs.bimba.collections

import java.util.TreeSet

class IndexableTreeSet<T>: TreeSet<T>() {
    operator fun get(position: Int): T {
        @Suppress("UNCHECKED_CAST")
        return this.toArray()[position] as T
    }

}