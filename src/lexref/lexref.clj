(ns lexref.lexref
  "Define the lexical reference type with associated methods."
  (:require [lexref.resource :refer [release! releasable?]]
            [clojure.string :as str]))

;; A lexical reference is a simple record type consisting of
;;  - a releasable value
;;  - a reference count, wrapped in a ref
;;  - a boolean ref telling if the lexical reference has been released or not
;; While the boolean is redundant, it is nice to have for debugging purposes.
;; The reference count and the boolean should be refs because they need to be updated simultaneously.
(defrecord LexRef [value count released?])

(defn lex-ref?
  "Check if an object is a lexical reference."
  [x]
  (instance? LexRef x))

;; Helper function for creating readable error messages.
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
  If a lexical reference is created and bound to name, it should be set to 1.
  The value should be releasable, and it should not be a lexical reference."
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
  If the object is not a lexical reference it is returned as is."
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

;; A lexical reference is released by releasing the underlying value.
;; The reference count must be zero, and the reference must not have been released already.
(defmethod release! LexRef [this]
  (dosync
   (assert (zero? @(:count this)))
   (assert (not @(:released? this)))
   (release! (:value this))
   (alter (:released? this) (fn [_] true))))
