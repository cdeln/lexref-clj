(ns lexref.expr
  "Define functions transforming expressions into their lexically referenced equivalents."
  (:require [lexref.tree :refer [tree? tree-map]]
            [lexref.apply :refer [lex-ref-apply]]))

(declare lex-ref-expr)

(def ^:private macro-whitelist (atom #{}))

(defn allow-macro
  "Whitelist a macro name, allowing expansion during lex-ref-expr transformation."
  [name]
  (swap! macro-whitelist (fn [s] (conj s name))))

(defn- macro? [sym]
  (when (symbol? sym)
    (:macro (meta (resolve sym)))))

(def ^:dynamic *allow-macros* false)

(defmulti on-list-expr
  "Hook into how list expressions are transformed by `lex-ref-expr`.
  List expressionsa are dispatched on the list head symbol,
  and this is the customization point for that.
  The method receives the tail of the list expression."
  identity)

;; Builtin forms such as if, do and fn are neither functions nor macros,
;; and need to be handled by hooks.

(defmethod on-list-expr 'if [cond-expr & body-exprs]
  `(if ~cond-expr
     ~@(map lex-ref-expr body-exprs)))

(defmethod on-list-expr 'do [& args]
  `(do ~@(map lex-ref-expr args)))

(defmethod on-list-expr 'fn [args & body] `(fn ~args ~@body))

(defmethod on-list-expr 'fn* [args & body] `(fn* ~args ~@body))

;; By default, a list expression denoted as function application
(defn- apply-expr [f xs]
  `(lex-ref-apply ~f ~(mapv lex-ref-expr xs)))

;; Most expressions are list expressions.
;; The head element will determine how the expression is transformed.
(defn- list-expr [expr]
  (let [[what & args] expr]
    (if-let [dispatch (get-method on-list-expr what)]
      (apply dispatch args)
      (if (macro? what)
        (if (or *allow-macros* (contains? @macro-whitelist what))
          (lex-ref-expr (macroexpand-1 expr))
          (throw (ex-info (str "Unsupported lexref macro " what) {})))
        (apply-expr what args)))))

(defn- cons? [x]
  (instance? clojure.lang.Cons x))

;; Yeah, lists are not cons in clojure...
(defn- list-like? [x]
  (or (list? x) (cons? x)))

(defn lex-ref-expr
  "Transforms an ordinary expression into a lexically referenced expression.
  Expressions are transformed if they are lists or trees.
  Lists are treated as function or macro applications,
  while trees are recursed with their structured preserved.
  Other expressions are left as is.
  Macros are treated differenly depending on the value of `*allow-macros*` or if the macro name
  has been whitelisted through `allow-macro`.
  If a macro name is allowed, then it will be expanded once and recursed upon.
  Otherwise an error is raised."
  [expr]
  (cond (list-like? expr) (list-expr expr)
        (tree? expr) (tree-map expr lex-ref-expr)
        :else expr))
