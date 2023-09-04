(ns lexref.resource
  "Define the interface of a lexical reference resource.")

(defmulti release!
  "Hook into how a resource is released.
  Dispatches on object type. Note that this can be overriden with meta."
  type)

(defn releasable?
  "Checks if a resource is releasable.
  True if there exist a matching method for `release!`."
  [x]
  (not (nil? (get-method release! (type x)))))

(defmulti equals?
  "Hook into how resource values are compared for equality.
  Useful when objects can alias each other outside of the JVM,
  a common thing in numeric computation."
  (fn [a b] [(type a) (type b)]))

;; Default checks for equality on the JVM
(defmethod equals? :default [a b] (identical? a b))
