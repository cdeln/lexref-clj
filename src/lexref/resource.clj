(ns lexref.resource
  "Defines interface of a lexical reference resource.")

(defprotocol ISelfRelease
  (self-release! [this]))

(defn- release-dispatch [x]
  (if (satisfies? ISelfRelease x)
    ::self-release
    (type x)))

(defmulti release!
  "Hook into how a resource is released.
  Dispatch is on type, with the exception of objects implementing `ISelfRelease`,
  which are delegated to the `self-release!` method instead."
  release-dispatch)

(defn releasable?
  "Checks if a resource is releasable, which is true if it implements `ISelfRelease` or
  if there exists a `release!` method for the type of the resource."
  [x]
  (not (nil? (get-method release! (release-dispatch x)))))

;; Default delegation to self releasable objects
(defmethod release! ::self-release [x] (self-release! x))

(defmulti equals?
  "Hook into how resource values are compared for equality.
  Useful when objects can alias each other outside of the JVM,
  a common thing in numeric computation."
  (fn [a b] [(type a) (type b)]))

;; Default checks for equality on the JVM
(defmethod equals? :default [a b] (identical? a b))
