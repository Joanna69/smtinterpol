package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.FunctionSymbol;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Theory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.atoms.EprGroundPredicateAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.atoms.EprPredicateAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.atoms.EprQuantifiedPredicateAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.clauses.ClauseEprGroundLiteral;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.clauses.ClauseEprLiteral;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.clauses.ClauseEprQuantifiedLiteral;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.clauses.EprClause;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.IDawg;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.partialmodel.DecideStackDecisionLiteral;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.partialmodel.DecideStackLiteral;

/**
 * Represents an uninterpreted predicate that the EPR theory reasons about.
 * Stores and updates a model for that predicate.
 * If setting a literal leads to a conflict, that conflict is reported.
 * 
 * @author nutz
 */
public class EprPredicate {

	private final int mArity;
	private final FunctionSymbol mFunctionSymbol;
	
	
	/**
	 * Every predicate symbol has canonical TermVariables for each of its argument positions.
	 * They form the signature of the corresponding Dawgs on the decide stack.
	 */
	private final SortedSet<TermVariable> mTermVariablesForArguments;

	final EprTheory mEprTheory;
	
	/**
	 * Contains all DecideStackLiterals which talk about this EprPredicate.
	 */
	private Set<DecideStackLiteral> mDecideStackLiterals =
			new HashSet<DecideStackLiteral>();
	
	/**
	 * Storage to track where this predicate occurs in the formula with at least one quantified argument.
	 */
	private HashMap<EprClause, HashSet<ClauseEprLiteral>> mQuantifiedOccurences = 
			new HashMap<EprClause, HashSet<ClauseEprLiteral>>();

	private HashMap<EprClause, HashSet<ClauseEprLiteral>> mGroundOccurences = 
			new HashMap<EprClause, HashSet<ClauseEprLiteral>>();
	
	private HashSet<EprGroundPredicateAtom> mDPLLAtoms = new HashSet<EprGroundPredicateAtom>();
	
	private HashMap<TermTuple, EprGroundPredicateAtom> mPointToAtom = new HashMap<TermTuple, EprGroundPredicateAtom>();
	private HashMap<TermTuple, EprQuantifiedPredicateAtom> mTermTupleToAtom = new HashMap<TermTuple, EprQuantifiedPredicateAtom>();

	public EprPredicate(FunctionSymbol fs, EprTheory eprTheory) {
		this.mFunctionSymbol = fs;
		this.mArity = fs.getParameterSorts().length;
		this.mEprTheory = eprTheory;

		TreeSet<TermVariable> tva = new TreeSet<TermVariable>(EprHelpers.getColumnNamesComparator());
		for (int i = 0; i < mArity; i++) {
			String tvName = mFunctionSymbol.getName() + "_arg_" + i;
			tva.add(
					mEprTheory.getTheory().createFreshTermVariable(tvName, fs.getParameterSorts()[i]));
			
		}
		mTermVariablesForArguments = Collections.unmodifiableSortedSet(tva);
	}

	public void addQuantifiedOccurence(ClauseEprQuantifiedLiteral l, EprClause eprClause) {
		HashSet<ClauseEprLiteral> val = mQuantifiedOccurences.get(eprClause);
		if (val == null) {
			val = new HashSet<ClauseEprLiteral>();
			mQuantifiedOccurences.put(eprClause, val);
		}
		val.add(l);
	}
	
	private HashMap<EprClause, HashSet<ClauseEprLiteral>> getQuantifiedOccurences() {
		return mQuantifiedOccurences;
	}
	
	public void addGroundOccurence(ClauseEprGroundLiteral l, EprClause eprClause) {
		HashSet<ClauseEprLiteral> val = mGroundOccurences.get(eprClause);
		if (val == null) {
			val = new HashSet<ClauseEprLiteral>();
			mGroundOccurences.put(eprClause, val);
		}
		val.add(l);
	}
	
	private HashMap<EprClause, HashSet<ClauseEprLiteral>> getGroundOccurences() {
		return mGroundOccurences;
	}
	
	public HashMap<EprClause, HashSet<ClauseEprLiteral>> getAllEprClauseOccurences() {
		HashMap<EprClause, HashSet<ClauseEprLiteral>> quantifiedOccurences = 
				getQuantifiedOccurences();
		HashMap<EprClause, HashSet<ClauseEprLiteral>> groundOccurences = 
				getGroundOccurences();

		HashMap<EprClause, HashSet<ClauseEprLiteral>> allOccurences = 
				new HashMap<EprClause, HashSet<ClauseEprLiteral>>(quantifiedOccurences);
		for (Entry<EprClause, HashSet<ClauseEprLiteral>> en : groundOccurences.entrySet()) {
			if (allOccurences.containsKey(en.getKey())) {
				allOccurences.get(en.getKey()).addAll(en.getValue());
			} else {
				allOccurences.put(en.getKey(), en.getValue());
			}
		}
		return allOccurences;
	}

	public void addDPLLAtom(EprGroundPredicateAtom egpa) {
		mDPLLAtoms.add(egpa);
	}
	
	public HashSet<EprGroundPredicateAtom> getDPLLAtoms() {
		return mDPLLAtoms;
	}
	
	/**
	 * Retrieve the ground atom belonging to TermTuple tt.
	 * Creates a new atom if no atom exists for tt.
	 * Note: this method assumes that tt only contains constants.
	 * Use getAtomForTermTuple in order to obtain a quantified atom.
	 */
	private EprGroundPredicateAtom getAtomForPoint(TermTuple point, Theory mTheory, int assertionStackLevel) {
		assert point.getFreeVars().size() == 0 : "Use getAtomForTermTuple, if tt is quantified";
		EprGroundPredicateAtom result = mPointToAtom.get(point);
		if (result == null) {
			ApplicationTerm newTerm = mTheory.term(this.mFunctionSymbol, point.terms);
			result = new EprGroundPredicateAtom(newTerm, 0, //TODO: hash
					assertionStackLevel,
					this);
			mPointToAtom.put(point, result);
			addDPLLAtom(result);
			
			// when we create a new ground atom, we have to inform the DPLLEngine if the EprTheory already knows
			// something about it
			for (DecideStackLiteral dsl : this.getDecideStackLiterals()) {
				EprClause conflict = mEprTheory.getStateManager().setGroundAtomIfCoveredByDecideStackLiteral(dsl, result);
				if (conflict != null) {
					assert false : "what now? give to EprTheory somehow so it can be returned by checkpoint??";
				}
			}
		}
		return result;
	}

	/**
	 * Retrieve the quantified atom belonging to TermTuple tt.
	 * Creates a new atom if no atom exists for tt.
	 * Note: this method assumes that tt has at least one TermVariable (i.e. is quantified).
	 * Use getAtomForPoint in order to obtain a ground atom.
	 * @param tt
	 * @param mTheory
	 * @param assertionStackLevel
	 * @return
	 */
	private EprQuantifiedPredicateAtom getAtomForQuantifiedTermTuple(TermTuple tt, Theory mTheory, int assertionStackLevel) {
		assert tt.getFreeVars().size() > 0 : "Use getAtomForPoint, if tt is ground";
		EprQuantifiedPredicateAtom result = mTermTupleToAtom.get(tt);
		
		if (result == null) {
			ApplicationTerm newTerm = mTheory.term(this.mFunctionSymbol, tt.terms);
			result = new EprQuantifiedPredicateAtom(newTerm, 0, //TODO: hash
					assertionStackLevel,
					this);
			mTermTupleToAtom.put(tt, result);
		}
		return result;
	}
	
	public EprPredicateAtom getAtomForTermTuple(TermTuple tt, Theory mTheory, int assertionStackLevel) {
		if (tt.getFreeVars().size() > 0) {
			return getAtomForQuantifiedTermTuple(tt, mTheory, assertionStackLevel);
		} else {
			return getAtomForPoint(tt, mTheory, assertionStackLevel);
		}
	}
	
	public String toString() {
		return "EprPred: " + mFunctionSymbol.getName();
	}

	/**
	 * 
	 *  @return null if the model of this predicate is already complete, a DecideStackLiteral
	 *          otherwise.
	 */
	public DecideStackLiteral getNextDecision() {
		IDawg<ApplicationTerm, TermVariable> undecidedPoints = computeUndecidedPoints();

		if (undecidedPoints.isEmpty()) {
			return null;
		} else {
			return new DecideStackDecisionLiteral(true, this, undecidedPoints);
		}
	}

	private IDawg<ApplicationTerm, TermVariable> computeUndecidedPoints() {
		IDawg<ApplicationTerm, TermVariable> positivelySetPoints = 
				mEprTheory.getDawgFactory().createEmptyDawg(mTermVariablesForArguments);
		IDawg<ApplicationTerm, TermVariable> negativelySetPoints =
				mEprTheory.getDawgFactory().createEmptyDawg(mTermVariablesForArguments);
		IDawg<ApplicationTerm, TermVariable> undecidedPoints = 
				mEprTheory.getDawgFactory().createEmptyDawg(mTermVariablesForArguments);

		for (DecideStackLiteral dsl : mDecideStackLiterals) {
			if (dsl.getPolarity()) {
				//positive literal
				positivelySetPoints.addAll(dsl.getDawg());
			} else {
				//negative literal
				negativelySetPoints.addAll(dsl.getDawg());
			}
		}

		// the ground predicates' decide statuses are managed by the DPLLEngine
		for (EprGroundPredicateAtom at : mDPLLAtoms) {
			if (at.getDecideStatus() == null) {
				// not yet decided
				undecidedPoints.add(EprHelpers.convertTermArrayToConstantList(at.getArguments()));
			} else if (at.getDecideStatus().getSign() == 1) {
				// positively set
				positivelySetPoints.add(EprHelpers.convertTermArrayToConstantList(at.getArguments()));
			} else {
				// negatively set
				negativelySetPoints.add(EprHelpers.convertTermArrayToConstantList(at.getArguments()));
			}
		}

		IDawg<ApplicationTerm, TermVariable> allDecidedPoints = 
				mEprTheory.getDawgFactory().createEmptyDawg(mTermVariablesForArguments);
		allDecidedPoints.addAll(positivelySetPoints);
		allDecidedPoints.addAll(negativelySetPoints);

		undecidedPoints.addAll(allDecidedPoints.complement());
		return undecidedPoints;
	}

	/**
	 * Called when an EprClause is disposed of (typically because of a pop command).
	 * Updates internal data structures of this EprPredicate accordingly.
	 * 
	 * @param eprClause
	 */
	public void notifyAboutClauseDisposal(EprClause eprClause) {
		mQuantifiedOccurences.remove(eprClause);
		mGroundOccurences.remove(eprClause);
	}

	public int getArity() {
		return mArity;
	}

	public FunctionSymbol getFunctionSymbol() {
		return mFunctionSymbol;
	}
	
	public SortedSet<TermVariable> getTermVariablesForArguments() {
		return mTermVariablesForArguments;
	}

	/**
	 * This has to be called when a literal that talks about this EprPredicate is put on the epr decide stack.
	 * @param dsl
	 */
	public void registerDecideStackLiteral(DecideStackLiteral dsl) {
		mDecideStackLiterals.add(dsl);
	}

	/**
	 * This has to be called when a literal that talks about this EprPredicate is removed from the epr decide stack.
	 * @param dsl
	 */
	public void unregisterDecideStackLiteral(DecideStackLiteral dsl) {
		mDecideStackLiterals.remove(dsl);
	}		

	public Set<DecideStackLiteral> getDecideStackLiterals() {
		return mDecideStackLiterals;
	}

}
