(ns lexref.tree-test
  (:require [clojure.test :refer :all]
            [lexref.tree :refer :all]))

(deftest test-leaf-map
  (testing "leaf-map"
    (is (= (leaf-map vector '(1 [2 {:a :b :c #{3 4}}]))
           '([1] [[2] {:a [:b] :c #{[3] [4]}}])))))

(deftest test-leaf-seq
  (testing "leaf-collect"
    (let [s (leaf-seq '(1 [2 {:a :b :c #{3 4}}]))]
      (is (or (= s '(1 2 :b 3 4))
              (= s '(1 2 :b 4 3)))))))
