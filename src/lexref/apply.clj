(ns lexref.apply
  "Define the semantics of function application on lexical references."
  (:require [lexref.lexref :refer
             [lex-ref? lex-ref-value lex-ref-create lex-ref-inc! lex-ref-dec!]]
            [lexref.resource :refer [releasable? release! equals?]]
            [lexref.tree :refer
             [leaf-map leaf-filter leaf-search]]))

(declare lex-ref-apply)

;; Check if a value is equal to the resource hold by a lexical reference.
;; Relies on the `equals?` multimethod.
(defn- lex-ref-value-equals?
  ([y-val]
   (partial lex-ref-value-equals? y-val))
  ([y-val x-ref]
   (equals? y-val (lex-ref-value x-ref))))

;; Resolve a lexical reference against a value.
;; Resolving a value means checking if it equals the resource hold by the lexical reference,
;; and incrementing the reference count accordingly.
;; If the value is releasable it is also wrapped into a lexical reference.
(defn- resolve-lex-ref [x-refs y-val]
  (if-let [x-ref (leaf-search (lex-ref-value-equals? y-val) x-refs)]
    x-ref
    (if (lex-ref? y-val)
      y-val
      (if (releasable? y-val)
        (lex-ref-create y-val)
        y-val))))

;; Resolve a set of values against a set of lexical references.
;; See `resolve-lex-ref` for details.
(defn- resolve-lex-refs [x-refs y-vals]
  (leaf-map (partial resolve-lex-ref x-refs) y-vals))

;; Check if an object is dangling, i.e. not lexically referenced by any name.
(defn- dangling? [x]
  (and (lex-ref? x)
       (zero? @(:count x))))

;; Lift a function to operate on lexical references.
;; This is how higher order functions are supported.
(defn- lex-ref-fn [f]
  (fn [& xs]
    (lex-ref-apply f xs)))

;; Lift a function argument to the apropriate type for lexical referencing.
(defn- fn-arg [x]
  (if (fn? x)
    (lex-ref-fn x)
    (lex-ref-value x)))

(defn lex-ref-apply
  "Applies a function to sequence of arguments`.
  The argument may consist of both raw values (including functions) and lexical references.
  Lexical references with zero count will be released at the end of scope,
  unless the function produces aliased values, which will retain those argument.
  A function return value is aliased if it `equals?` some argument."
  [f args]
  (run! lex-ref-inc! (leaf-filter lex-ref? args))
  (let [y-vals (apply f (leaf-map fn-arg args))
        y-refs (resolve-lex-refs args y-vals)]
    (run! lex-ref-dec! (leaf-filter lex-ref? args))
    (run! lex-ref-inc! (leaf-filter lex-ref? y-refs))
    (run! release! (leaf-filter dangling? args))
    (run! lex-ref-dec! (leaf-filter lex-ref? y-refs))
    y-refs))
