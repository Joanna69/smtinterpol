package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.TermTuple;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.IDawgLetter.UniversalDawgLetter;

public class Dawg<LETTER> implements IDawg<LETTER> {
	
	
	private final TermVariable[] mVariables;
	
	private final int mArity;
	
	/*
	 * convention:
	 * states are just integers
	 * 
	 * the initial state is "0"
	 * the accepting state is <mArity>
	 * the sink state is "-1"
	 */
	
	/**
	 * Transition relation of the finite automaton as a nested map.
	 *  --> there are more efficient solutions, probably TODO
	 */
	Map<Integer, Map<IDawgLetter<LETTER>, Integer>> transitionRelation;

	public Dawg(TermVariable[] termVariables) {
		mVariables = termVariables;
		mArity = termVariables.length;
		
		/*
		 * create as an empty dawg
		 */
		addTransition(0, UniversalDawgLetter.INSTANCE, -1);
	}

	private void addTransition(int i, IDawgLetter<LETTER> dawgLetter, int j) {
		Map<IDawgLetter<LETTER>, Integer> letterToTarget = transitionRelation.get(i);
		if (letterToTarget == null) {
			letterToTarget = new HashMap<IDawgLetter<LETTER>, Integer>();
			transitionRelation.put(i, letterToTarget);
		}
		letterToTarget.put(dawgLetter, j);
	}

	@Override
	public IDawgSubstitution join(IDawg other) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IDawg intersect(IDawg fp) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IDawg complement() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IDawg union(IDawg other) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean accepts(TermTuple point) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isUniversal() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public TermVariable[] getVariables() {
		return mVariables;
	}

	@Override
	public void add(Term[] arguments) {
		// TODO Auto-generated method stub
	
	}

	@Override
	public void addAll(IDawg dawg) {
		// TODO Auto-generated method stub
	
	}

	@Override
	public void removeAll(IDawg fpOne) {
		// TODO Auto-generated method stub
	
	}

}
