(set-option :produce-proofs true)
(set-option :proof-check-mode true)
(set-option :model-check-mode true)
(set-option :print-terms-cse false)

(set-logic QF_UFLIRA)

(declare-sort U 0)
(declare-fun g (U) Int)
(declare-const u U)
(declare-const v U)
(declare-fun f (Int) U)
(declare-const x Int)
(declare-const y Int)
(declare-fun h (Real) U)
(declare-const r Real)
(declare-const s Real)

(push 1)
(assert (= u v))
(assert (not (= v u)))
(check-sat)
(get-proof)
(pop 1)

(push 1)
(assert (= u v))
(assert (not (= (g v) (g u))))
(check-sat)
(get-proof)
(pop 1)

(push 1)
(assert (= x y))
(assert (not (= y x)))
(check-sat)
(get-proof)
(pop 1)

(push 1)
(assert (= (- x y) 0))
(assert (not (= (f y) (f x))))
(check-sat)
(get-proof)
(pop 1)

(push 1)
(assert (= (* 2 x) (+ (* 2 y) 1)))
(check-sat)
(get-proof)
(pop 1)

(push 1)
(assert (>= (* 3 x) 1))
(assert (<= (* 3 x) 2))
(check-sat)
(get-proof)
(pop 1)

(push 1)
(assert (>= (* (- 3) x) 1))
(assert (<= (* (- 3) x) 2))
(check-sat)
(get-proof)
(pop 1)

(push 1)
(assert (= r s))
(assert (not (= s r)))
(check-sat)
(get-proof)
(pop 1)

(push 1)
(assert (= (- r s) 0))
(assert (not (= (h s) (h r))))
(check-sat)
(get-proof)
(pop 1)

(push 1)
(assert (<= r s))
(assert (>= r s))
(assert (not (= (h s) (h r))))
(check-sat)
(get-proof)
(pop 1)

(push 1)
(assert (= (+ (* 2 x) r) (+ (* 2 y) r 1)))
(check-sat)
(get-proof)
(pop 1)

(push 1)
(assert (= (+ (* 2 x) r) (+ (* 2 y) 1)))
(assert (= (to_int (/ r 2)) (/ r 2)))
(check-sat)
(get-proof)
(pop 1)

