(ns lexref.dev
  (:gen-class)
  (:require
   [lexref.core :refer [with-lex-ref]]
   [lexref.lexref :refer [lex-ref? lex-ref-create]]
   [lexref.apply :refer [lex-ref-apply]]
   [lexref.tree :refer [tree? leaf-map]]
   [lexref.release :refer [ISelfRelease release!]]
   [libpython-clj2.python :as py]
   [libpython-clj2.require :refer [require-python]]
   [libpython-clj2.python.ffi :as py-ffi]
   [libpython-clj2.python.gc :as py-gc]))

(require-python '[builtins :as pyb])
(require-python '[numpy :as np])

(extend-type java.lang.Number
  ISelfRelease
  (self-release! [this]
    (println "release number " this)))

(defn- lex-ref->map [x]
  (cond (lex-ref? x) {:value (:value x)
                      :count @(:count x)
                      :released? @(:released? x)}
        (tree? x) (leaf-map lex-ref->map x)
        :else x))

(println "Test return temporary argument")
(let [temp (lex-ref-create 13)
      bound (lex-ref-create 37 1)]
  (println)
  (println "temp before:  " (lex-ref->map temp))
  (println "bound before: " (lex-ref->map bound))
  (let [result (lex-ref-apply (fn [a _] a) [temp bound])]
    (println "result        " (lex-ref->map result))
    (println "temp after:   " (lex-ref->map temp))
    (println "bound after:  " (lex-ref->map bound))))
(println)

(println "Test return bound argument")
(let [temp (lex-ref-create 13)
      bound (lex-ref-create 37 1)]
  (println)
  (println "temp before:  " (lex-ref->map temp))
  (println "bound before: " (lex-ref->map bound))
  (let [result (lex-ref-apply (fn [_ b] [b b]) [temp bound])]
    (println "result        " (lex-ref->map result))
    (println "temp after:   " (lex-ref->map temp))
    (println "bound after:  " (lex-ref->map bound))))


(println "Test with-lex-ref without external var")
(def outer-var 3)
(with-lex-ref [outer-var]
  (let [inner-var (* outer-var outer-var)]
    (+ outer-var inner-var)))
(println)

(println "Test with-lex-ref reduce")
(with-lex-ref
  (let [x 1 y 2 z 4]
    (reduce + [x y z])))
(println)

(defmethod release! :pyobject [x]
  (when (pyb/isinstance x np/ndarray)
    (println "release numpy array: " (np/shape x))
    (py-ffi/Py_DecRef x)))

(defmacro with-python
  ([vars body]
   `(py-gc/with-disabled-gc
      (py/with-gil
        (with-lex-ref ~vars
          ~body))))
  ([body]
   `(with-python [] ~body)))


(defn -main [& args]
  (println "create x")
  (def x
    (time (with-python
            (np/add (np/ones [1000 1000 1000] :dtype :uint8)
                    (np/ones [1000 1000 1000] :dtype :uint8)))))
  (println "move x, create y")
  (def y
    (time (with-python [x]
            (np/add x x))))
  (println "release y")
  (time (release! y))
  (System/exit 0))
