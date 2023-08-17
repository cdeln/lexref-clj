(ns lexref.release)

(defprotocol ISelfRelease
  (self-release! [this]))

(defn release-dispatch [x]
  (if (satisfies? ISelfRelease x)
    ::self-release
    (type x)))

(defmulti release! release-dispatch)

(defn releasable? [x]
  (not (nil? (get-method release! (release-dispatch x)))))

(defmethod release! ::self-release [x] (self-release! x))
