package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.clauses;

import de.uni_freiburg.informatik.ultimate.logic.Theory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Literal;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.EprStateManager;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.EprTheory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.TTSubstitution;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.atoms.EprPredicateAtom;

public abstract class EprUnitClause extends EprClause {
	
	/**
	 * The eprClause this clause has been instantiated from.
	 * TODO: currently this field is duplicate code with EprDerivedClause..
	 */
	EprClause mExplanation;

	public EprUnitClause(Literal[] literals, EprTheory eprTheory,
			EprClause explanation, 
			boolean isFreshAlphaRenaming, TTSubstitution freshAlphaRen) {
		super(literals, eprTheory, isFreshAlphaRenaming, freshAlphaRen);
		assert (eprQuantifiedPredicateLiterals.length == 1 && groundLiterals.length == 0)
		|| (eprQuantifiedPredicateLiterals.length == 0 && groundLiterals.length == 1) :
			"not a unit clause";
//		if (isFreshAlphaRenaming) {
//			mExplanation = explanation.getFreshAlphaRenamedVersion(freshAlphaRen);
//		} else {
			mExplanation = explanation;
//		}
	}
	
	public abstract Literal getPredicateLiteral();
	
	public abstract EprPredicateAtom getPredicateAtom();
	
	public EprClause getExplanation() {
		return mExplanation;
	}
}
