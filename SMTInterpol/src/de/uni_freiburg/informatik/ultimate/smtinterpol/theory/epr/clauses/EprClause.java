package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.clauses;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Clause;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.DPLLAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Literal;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.BinaryRelation;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.EprHelpers;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.EprHelpers.Pair;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.EprPredicate;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.EprTheory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.TTSubstitution;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.TermTuple;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.atoms.EprGroundEqualityAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.atoms.EprGroundPredicateAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.atoms.EprPredicateAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.atoms.EprQuantifiedEqualityAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.atoms.EprQuantifiedPredicateAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.DawgFactory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.IDawg;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.partialmodel.DecideStackDecisionLiteral;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.partialmodel.DecideStackLiteral;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.partialmodel.IEprLiteral;

/**
 * Represents a clause that is only known to the EprTheory.
 * This means that the clause typically contains at least one free 
 * (implicitly forall-quantified) variable -- exceptions, i.e. ground EprClauses may occur through
 * factoring or resolution.
 * 
 * @author Alexander Nutz
 */
public class EprClause {
	
	private final Set<Literal> mDpllLiterals;
	private final EprTheory mEprTheory;
	private final DawgFactory<ApplicationTerm, TermVariable> mDawgFactory;

	private final Set<ClauseLiteral> mLiterals;

	/**
	 * Stores the variables occurring in this clause in the order determined by the HashMap mVariableToClauseLitToPositions
	 */
	private SortedSet<TermVariable> mVariables;

	/**
	 * If this flag is true, the value of mEprClauseState can be relied on.
	 * Otherwise the state must be recomputed.
	 */
	boolean mClauseStateIsDirty = true;

	/**
	 * The current fulfillment state of this epr clause
	 */
	private EprClauseState mEprClauseState;
	
	private IDawg<ApplicationTerm, TermVariable> mConflictPoints;
	
	private UnitPropagationData mUnitPropagationData;
	
	/*
	 * stuff relative to the decide stack border; done as maps now, but maybe one item each is enough??
	 */
	private Map<DecideStackLiteral, Map<ClauseLiteral, IDawg<ApplicationTerm, TermVariable>>> mDecideStackBorderToClauseLitToUnitPoints =
			new HashMap<DecideStackLiteral, Map<ClauseLiteral,IDawg<ApplicationTerm,TermVariable>>>();
	private Map<DecideStackLiteral, EprClauseState> mDecideStackBorderToClauseState = 
			new HashMap<DecideStackLiteral, EprClauseState>();
	private Map<DecideStackLiteral, IDawg<ApplicationTerm, TermVariable>> mDecideStackBorderToConflictPoints =
			new HashMap<DecideStackLiteral, IDawg<ApplicationTerm,TermVariable>>();
	private Map<DecideStackLiteral, UnitPropagationData> mDecideStackBorderToUnitPropagationData =
			new HashMap<DecideStackLiteral, UnitPropagationData>();
	
	private boolean mHasBeenDisposed = false;

	public EprClause(Set<Literal> lits, EprTheory eprTheory) {
		mDpllLiterals = lits;
		mEprTheory = eprTheory;
		mDawgFactory = eprTheory.getDawgFactory();

		// set up the clause..

		Pair<SortedSet<TermVariable>, Set<ClauseLiteral>> resPair = 
				 createClauseLiterals(lits);

		mLiterals = Collections.unmodifiableSet(resPair.second);

		mVariables = Collections.unmodifiableSortedSet(resPair.first);
		
		registerFulfillingOrConflictingEprLiteralInClauseLiterals();
	}


	private void registerFulfillingOrConflictingEprLiteralInClauseLiterals() {
		for (ClauseLiteral cl : getLiterals()) {
			if (!(cl instanceof ClauseEprLiteral)) {
				continue;
			}
			ClauseEprLiteral cel = (ClauseEprLiteral) cl;
			for (IEprLiteral dsl : cel.getEprPredicate().getEprLiterals()) {
				if (cel.isDisjointFrom(dsl.getDawg())) {
					continue;
				}
			
				if (dsl.getPolarity() == cel.getPolarity()) {
					cel.addPartiallyFulfillingEprLiteral(dsl);
				} else {
					cel.addPartiallyConflictingEprLiteral(dsl);
				}
			}	
		}
	}


	/**
	 * Set up the clause in terms of our Epr data structures.
	 * Fills the fields mVariableToClauseLitPositionsTemp and mLiteralsTemp.
	 * 
	 * TODOs:
	 *  - detect tautologies
	 *  - detect duplicate literals
	 * 
	 * @param lits The (DPLL) literals that this clause is created from.
	 * @return 
	 * @return 
	 */
	private Pair<SortedSet<TermVariable>, Set<ClauseLiteral>> createClauseLiterals(Set<Literal> lits) {

		SortedSet<TermVariable> variables = new TreeSet<TermVariable>(EprHelpers.getColumnNamesComparator());
		HashSet<ClauseLiteral> literals = new HashSet<ClauseLiteral>();

		Set<EprQuantifiedEqualityAtom> quantifiedEqualities = new HashSet<EprQuantifiedEqualityAtom>();

		for (Literal l : lits) {
			boolean polarity = l.getSign() == 1;
			DPLLAtom atom = l.getAtom();
			
			if (atom instanceof EprQuantifiedPredicateAtom) {
				EprQuantifiedPredicateAtom eqpa = (EprQuantifiedPredicateAtom) atom;
				
				variables.addAll(
						Arrays.asList(
								atom.getSMTFormula(mEprTheory.getTheory()).getFreeVars()));

				ClauseEprQuantifiedLiteral newL = new ClauseEprQuantifiedLiteral(
						polarity, eqpa, this, mEprTheory);
				literals.add(newL);
				eqpa.getEprPredicate().addQuantifiedOccurence(newL, this);
				
				
				continue;
			} else if (atom instanceof EprGroundPredicateAtom) {
				EprGroundPredicateAtom egpa = (EprGroundPredicateAtom) atom;
				ClauseEprGroundLiteral newL = new ClauseEprGroundLiteral(
						polarity, egpa, this, mEprTheory);
				literals.add(newL);
				egpa.getEprPredicate().addGroundOccurence(newL, this);
				continue;
			} else if (atom instanceof EprQuantifiedEqualityAtom) {
				// quantified equalities we don't add to the clause literals but 
				// just collect them for adding exceptions to the other quantified
				// clause literals later
				assert atom == l : "should have been eliminated by destructive equality reasoning";
				quantifiedEqualities.add((EprQuantifiedEqualityAtom) atom);
				continue;
			} else if (atom instanceof EprGroundEqualityAtom) {
				assert false : "do we really have this case?";
				continue;
//			} else if (atom instanceof CCEquality) {
//				(distinction form DPLLAtom does not seem necessary)
//				continue;
			} else {
				// atom is a "normal" Atom from the DPLLEngine
				literals.add(
						new ClauseDpllLiteral(polarity, atom, this, mEprTheory));
				continue;
			}
		}
		
		for (ClauseLiteral cl : literals) {
			if (cl instanceof ClauseEprQuantifiedLiteral) {
				ClauseEprQuantifiedLiteral ceql = (ClauseEprQuantifiedLiteral) cl;
				// update all quantified predicate atoms according to the quantified equalities
				// by excluding the corresponding points in their dawgs
				ceql.addExceptions(quantifiedEqualities);
			}
		}
		
		assert literals.size() == mDpllLiterals.size() - quantifiedEqualities.size();
		
		return new Pair<SortedSet<TermVariable>, Set<ClauseLiteral>>(
				variables, literals);

	}
	
	/**
	 * Removes the traces of the clause in the data structures that link to it.
	 * The application I can think of now is a pop command.
	 */
	public void disposeOfClause() {
		assert !mHasBeenDisposed;
		for (ClauseLiteral cl : mLiterals) {
			if (cl instanceof ClauseEprLiteral) {
				ClauseEprLiteral cel = (ClauseEprLiteral) cl;
				cel.getEprPredicate().notifyAboutClauseDisposal(this);
			}
		}
		mEprTheory.getStateManager().getDecideStackManager().removeFromUnitClauseSet(this);
		mHasBeenDisposed = true;
	}

	/**
	 * Update the necessary data structure that help the clause to determine which state it is in.
	 *  --> determineState does not work correctly if this has not been called before.
	 * @param dsl
	 * @param literalsWithSamePredicate
	 * @return
	 */
	public void updateStateWrtDecideStackLiteral(IEprLiteral dsl, 
			Set<ClauseEprLiteral> literalsWithSamePredicate) {
		assert !mHasBeenDisposed;
		
		mClauseStateIsDirty = true;

		// update the storage of each clause literal that contains the decide stack literals
		// the clause literal is affected by
		for (ClauseEprLiteral cel : literalsWithSamePredicate) {
			assert cel.getClause() == this;
			
			if (cel.isDisjointFrom(dsl.getDawg())) {
				continue;
			}
			
			if (cel.getPolarity() == dsl.getPolarity()) {
				cel.addPartiallyFulfillingEprLiteral(dsl);
			} else {
				cel.addPartiallyConflictingEprLiteral(dsl);
			}
		}
	}


	public void backtrackStateWrtDecideStackLiteral(DecideStackLiteral dsl) {
		mClauseStateIsDirty = true;
	}

	/**
	 * This clause is informed that the DPLLEngine has set literal.
	 * The fulfillmentState of this clause may have to be updated because of this.
	 * 
	 * @param literal ground Epr Literal that has been set by DPLLEngine
	 * @return 
	 */
	public EprClauseState updateStateWrtDpllLiteral(Literal literal) {
		assert !mHasBeenDisposed;
		mClauseStateIsDirty = true;
		return determineClauseState(null);
	}

	public void backtrackStateWrtDpllLiteral(Literal literal) {
		assert !mHasBeenDisposed;
		mClauseStateIsDirty = true;
	}
	
	/**
	 * Checks if, in the current decide state, this EprClause is
	 *  a) a conflict clause or
	 *  b) a unit clause
	 * .. on at least one grounding.
	 * 
	 * TODO: this is a rather naive implementation
	 *   --> can we do something similar to two-watchers??
	 *   --> other plan: having a multi-valued dawg that count how many literals are fulfillable
	 *       for each point
	 * 
	 * @return a) something that characterized the conflict (TODO: what excactly?) or 
	 *         b) a unit literal for propagation (may be ground or not)
	 *         null if it is neither
	 */
	private EprClauseState determineClauseState(DecideStackLiteral decideStackBorder) {
		
		// do we have a literal that is fulfilled (on all points)?
		for (ClauseLiteral cl : getLiterals()) {
			if (cl.isFulfilled(decideStackBorder)) {
				setClauseState(decideStackBorder, EprClauseState.Fulfilled);
				return getClauseState(decideStackBorder);
			}
		}
		
		// Although the whole literal is not fulfilled, some points may be..
		// we only need to consider points where no literal is decided "true" yet..
		IDawg<ApplicationTerm, TermVariable> pointsToConsider = 
				mEprTheory.getDawgFactory().createFullDawg(getVariables());
		for (ClauseLiteral cl : getLiterals()) {
			if (cl.isRefuted(decideStackBorder)) {
				continue;
			}

			if (cl instanceof ClauseEprQuantifiedLiteral) {
				IDawg<ApplicationTerm, TermVariable> clFulfilledPoints = 
						((ClauseEprQuantifiedLiteral) cl).getFulfilledPoints();
//				pointsToConsider.removeAll(clFulfilledPoints);
				pointsToConsider = pointsToConsider.difference(clFulfilledPoints);
			}
		}
		assert EprHelpers.verifySortsOfPoints(pointsToConsider, getVariables());
		
		
		/**
		 * The set of all points (over this clause's signature, read: groundings) where no literal of this 
		 * clause is fulfillable
		 *  --> once the computation is complete, this represents the set of groundings that are a conflict.
		 */
		IDawg<ApplicationTerm, TermVariable> pointsWhereNoLiteralsAreFulfillable =
				mDawgFactory.copyDawg(pointsToConsider);
		IDawg<ApplicationTerm, TermVariable> pointsWhereOneLiteralIsFulfillable =
				mDawgFactory.createEmptyDawg(getVariables());
		IDawg<ApplicationTerm, TermVariable> pointsWhereTwoOrMoreLiteralsAreFulfillable =
				mDawgFactory.createEmptyDawg(getVariables());
		assert EprHelpers.haveSameSignature(pointsWhereNoLiteralsAreFulfillable,
				pointsWhereOneLiteralIsFulfillable,
				pointsWhereTwoOrMoreLiteralsAreFulfillable);

		Map<ClauseLiteral, IDawg<ApplicationTerm, TermVariable>> clauseLitToPotentialUnitPoints =
				new HashMap<ClauseLiteral, IDawg<ApplicationTerm,TermVariable>>();
		
		
		for (ClauseLiteral cl : getLiterals()) {
			if (cl.isFulfillable(decideStackBorder)) {
				// at least one point of cl is still undecided (we sorted out fulfilled points before..)
				// we move the newly fulfillable points one up in our hierarchy
				
				IDawg<ApplicationTerm, TermVariable> toMoveFromNoToOne;
				IDawg<ApplicationTerm, TermVariable> toMoveFromOneToTwo;
				if (cl instanceof ClauseEprQuantifiedLiteral) {
					IDawg<ApplicationTerm, TermVariable> fp = 
							((ClauseEprQuantifiedLiteral) cl).getFulfillablePoints(decideStackBorder);

					toMoveFromNoToOne = pointsWhereNoLiteralsAreFulfillable.intersect(fp);
					toMoveFromOneToTwo = pointsWhereOneLiteralIsFulfillable.intersect(fp);
				} else {
					// the dawg of the current cl is the full dawg --> intersecting something with the full dawg means copying the something..
					toMoveFromNoToOne = mDawgFactory.copyDawg(pointsWhereNoLiteralsAreFulfillable);
					toMoveFromOneToTwo = mDawgFactory.copyDawg(pointsWhereOneLiteralIsFulfillable);
				}
				
				assert EprHelpers.haveSameSignature(toMoveFromNoToOne, toMoveFromOneToTwo, pointsWhereNoLiteralsAreFulfillable);

				pointsWhereNoLiteralsAreFulfillable = 
						pointsWhereNoLiteralsAreFulfillable.difference(toMoveFromNoToOne);
				pointsWhereOneLiteralIsFulfillable = 
						pointsWhereOneLiteralIsFulfillable.union(toMoveFromNoToOne);
				pointsWhereOneLiteralIsFulfillable = 
						pointsWhereOneLiteralIsFulfillable.difference(toMoveFromOneToTwo);
				pointsWhereTwoOrMoreLiteralsAreFulfillable = 
						pointsWhereTwoOrMoreLiteralsAreFulfillable.union(toMoveFromOneToTwo);
				
				// if the current ClauseLiteral is the last ClauseLiteral, its unit points are exactly the ones that 
				// moved from noFulfillableLiteral to OneFulfillableLiteral ..
				clauseLitToPotentialUnitPoints.put(cl, mDawgFactory.copyDawg(toMoveFromNoToOne));
				// ... however if we later find out for some of these points, that it is fulfilled somewhere else, we 
				// have to remove it from the list.
				Map<ClauseLiteral, IDawg<ApplicationTerm, TermVariable>> newClauseLitToPotentialUnitPoints = 
						new HashMap<ClauseLiteral, IDawg<ApplicationTerm,TermVariable>>();
				for (Entry<ClauseLiteral, IDawg<ApplicationTerm, TermVariable>> en 
						: clauseLitToPotentialUnitPoints.entrySet()) {				
//					en.getValue().removeAll(toMoveFromOneToTwo);
					newClauseLitToPotentialUnitPoints.put(
							en.getKey(), 
							en.getValue().difference(toMoveFromOneToTwo));
				}
				clauseLitToPotentialUnitPoints = newClauseLitToPotentialUnitPoints;
			} else {
				assert cl.isRefuted(decideStackBorder);
			}
		}
		
		//remove all empty dawgs from clauseLitToPotentialUnitPoints
		Map<ClauseLiteral, IDawg<ApplicationTerm, TermVariable>> finalClauseLitToUnitPoints =
						new HashMap<ClauseLiteral, IDawg<ApplicationTerm,TermVariable>>();
		for (Entry<ClauseLiteral, IDawg<ApplicationTerm, TermVariable>> en : clauseLitToPotentialUnitPoints.entrySet()) {
			if (!en.getValue().isEmpty()) {
				finalClauseLitToUnitPoints.put(en.getKey(), en.getValue());
			}
		}

		assert EprHelpers.verifySortsOfPoints(pointsWhereNoLiteralsAreFulfillable, getVariables());
		assert EprHelpers.verifySortsOfPoints(pointsWhereOneLiteralIsFulfillable, getVariables());
		assert EprHelpers.verifySortsOfPoints(pointsWhereTwoOrMoreLiteralsAreFulfillable, getVariables());


		if (!pointsWhereNoLiteralsAreFulfillable.isEmpty()) {
			setPointsWhereNoLiteralsAreFulfillable(decideStackBorder, pointsWhereNoLiteralsAreFulfillable);
			setClauseState(decideStackBorder, EprClauseState.Conflict);
		} else if (!pointsWhereOneLiteralIsFulfillable.isEmpty()) {
			UnitPropagationData upd = new UnitPropagationData(finalClauseLitToUnitPoints, mDawgFactory);
			setUnitPropagationData(decideStackBorder, upd);
			setClauseState(decideStackBorder, EprClauseState.Unit);
		} else {
			assert pointsWhereTwoOrMoreLiteralsAreFulfillable.supSetEq(pointsToConsider) 
				&& pointsToConsider.supSetEq(pointsWhereTwoOrMoreLiteralsAreFulfillable)
					: "we found no conflict and no unit points, thus all non-fulfilled points must be fulfillable "
					+ "on two or more literals";
			setClauseState(decideStackBorder, EprClauseState.Normal);
		}
		return getClauseState(decideStackBorder);
	}
	
	private void setUnitPropagationData(DecideStackLiteral decideStackBorder, UnitPropagationData upd) {
		if (decideStackBorder == null) {
			mUnitPropagationData = upd;
		} else { 
			mDecideStackBorderToUnitPropagationData.put(decideStackBorder, upd);
		}
	}


	private void setPointsWhereNoLiteralsAreFulfillable(DecideStackLiteral decideStackBorder,
		IDawg<ApplicationTerm, TermVariable> pointsWhereNoLiteralsAreFulfillable) {
		if (decideStackBorder == null) {
			mConflictPoints = pointsWhereNoLiteralsAreFulfillable;
		} else {
			mDecideStackBorderToConflictPoints.put(decideStackBorder, pointsWhereNoLiteralsAreFulfillable);
		}
	}

	private EprClauseState getClauseState(DecideStackLiteral decideStackBorder) {
		if (decideStackBorder == null) {
			return mEprClauseState;
		} else {
			EprClauseState res = mDecideStackBorderToClauseState.get(decideStackBorder);
			assert res != null;
			return res;
		}
	}

	private void setClauseState(DecideStackLiteral decideStackBorder, EprClauseState newState) {
		if (decideStackBorder == null) {
			mClauseStateIsDirty = false;
			mEprClauseState = newState;
		} else {
			mDecideStackBorderToClauseState.put(decideStackBorder, newState);
		}
	}


	public SortedSet<TermVariable> getVariables() {
		return mVariables;
	}
	
	public UnitPropagationData getUnitPropagationData() {
		assert mUnitPropagationData != null;
		UnitPropagationData result = mUnitPropagationData;
		mUnitPropagationData = null;
		return result;
	}
	
	public boolean isUnit() {
		assert !mHasBeenDisposed;
		if (mClauseStateIsDirty) {
			return determineClauseState(null) == EprClauseState.Unit;
		}
		return mEprClauseState == EprClauseState.Unit;
	}

	public boolean isConflict() {
		assert !mHasBeenDisposed;
		if (mClauseStateIsDirty) {
			return determineClauseState(null) == EprClauseState.Conflict;
		}
		return mEprClauseState == EprClauseState.Conflict;
	}

	public IDawg<ApplicationTerm, TermVariable> getConflictPoints() {
		if (mClauseStateIsDirty) {
			determineClauseState(null);
		}
		assert isConflict();
		assert mConflictPoints != null : "this should have been set somewhere..";
		return mConflictPoints;
	}

	public Set<ClauseLiteral> getLiterals() {
		assert !mHasBeenDisposed;
		return mLiterals;
	}
	
	public List<Literal[]> computeAllGroundings(List<TTSubstitution> allInstantiations) {
		ArrayList<Literal[]> result = new ArrayList<Literal[]>();
		for (TTSubstitution sub : allInstantiations) {
			ArrayList<Literal> groundInstList = getSubstitutedLiterals(sub);
			result.add(groundInstList.toArray(new Literal[groundInstList.size()]));
		}
		
		return result;
	}

	public List<Literal[]> computeAllGroundings(HashSet<ApplicationTerm> constants) {
		
		List<TTSubstitution> allInstantiations =  
				EprHelpers.getAllInstantiations(getVariables(), constants);
		
		return computeAllGroundings(allInstantiations);
	}
	
	protected ArrayList<Literal> getSubstitutedLiterals(TTSubstitution sub) {
		assert false : "TODO reimplement";
		return null;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		String comma = "";
		
		for (ClauseLiteral cl : getLiterals()) {
			sb.append(comma);
			sb.append(cl.toString());
			comma = ", ";
		}

		sb.append("}");
		return sb.toString();
	}

	public Set<Clause> getGroundings(IDawg<ApplicationTerm, TermVariable> groundingDawg) {
		assert groundingDawg.getColnames().equals(mVariables) : "signatures don't match";

		Set<Clause> result = new HashSet<Clause>();

		for (List<ApplicationTerm> point : groundingDawg){
			Set<Literal> groundLits = getGroundingForPoint(point).getDomain();
			
			result.add(new Clause(groundLits.toArray(new Literal[groundLits.size()])));
		}

		return result;
	}


	/**
	 * Obtains a grounding of the clause for one point.
	 * The point needs to be in the clause's signature.
	 * Also tracks which instantiation comes from which ClauseLiteral -- which is useful for observing if a factoring has occurred.
	 * @param point
	 * @return
	 */
	private BinaryRelation<Literal, ClauseLiteral> getGroundingForPoint(List<ApplicationTerm> point) {
		TTSubstitution sub = new TTSubstitution(mVariables, point);
		Set<Literal> groundLits = new HashSet<Literal>();
//		Map<ClauseEprQuantifiedLiteral, Literal> quantifiedLitToInstantiation
		BinaryRelation<Literal, ClauseLiteral> instantiationToClauseLiteral = 
				new BinaryRelation<Literal, ClauseLiteral>();
		for (ClauseLiteral cl : getLiterals()) {
			if (cl instanceof ClauseEprQuantifiedLiteral) {
				ClauseEprQuantifiedLiteral ceql = (ClauseEprQuantifiedLiteral) cl;
				Term[] ceqlArgs = ceql.mArgumentTerms.toArray(new Term[ceql.mArgumentTerms.size()]);
				TermTuple newTT = sub.apply(new TermTuple(ceqlArgs));
				assert newTT.getFreeVars().size() == 0;
				EprPredicateAtom at = ceql.getEprPredicate().getAtomForTermTuple(
						newTT, 
						mEprTheory.getTheory(), 
						mEprTheory.getClausifier().getStackLevel());
				
				Literal newLit = cl.getPolarity() ? at : at.negate();

				instantiationToClauseLiteral.addPair(newLit, ceql);
				groundLits.add(newLit);
			} else {
				instantiationToClauseLiteral.addPair(cl.getLiteral(), cl);
				groundLits.add(cl.getLiteral());
			}
		}
		return instantiationToClauseLiteral;
	}


	/**
	 * Checks if in the current decide state a factoring of this conflict clause is possible.
	 * If a factoring is possible, a factored clause is returned.
	 * Otherwise this clause is returned unchanged.
	 * 
	 * @return A factored version of this clause or this clause.
	 */
	public EprClause factorIfPossible() {
		assert this.isConflict();
		
		for (List<ApplicationTerm> cp : getConflictPoints()) {
			BinaryRelation<Literal, ClauseLiteral> cpg = getGroundingForPoint(cp);
			
			for (Literal groundLit : cpg.getDomain()) {
				Set<ClauseLiteral> preGroundingClauseLits = cpg.getImage(groundLit);
				if (preGroundingClauseLits.size() == 1) {
					// no factoring occurred for that literal
					continue;
				}
				assert preGroundingClauseLits.size() > 1;
				/*
				 *  factoring occurred for that literal
				 *  that literal is a conflict grounding of this conflict epr clause
				 *  --> we can factor this conflict epr clause
				 */
				assert preGroundingClauseLits.size() == 2 : "TODO: deal with factoring for more that two literals";
				ClauseEprQuantifiedLiteral ceql = null;
				ClauseEprLiteral cel = null;
				for (ClauseLiteral cl : preGroundingClauseLits) {
					assert cl instanceof ClauseEprLiteral;
					if (ceql == null && cl instanceof ClauseEprQuantifiedLiteral) {
						ceql = (ClauseEprQuantifiedLiteral) cl;
					} else if (cel == null && 
							(ceql != null || !(cl instanceof ClauseEprQuantifiedLiteral))) {
						cel = (ClauseEprLiteral) cl;
					}
				}
				assert cel != null && ceql != null && !cel.equals(ceql);

				
				mEprTheory.getLogger().debug("EPRDEBUG: (EprClause): factoring " + this);
				EprClause factor = mEprTheory.getEprClauseFactory().getFactoredClause(ceql, cel);
				assert factor.isConflict() : "we only factor conflicts -- we should get a conflict out, too.";
				return factor;					
			}
		}

		// when we can't factor, we just return this clause
		return this;
	}

	public boolean isUnitBelowDecisionPoint(DecideStackDecisionLiteral dsdl) {
		EprClauseState state = determineClauseState(dsdl);
		return state == EprClauseState.Unit;
	}

	public Map<ClauseLiteral, IDawg<ApplicationTerm, TermVariable>> getClauseLitToUnitPointsBelowDecisionPoint(
			DecideStackDecisionLiteral dsdl) {
		Map<ClauseLiteral, IDawg<ApplicationTerm, TermVariable>> res = mDecideStackBorderToClauseLitToUnitPoints.get(dsdl);
		assert res != null;
		return res;
	}
}
