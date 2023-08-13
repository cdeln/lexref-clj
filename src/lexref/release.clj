(ns lexref.release)

(defprotocol IRelease
  (release! [this]))

(defn releasable? [x]
  (satisfies? IRelease x))
