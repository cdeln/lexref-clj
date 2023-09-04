(ns lexref.python
  "Define an integration of the python library into this library.
  Ordinary python objects and numpy arrays are supported."
  (:require
   [lexref.core :refer [with-lexref]]
   [lexref.resource :refer [release! equals?]]
   [libpython-clj2.python :as py]
   [libpython-clj2.require :refer [require-python]]
   [libpython-clj2.python.ffi :as py-ffi]
   [libpython-clj2.python.gc :as py-gc]))

(require-python '[builtins :as pyb])
(require-python '[numpy :as np])

(defmethod release! :pyobject [x]
  (py-ffi/Py_DecRef x))

(defmethod equals? :pyobject [a b]
  (if (and (pyb/isinstance a np/ndarray)
           (pyb/isinstance b np/ndarray))
    (let [a-base (py/py.- a base)
          b-base (py/py.- b base)]
      (if (and (nil? a-base)
               (nil? b-base))
        false
        (or (= a-base b)
            (= b-base a))))
    (identical? a b)))

;; Evaluates a function in a python context with disabled gc and grabbed gil.
;; Should probably only be called once, hence not exposed directly.
(defn- with-python-gc-gil [f]
  (py-gc/with-disabled-gc
    (py/with-gil
      (f))))

(def ^:private in-python-context? (atom false))

(defn with-python
  "Creates a python context with disabled gc suitable for reference counting."
  [f]
  (if (compare-and-set! in-python-context? false true)
    (let [result (with-python-gc-gil f)]
      (reset! in-python-context? false)
      result)
    (f)))

(defmacro with-python-lexref
  "Creates a lexically referenced context suitable for evaluating python expressions."
  ([vars body]
   `(with-python
      (fn []
        (with-lexref ~vars
          ~body))))
  ([body]
   `(with-python-lexref [] ~body)))
