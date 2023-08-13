(ns lexref.tree)

(defprotocol ITree
  (tree-vals [this])
  (tree-map [f this]))

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

;; Function varargs
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

(defn tree? [x]
  (satisfies? ITree x))

(defn leaf-map [f x]
  (if (tree? x)
    (tree-map x (partial leaf-map f))
    (f x)))

(defn leaf-seq [x]
  (if (tree? x)
    (mapcat leaf-seq (tree-vals x))
    (list x)))

(defn leaf-filter [pred tree]
  (filter pred (leaf-seq tree)))

(defn leaf-search [pred tree]
  (first (leaf-filter pred tree)))
