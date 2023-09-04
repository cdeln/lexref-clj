(ns lexref.lexref
  "Defines the lexical reference type with associated methods."
  (:require [lexref.resource :refer [ISelfRelease release! releasable?]]
            [clojure.string :as str]))

(defrecord LexRef [value count released?]
  ISelfRelease
  (self-release! [_]
    (dosync
     (assert (zero? @count))
     (assert (not @released?))
     (release! value)
     (alter released? (fn [_] true)))))

(defn lex-ref?
  "Check if an object is a lexical reference."
  [x]
  (instance? LexRef x))

(defn- short-description
  ([x]
   (short-description x 10))
  ([x n]
   (let [s (str x)
         d "....."]
     (if (< (count s) n)
       s
       (str/join "" (concat (take n (str x)) [d]))))))

(defn lex-ref-create
  "Create a lexical reference from a value and optionally an initial count.
  If left out, count defaults to 0, representing a temporary value.
  If a lexical reference is created and bound to name, it should be set to 1."
  ([value]
   (lex-ref-create value 0))
  ([value count]
   (assert (releasable? value)
           (str "Lexical reference of type '" (type value)
                "' with value '" (short-description value) "' is not releasable"))
   (assert (not (lex-ref? value))
           (str "Lexical reference with value '" (short-description value)
                "' is a lexical reference"))
   (->LexRef value (ref count) (ref false))))

(defn lex-ref-value
  "Return the value hold by a lexical reference.
  If the value is not a lexical reference it is returned directly."
  [x]
  (if (lex-ref? x)
    (:value x)
    x))

(defn lex-ref-inc!
  "Increment the reference count of a lexical reference.
  Return the incremented reference count."
  [x]
  (assert (lex-ref? x))
  (dosync
   (alter (:count x) inc)))

(defn lex-ref-dec!
  "Decrements the reference count of a lexical reference.
  Return the decremented reference count."
  [x]
  (assert (lex-ref? x))
  (dosync
   (alter (:count x) dec)))
