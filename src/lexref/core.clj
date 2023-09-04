(ns lexref.core
  (:require
   [lexref.lexref :refer
    [lex-ref? lex-ref-create lex-ref-value lex-ref-inc! lex-ref-dec!]]
   [lexref.tree :refer [leaf-seq leaf-map]]
   [lexref.expr :refer [lex-ref-expr on-list-expr]]
   [lexref.resource :refer [releasable? release!]]))

(defn lex-ref-retain!
  "Create a lexical reference or increase the reference count if it is already reference counted.
  Used when a value is bound to a name in a lexical reference context.
  Only releasable values are handled, other values are returned as is.
  This should probably not be used by end users, use `with-lexref` or `with-lexref-sym` instead." 
  [x]
  (cond (lex-ref? x) (doto x (lex-ref-inc!))
        (releasable? x) (lex-ref-create x 1)
        :else x))

(defn lex-ref-release!
  "Decrement reference count and release.
  Used when a value is unbound from a name in a lexical reference context.
  This should probably not be used by end users, use `with-lexref` or `with-lexref-sym` instead."
  [x]
  (when (lex-ref? x)
    (lex-ref-dec! x)
    (when (zero? @(:count x))
      (release! x))))

(defn- bind-external-name-expr [var-name]
  [var-name `(leaf-map lex-ref-retain! ~var-name)])

(defn with-lexref-sym
  "Symbolic function version of `with-lexrex`.
  Suitable to use for extending list expression transformation through `on-list-expr`."
  ([expr]
   (with-lexref-sym [] expr))
  ([vars expr]
   `(let [~@(mapcat bind-external-name-expr vars)
          result# (leaf-map lex-ref-value ~(lex-ref-expr expr))]
      (run! lex-ref-release! (leaf-seq ~vars))
      result#)))

(defmacro with-lexref
  "Create a lexical reference context and evaluate an expression within it.
  Optionally move variables into the context.
  This should be used to transfer ownership of manually tracked variables
  created in other contexts to this context.
  Passed variables are invalidated and should not be used after evaluating the context.
  To create a context as part of a list expression hook,
  it is easier to use the symbolic function version `with-lexref-sym`"
  ([expr]
   (with-lexref-sym [] expr))
  ([vars expr]
   (with-lexref-sym vars expr)))

;; Hook into let expressions
;; Let expressions are easily transformed using independent lexref contexts for each binding.
;; Then, all bindings are moved into a final context which transforms the body.
(defmethod on-list-expr 'let [bindings & body]
  (let [names (take-nth 2 bindings)
        exprs (take-nth 2 (drop 1 bindings))
        exprs' (map with-lexref-sym exprs)]
    `(let [~@(interleave names exprs')]
       (with-lexref [~@names]
         ~@body))))
