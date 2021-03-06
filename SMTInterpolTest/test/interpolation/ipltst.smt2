(set-option :produce-interpolants true)
(set-option :interpolant-check-mode true)
(set-logic QF_UFLIA)
(declare-fun f (Int) Int)
(declare-fun a () Int)
(declare-fun b () Int)
(declare-fun c () Int)
(declare-fun p () Bool)
(declare-fun ac () Int)
(declare-fun bc () Int)
(declare-fun cc () Int)
(declare-fun pc () Bool)
(assert (! (and (= (f b) 0) (= a ac) (= b bc) (= c cc)) :named root))
(assert (! (and (= a b) (=> p (distinct a c))) :named left))
(assert (! (and (= b c) (=> (not p) (distinct a c))) :named right))
(check-sat)
(get-interpolants left (right) root)
