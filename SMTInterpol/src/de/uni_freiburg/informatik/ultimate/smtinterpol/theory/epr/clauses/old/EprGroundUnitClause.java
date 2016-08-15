package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.clauses.old;

import de.uni_freiburg.informatik.ultimate.logic.Theory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Literal;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.EprTheory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.TTSubstitution;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.atoms.EprGroundPredicateAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.partialmodel.EprStateManager;

public class EprGroundUnitClause extends EprUnitClause {

	public EprGroundUnitClause(Literal literal, EprTheory eprTheory, 
			 EprClauseOld explanation) {
		this(literal, eprTheory, explanation, false);
	}
	
	public EprGroundUnitClause(Literal literal, EprTheory eprTheory, 
			 EprClauseOld explanation,
			boolean freshAlphaRenaming) {
		super(new Literal[] { literal }, eprTheory, explanation, 
				freshAlphaRenaming, new TTSubstitution());
		assert eprQuantifiedPredicateLiterals.length == 0;
		assert groundLiterals.length == 1;
	}

	public Literal getPredicateLiteral() {
		return groundLiterals[0];
	}

	public EprGroundPredicateAtom getPredicateAtom() {
		return (EprGroundPredicateAtom) groundLiterals[0].getAtom();
	}

	@Override
	public boolean isConflictClause() {
		assert false : "TODO";
		return false;
	}

	@Override
	public EprUnitClause getUnitClauseLiteral() {
		assert false : "TODO";
		return null;
	}

	@Override
	public EprClauseOld getFreshAlphaRenamedVersion() {
		//TODO: not so nice, somehow
		return new EprGroundUnitClause(getPredicateLiteral(), mEprTheory, 
				mExplanation, true);
	}
}
