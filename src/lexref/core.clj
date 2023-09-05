(ns lexref.core
  "Define the core end user functionality of this library."
  (:require
   [lexref.lexref :refer
    [lex-ref? lex-ref-create lex-ref-value lex-ref-inc! lex-ref-dec!]]
   [lexref.tree :refer [leaf-seq leaf-map]]
   [lexref.expr :refer [lex-ref-expr on-list-expr]]
   [lexref.apply :refer [lex-ref-apply]]
   [lexref.resource :refer [releasable? release!]]))

(defn lex-ref-init
  "Initialize a value to a lexical reference, if it is a resource, otherwise return as is.
  Used when a value is passed to a lexical reference context."
  [x]
  (if (releasable? x)
    (lex-ref-create x)
    x))

(defn- bind-external-name-expr [var-name]
  [var-name `(leaf-map lex-ref-init ~var-name)])


(defn with-lexref-sym
  "Symbolic function version of `with-lexrex`.
  Suitable to use for extending list expression transformation through `on-list-expr`."
  ([expr]
   (with-lexref-sym [] expr))
  ([vars expr]
   `(let [~@(mapcat bind-external-name-expr vars)]
      (leaf-map lex-ref-value
                (lex-ref-apply (fn ~vars ~(lex-ref-expr expr)) ~vars)))))


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
