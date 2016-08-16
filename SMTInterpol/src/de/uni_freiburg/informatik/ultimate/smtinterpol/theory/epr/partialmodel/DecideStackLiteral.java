package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.partialmodel;

import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.atoms.EprQuantifiedPredicateAtom;

/**
 * Represents a literal on the DPLL decide stack of the EprTheory.
 * This special literal consists of a quantified literal together with a 
 * data structure restricting the possible groundings of that literal.
 * 
 * @author nutz
 */
public class DecideStackLiteral {

	boolean mPolarity;
	EprQuantifiedPredicateAtom mAtom;
	/**
	 * Stores all the groundings for which this.atom is decided with this.polarity
	 * by this DecideStackLiteral
	 */
	IDawg mDawg;
	
	public DecideStackLiteral(boolean polarity, EprQuantifiedPredicateAtom atom, IDawg dawg) {
		mPolarity = polarity;
		mAtom = atom;
		mDawg = dawg;
	}
	
	
}
