(ns lexref.core
  (:require
   [lexref.lexref :refer
    [lex-ref? lex-ref-create lex-ref-value lex-ref-inc! lex-ref-dec!]]
   [lexref.apply :refer [lex-ref-apply]]
   [lexref.tree :refer
    [tree? leaf-seq leaf-map]]
   [lexref.release :refer [release! releasable?]]))

(defn lex-ref-retain [x]
  (cond (lex-ref? x) (doto x (lex-ref-inc!))
        (releasable? x) (lex-ref-create x 1)
        :else x))

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

(def ^:private macro-whitelist (atom #{}))

(defn allow-macro [sym]
  (swap! macro-whitelist (fn [s] (conj s sym))))

(defmulti on-list-expr identity)

(defmethod on-list-expr 'if [cond-expr & body-exprs]
  `(if ~cond-expr
     ~@(map lex-ref-expr body-exprs)))

(defmethod on-list-expr 'do [& args]
  `(do ~@(map lex-ref-expr args)))

(defmethod on-list-expr 'fn [args & body] `(fn ~args ~@body))

(defmethod on-list-expr 'fn* [args & body] `(fn* ~args ~@body))

(defn- apply-expr [f xs]
  `(lex-ref-apply ~f ~(mapv lex-ref-expr xs)))

(defn- macro? [sym]
  (when (symbol? sym)
    (:macro (meta (resolve sym)))))

(def ^:dynamic *allow-macros* false)

(defn- list-expr [expr]
  (let [[what & args] expr]
    (if-let [dispatch (get-method on-list-expr what)]
      (apply dispatch args)
      (if (macro? what)
        (if (or *allow-macros* (contains? @macro-whitelist what))
          ;`(~what ~@(map lex-ref-expr args))
          (lex-ref-expr (macroexpand-1 expr))
          (throw (ex-info (str "Unsupported lexref macro " what) {})))
        (apply-expr what args)))))

(defn- lex-ref-expr [expr]
  (cond (list-like? expr) (list-expr expr)
        (tree? expr) (leaf-map lex-ref-expr expr)
        :else expr))

(defn- bind-external-name-expr [var-name]
  [var-name `(leaf-map #(lex-ref-create % 1) ~var-name)])

(defn- with-lexref-context
  ([expr]
   (with-lexref-context [] expr))
  ([vars expr]
   `(let [~@(mapcat bind-external-name-expr vars)
          result# (leaf-map lex-ref-value ~(lex-ref-expr expr))]
      (run! lex-ref-release! (leaf-seq ~vars))
      result#)))

(defmacro with-lexref
  "Create a lexical reference context, evaluate `expr` within it.
  Optionally move expiring `vars` into the context.
  This should be used to transfer ownership of manually tracked variables
  created in other contexts to this context. Passed `vars` are invalidated and
  should not be used after evaluating the context."
  ([expr]
   (with-lexref-context [] expr))
  ([vars expr]
   (with-lexref-context vars expr)))

(defmethod on-list-expr 'let [bindings & body]
  (let [names (take-nth 2 bindings)
        exprs (take-nth 2 (drop 1 bindings))
        exprs' (map with-lexref-context exprs)]
    `(let [~@(interleave names exprs')]
       (with-lexref [~@names]
         ~@body))))
