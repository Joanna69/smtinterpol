(set-option :produce-proofs true)
(set-option :proof-check-mode true)

(set-logic QF_AX)
(declare-sort U 0)
(declare-fun i () U)
(declare-fun j () U)
(declare-fun k () U)
(declare-fun v () U)
(declare-fun a () (Array U U))
(declare-fun b () (Array U U))
(assert (= i j))
(assert (not (= i k)))
(assert (= b (store a k v)))
(assert (not (= (select a i) (select b j))))

(check-sat)
(set-option :print-terms-cse false)
(get-proof)