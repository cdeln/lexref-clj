(ns lexref.expr-test
  (:require [clojure.test :refer :all]
            [lexref.expr :refer :all]))

(deftest test-lex-ref-expr
  (testing "binary op"
    (is (= (lex-ref-expr '(+ 1 2))
           '(lexref.apply/lex-ref-apply + [1 2]))))
  (testing "ternary op"
    (is (= (lex-ref-expr '(+ 1 2 3))
           '(lexref.apply/lex-ref-apply + [1 2 3]))))
  (testing "nested function application"
    (is (= (lex-ref-expr '(+ 1 (* 2 3)))
           '(lexref.apply/lex-ref-apply + [1 (lexref.apply/lex-ref-apply * [2 3])]))))
  (testing "higher order function"
    (is (= (lex-ref-expr '(reduce + [1 2 3]))
           '(lexref.apply/lex-ref-apply reduce [+ [1 2 3]])))))
