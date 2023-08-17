(ns lexref.lexref
  (:require [lexref.release :refer [ISelfRelease release! releasable?]]))

(defrecord LexRef [value count released?]
  ISelfRelease
  (self-release! [_]
    (dosync
     (assert (zero? @count))
     (assert (not @released?))
     (release! value)
     (alter released? (fn [_] true)))))

(defn lex-ref? [x]
  (instance? LexRef x))

(defn lex-ref-create
  "Create a lexical reference from a `value` and optionally an initial `count`.
  If left out, `count` defaults to 0, representing a temporary value.
  If a lexical reference is created and bound to name, it should be set to 1."
  ([value]
   (lex-ref-create value 0))
  ([value count]
   (assert (releasable? value)
           (str "Lexical reference value " value " is not releasable"))
   (assert (not (lex-ref? value))
           (str "Lexical reference value " value " is a lexical reference"))
   (->LexRef value (ref count) (ref false))))

(defn lex-ref-value
  "Return the value hold by a lexical reference.
  If `x` is not a lexical reference it is returned directly."
  [x]
  (if (lex-ref? x)
    (:value x)
    x))

(defn lex-ref-inc! [x]
  (assert (lex-ref? x))
  (dosync
   (alter (:count x) inc)))

(defn lex-ref-dec! [x]
  (assert (lex-ref? x))
  (dosync
   (alter (:count x) dec)))
