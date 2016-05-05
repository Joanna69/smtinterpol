package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.clauses;

import java.util.ArrayList;

import de.uni_freiburg.informatik.ultimate.logic.Theory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Literal;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.EprStateManager;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.TTSubstitution;

public class EprBaseClause extends EprNonUnitClause {

	public EprBaseClause(Literal[] literals, Theory theory, EprStateManager stateManager) {
		this(literals, theory, stateManager, false, null);
	}
	
	public EprBaseClause(Literal[] literals, Theory theory, 
			EprStateManager stateManager, boolean freshAlpharenamed, TTSubstitution freshAlphaRen) {
		super(literals, theory, stateManager, freshAlpharenamed, freshAlphaRen);
	}

	@Override
	public EprClause getFreshAlphaRenamedVersion() {
		TTSubstitution sub = new TTSubstitution();
		ArrayList<Literal> newLits = getFreshAlphaRenamedLiterals(sub);
		return new EprBaseClause(newLits.toArray(new Literal[newLits.size()]), 
					mTheory, mStateManager, true, sub);
	}
}
