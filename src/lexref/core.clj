(ns lexref.core
  (:require
   [lexref.tree :refer
    [tree? leaf-map leaf-seq leaf-filter leaf-search]]
   [lexref.release :refer
    [IRelease release!]]
   [lexref.lexref :refer
    [lex-ref? lex-ref-create lex-ref-value lex-ref-inc! lex-ref-dec!]]))

;; For debugging
(comment
  (extend-type java.lang.Number
    IRelease
    (release! [this]
      (println "release number " this))))


;;; Apply

(declare lex-ref-apply)

(defn- lex-ref-value-eq?
  ([y-val]
   (partial lex-ref-value-eq? y-val))
  ([y-val x-ref]
   (and (lex-ref? x-ref)
        (identical? y-val (lex-ref-value x-ref)))))

(defn- resolve-lex-ref [x-refs y-val]
  (if-let [x-ref (leaf-search (lex-ref-value-eq? y-val) x-refs)]
    x-ref
    (if (lex-ref? y-val)
      y-val
      (lex-ref-create y-val))))

(defn- resolve-lex-refs [x-refs y-vals]
  (leaf-map (partial resolve-lex-ref x-refs) y-vals))

(defn- lex-ref-dangling? [x]
  (and (lex-ref? x)
       (zero? @(:count x))))

(defn- lex-ref-fn [f]
  (fn [& xs]
    (lex-ref-apply f xs)))

(defn- lex-ref-fn-arg [x]
  (if (fn? x)
    (lex-ref-fn x)
    (lex-ref-value x)))

(defn- lex-ref-apply [f xs]
  (run! lex-ref-inc! (leaf-filter lex-ref? xs))
  (let [ys (apply f (leaf-map lex-ref-fn-arg xs))
        y-refs (resolve-lex-refs xs ys)]
    (run! lex-ref-dec! (leaf-filter lex-ref? xs))
    (run! lex-ref-inc! (leaf-seq y-refs))
    (run! release! (leaf-filter lex-ref-dangling? xs))
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

(defn- cons? [x]
  (instance? clojure.lang.Cons x))

(defn- list-like? [x]
  (or (list? x) (cons? x)))

(defn- lex-ref-retain [x]
  (if (lex-ref? x)
    (do
      (lex-ref-inc! x)
      x)
    (lex-ref-create x 1)))

(defn- lex-ref-release! [x]
  (when (lex-ref? x)
    (lex-ref-dec! x)
    (when (zero? @(:count x))
      (release! x))))

(defn- retain-expr [expr]
  `(lex-ref-retain ~expr))

(defn- let-expr [bindings & body]
  (let [names (take-nth 2 bindings)
        exprs (map (comp retain-expr lex-ref-expr)
                   (take-nth 2 (drop 1 bindings)))]
    `(let [~@(interleave names exprs)
           result# (do ~@(map lex-ref-expr body))]
       (run! lex-ref-release! [~@names])
       result#)))

(defn- apply-expr [f xs]
  `(lex-ref-apply ~f ~(mapv lex-ref-expr xs)))

(defn- list-expr [expr]
  (let [[what & args] expr]
    (cond
      (= what 'let) (apply let-expr args)
      :else (apply-expr what args))))

(defn- lex-ref-expr [expr]
  (cond (list-like? expr) (list-expr expr)
        (tree? expr) (leaf-map lex-ref-expr expr)
        :else expr))

(defn- bind-expr [var-name]
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
   `(let [~@(mapcat bind-expr vars)
          result# (lex-ref-value ~(lex-ref-expr expr))]
      (run! lex-ref-release! ~vars)
      result#)))

(comment
  (def outer-var 3)
  (with-lex-ref [outer-var]
    (let [inner-var (* outer-var outer-var)]
      (+ outer-var inner-var)))

  (with-lex-ref
    (let [x 1 y 2 z 4]
      (reduce + [x y z])))
)
