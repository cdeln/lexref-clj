(ns lexref.dev
  (:require
   [lexref.core :refer [with-lex-ref]]
   [lexref.lexref :refer [lex-ref? lex-ref-create]]
   [lexref.apply :refer [lex-ref-apply]]
   [lexref.tree :refer [tree? leaf-map]]
   [lexref.release :refer [IRelease]]))

(extend-type java.lang.Number
  IRelease
  (release! [this]
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
