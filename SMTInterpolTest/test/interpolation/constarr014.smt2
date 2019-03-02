(set-option :produce-proofs true)
(set-option :produce-interpolants true)
(set-option :proof-transformation LURPI)
(set-option :simplify-interpolants true)
(set-option :proof-check-mode true)
(set-option :interpolant-check-mode true)

(set-logic QF_AUFLIA)

(declare-fun i1 () Int)
(declare-fun i2 () Int)
(declare-fun j1 () Int)
(declare-fun j2 () Int)
(declare-fun k11 () Int)
(declare-fun k12 () Int)
(declare-fun k21 () Int)
(declare-fun k22 () Int)

(declare-fun v () Int)
(declare-fun v1 () Int)
(declare-fun v2 () Int)
(declare-fun v11 () Int)
(declare-fun v12 () Int)
(declare-fun v21 () Int)
(declare-fun v22 () Int)

(declare-fun x () Int)

(declare-fun a () (Array Int Int))
(declare-fun b () (Array Int Int))
(declare-fun s () (Array Int Int))
(declare-fun a1 () (Array Int Int))
(declare-fun s2 () (Array Int Int))

(declare-fun pa ((Array Int Int)) Bool)

(assert (! (and (= a (store s i1 v1)) (pa a)
	(= a (store a1 k11 v11)) (not (= i1 k11)) (= (select a1 j1) x) (= i1 j1) (< i1 0)
	(= (store a k21 v21) s2) (< k21 0)) :named A))
(assert (! (and (= b (store s i2 v2)) (not (pa b))
	(= x v) (= ((as const (Array Int Int)) v) (store b k12 v12)) (< 0 k12)
	(< 0 i2) (= b (store s2 k22 v22)) (not (= i2 k22))) :named B))

(check-sat)
(get-proof)
(get-interpolants A B)
(exit)