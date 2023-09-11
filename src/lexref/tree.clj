(ns lexref.tree
  "Define a interface to tree structures.
  Tree structures are preserved by the lexical reference framework,
  and values within them are handled properly.
  Non-tree collections passed into the framework will not be reference counted correctly,
  most likely leading to resource leaks.")

(defprotocol ITree
  "Interface for trees.
  A tree is a structure that can enumerate its own values,
  and map a function over them while preserving the tree structure."
  (tree-vals [this])
  (tree-map [this f]))

(extend-type clojure.lang.PersistentVector
  ITree
  (tree-vals [this] (seq this))
  (tree-map [this f]
    (mapv f this)))

(extend-type clojure.lang.PersistentList
  ITree
  (tree-vals [this] (seq this))
  (tree-map [this f]
    (into '() (reverse (map f this)))))

;; Function varargs become this type
(extend-type clojure.lang.ArraySeq
  ITree
  (tree-vals [this] (seq this))
  (tree-map [this f]
    (mapv f this)))

(extend-type clojure.lang.PersistentArrayMap
  ITree
  (tree-vals [this] (vals this))
  (tree-map [this f]
    (into {} (map (fn [[k v]] [k (f v)]) this))))

(extend-type clojure.lang.PersistentHashMap
  ITree
  (tree-vals [this] (vals this))
  (tree-map [this f]
    (into {} (map (fn [[k v]] [k (f v)]) this))))

(extend-type clojure.lang.PersistentHashSet
  ITree
  (tree-vals [this] (seq this))
  (tree-map [this f]
    (into #{} (map f this))))

(defn tree?
  "Check if an object is a tree.
  If an object is not a tree it is a leaf."
  [x]
  (satisfies? ITree x))

(defn leaf-map
  "Map a function over the leafs of a tree.
  The tree structure is preserved."
  [f x]
  (if (tree? x)
    (tree-map x (partial leaf-map f))
    (f x)))

(defn leaf-seq
  "Enumerate the leaves of a tree as a flat sequence."
  [x]
  (if (tree? x)
    (mapcat leaf-seq (tree-vals x))
    (list x)))

(defn leaf-filter
  "Filter the leaves of a tree with a specified predicate.
  Return a flat sequence of filtered values."
  [pred tree]
  (filter pred (leaf-seq tree)))

(defn leaf-search
  "Find the first leaf matching the specified predicate.
  Return nil if no such value is found."
  [pred tree]
  (first (leaf-filter pred tree)))
