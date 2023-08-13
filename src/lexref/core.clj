(ns lexref.core
  (:require [clojure.pprint :refer [pprint]]))

;;; Release

(defprotocol IRelease
  (release! [this]))

(defn- releasable? [x]
  (satisfies? IRelease x))

(defn- try-release! [x]
  (when (releasable? x)
    (release! x)))


;;; Tree

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

(defn- tree? [x]
  (satisfies? ITree x))

(defn- leaf? [x]
  (not (tree? x)))

(defn- leaf-map [f x]
  (if (tree? x)
    (tree-map x (partial leaf-map f))
    (f x)))

(defn- leaf-seq [x]
  (if (tree? x)
    (mapcat leaf-seq (tree-vals x))
    (list x)))

(defn- leaf-search [pred tree]
  (first (filter pred (leaf-seq tree))))

(defn return [value]
  (fn [& _]
    value))


;;; LexRef

(defrecord LexRef [value count released?]
  IRelease
  (release! [_]
    (dosync
     (assert (zero? @count))
     (assert (not @released?))
     (release! value)
     (alter released? (return true)))))

(defn- lex-ref? [x]
  (instance? LexRef x))

(defn- lex-ref-create
  ([value]
   (lex-ref-create value 0))
  ([value count]
   (assert (releasable? value)
           (str "Value " value " is not releasable"))
   (->LexRef value (ref count) (ref false))))

(defn- lex-ref-value [x]
  (if (lex-ref? x)
    (:value x)
    x))

(defn- lex-ref-inc! [x]
  (dosync
   (alter (:count x) inc)))

(defn- lex-ref-dec! [x]
  (dosync
   (alter (:count x) dec)))

;; For debugging
(comment
  (extend-type java.lang.Number
    IRelease
    (release! [this]
      (println "release number " this))))


;;; Apply

(defn- lex-ref-value-eq?
  ([y-val]
   (partial lex-ref-value-eq? y-val))
  ([y-val x-ref]
   (and (lex-ref? x-ref)
        (identical? y-val (lex-ref-value x-ref)))))

(defn- resolve-lex-ref [x-refs y-val]
  (if-let [x-ref (leaf-search (lex-ref-value-eq? y-val) x-refs)]
    x-ref
    (lex-ref-create y-val)))

(defn- resolve-lex-refs [x-refs y-vals]
  (leaf-map (partial resolve-lex-ref x-refs) y-vals))

(defn lex-ref-dangling? [x]
  (and (lex-ref? x)
       (zero? @(:count x))))

(defn lex-ref-fn-arg [x]
  (lex-ref-value x))

(defn- lex-ref-apply [f x-refs]
  (let [y-vals (apply f (leaf-map lex-ref-fn-arg x-refs))
        y-refs (resolve-lex-refs x-refs y-vals)]
    (run! lex-ref-inc! (leaf-seq y-refs))
    (run! release! (filter lex-ref-dangling? (leaf-seq x-refs)))
    (run! lex-ref-dec! (leaf-seq y-refs))
    y-refs))

(defn- lex-ref->map [x]
  (cond (lex-ref? x) {:value (:value x)
                      :count @(:count x)
                      :released? @(:released? x)}
        (tree? x) (leaf-map lex-ref->map x)
        :else x))

(comment
  (let [temp (lex-ref-create 13)
        bound (lex-ref-create 37 1)]
    (println)
    (println "temp before:  " (lex-ref->map temp))
    (println "bound before: " (lex-ref->map bound))
    (let [result (lex-ref-apply (fn [a _] a) [temp bound])]
      (println "result        " (lex-ref->map result))
      (println "temp after:   " (lex-ref->map temp))
      (println "bound after:  " (lex-ref->map bound))))

  (let [temp (lex-ref-create 13)
        bound (lex-ref-create 37 1)]
    (println)
    (println "temp before:  " (lex-ref->map temp))
    (println "bound before: " (lex-ref->map bound))
    (let [result (lex-ref-apply (fn [_ b] [b b]) [temp bound])]
      (println "result        " (lex-ref->map result))
      (println "temp after:   " (lex-ref->map temp))
      (println "bound after:  " (lex-ref->map bound))))
)


;;; Expr

(declare lex-ref-expr)

(defn cons? [x]
  (instance? clojure.lang.Cons x))

(defn list-like? [x]
  (or (list? x) (cons? x)))

(defn lex-ref-retain [x]
  (if (lex-ref? x)
    (do
      (lex-ref-inc! x)
      x)
    (lex-ref-create x 1)))

(defn lex-ref-release! [x]
  (when (lex-ref? x)
    (lex-ref-dec! x)
    (when (zero? @(:count x))
      (release! x))))

(defn lex-ref-retain-expr [expr]
  `(lex-ref-retain ~expr))

(defn lex-ref-let-expr [bindings & body]
  (let [names (take-nth 2 bindings)
        exprs (map (comp lex-ref-retain-expr lex-ref-expr)
                   (take-nth 2 (drop 1 bindings)))]
    `(let [~@(interleave names exprs)
           result# (do ~@(map lex-ref-expr body))]
       (run! lex-ref-release! [~@names])
       result#)))

(defn lex-ref-apply-expr [f xs]
  `(lex-ref-apply ~f ~(mapv lex-ref-expr xs)))

(defn lex-ref-list-expr [expr]
  (let [[what & args] expr]
    (cond
      (= what 'let) (apply lex-ref-let-expr args)
      :else (lex-ref-apply-expr what args))))

(defn lex-ref-expr [expr]
  (cond (list-like? expr) (lex-ref-list-expr expr)
        (tree? expr) (leaf-map lex-ref-expr expr)
        :else expr))

(defn lex-ref-bind-expr [var-name]
  [var-name `(lex-ref-create ~var-name 1)])

(defmacro with-lex-ref
  "Create a lexical reference context, evaluate `expr` within it.
  Optionally move expiring `vars` into the context.
  This should be used to transfer ownership of manually tracked variables
  created in other contexts to this context. Passed `vars` are invalidated and
  should not be used after evaluating the context."
  ([expr]
   `(with-lex-ref [] ~expr))
  ([vars expr]
   `(let [~@(mapcat lex-ref-bind-expr vars)
          result# (lex-ref-value ~(lex-ref-expr expr))]
      (run! lex-ref-release! ~vars)
      result#)))

(comment
  (def outer-var 3)
  (with-lex-ref [outer-var]
    (let [inner-var (* outer-var outer-var)]
      (+ outer-var inner-var)))
)
