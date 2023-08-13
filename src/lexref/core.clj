(ns lexref.core
  (:require
   [lexref.lexref :refer
    [lex-ref? lex-ref-create lex-ref-value lex-ref-inc! lex-ref-dec!]]
   [lexref.apply :refer [lex-ref-apply]]
   [lexref.tree :refer
    [tree? leaf-map]]
   [lexref.release :refer [release!]]))

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

;;; Expr

(defn- cons? [x]
  (instance? clojure.lang.Cons x))

(defn- list-like? [x]
  (or (list? x) (cons? x)))

(declare lex-ref-expr)

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
