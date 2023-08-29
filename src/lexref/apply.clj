(ns lexref.apply
  (:require [lexref.lexref :refer
             [lex-ref? lex-ref-value lex-ref-create lex-ref-inc! lex-ref-dec!]]
            [lexref.release :refer [releasable? release!]]
            [lexref.tree :refer
             [leaf-map leaf-filter leaf-search]]))

(declare lex-ref-apply)

(defmulti lex-ref-value-eq? (fn [a b] [(type a) (type b)]))

(defmethod lex-ref-value-eq? :default [a b] (identical? a b))

(defn- value-eq?
  ([y-val]
   (partial value-eq? y-val))
  ([y-val x-ref]
   (lex-ref-value-eq? y-val (lex-ref-value x-ref))))

(defn- resolve-lex-ref [x-refs y-val]
  (if-let [x-ref (leaf-search (value-eq? y-val) x-refs)]
    x-ref
    (if (lex-ref? y-val)
      y-val
      (if (releasable? y-val)
        (lex-ref-create y-val)
        y-val))))

(defn- resolve-lex-refs [x-refs y-vals]
  (leaf-map (partial resolve-lex-ref x-refs) y-vals))

(defn- dangling? [x]
  (and (lex-ref? x)
       (zero? @(:count x))))

(defn- lex-ref-fn [f]
  (fn [& xs]
    (lex-ref-apply f xs)))

(defn- fn-arg [x]
  (if (fn? x)
    (lex-ref-fn x)
    (lex-ref-value x)))

(defn lex-ref-apply
  "Applies a function `f` to sequence of `args`.
  `args` may consist of both values (including functions) and lexical references.
  Lexical references in `args` with zero count will be released at the end of scope,
  unless `f` produces aliased value, which will retain that argument.
  A value of `f` is aliased if it is referentially identical to some object in `args`."
  [f args]
  (run! lex-ref-inc! (leaf-filter lex-ref? args))
  (let [y-vals (apply f (leaf-map fn-arg args))
        y-refs (resolve-lex-refs args y-vals)]
    (run! lex-ref-dec! (leaf-filter lex-ref? args))
    (run! lex-ref-inc! (leaf-filter lex-ref? y-refs))
    (run! release! (leaf-filter dangling? args))
    (run! lex-ref-dec! (leaf-filter lex-ref? y-refs))
    y-refs))
