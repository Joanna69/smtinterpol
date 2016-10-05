package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.FunctionSymbol;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Theory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.LogProxy;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.Clausifier;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Clause;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.ClauseDeletionHook;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.DPLLAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.DPLLAtom.TrueAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.DPLLEngine;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.ITheory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Literal;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.NamedAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.model.Model;
import de.uni_freiburg.informatik.ultimate.smtinterpol.model.SharedTermEvaluator;
import de.uni_freiburg.informatik.ultimate.smtinterpol.proof.ProofNode;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.cclosure.CCEquality;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.cclosure.CClosure;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.atoms.EprAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.atoms.EprGroundPredicateAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.atoms.EprQuantifiedEqualityAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.atoms.EprQuantifiedPredicateAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.clauses.EprClauseFactory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.DawgFactory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.partialmodel.EprStateManager;
import de.uni_freiburg.informatik.ultimate.util.datastructures.ScopedHashSet;

public class EprTheory implements ITheory {

	Map<FunctionSymbol, EprPredicate> mFunctionSymbolToEprPredicate = new HashMap<FunctionSymbol, EprPredicate>();

	Map<Literal, Clause> mGroundLiteralsToPropagateToReason = 
			new HashMap<Literal, Clause>();

	ScopedHashSet<DPLLAtom> mAtomsAddedToDPLLEngine = new ScopedHashSet<DPLLAtom>();
	
	EqualityManager mEqualityManager;

	/**
	 * If this is set to true, EprTheory just computes all groundings for a given quantified clause
	 * and returns them to the DPLLEngine.
	 */
	private final boolean mGroundAllMode;
	private ArrayList<Literal[]> mAllGroundingsOfLastAddedEprClause;

	private EprStateManager mStateManager;
	private DawgFactory<ApplicationTerm, TermVariable> mDawgFactory;
	private EprClauseFactory mClauseFactory;

	private CClosure mCClosure;
	private Clausifier mClausifier;
	private LogProxy mLogger;
	private Theory mTheory;
	private DPLLEngine mEngine;

	private ArrayDeque<Literal> mGroundDecisionSuggestions = new ArrayDeque<Literal>();

	/**
	 * Used to pass over a conflict that came from adding an input clause over to the next call of
	 * checkpoint()
	 */
	private Clause mStoredConflict;

	/**
	 * A queue for literal propagation.
	 * It is important that literal propagation is done in FIFO-order, because only then it can be guaranteed that
	 * the reason unit clauses are actually unit at the time of propagation and at the time of explanation.
	 *   --> when this was mixed up we ran into a bug because some literal was backtracked and later used in an explanation
	 *      the consequence was that the explanation was not unit anymore.
	 */
	private Deque<Literal> mLiteralsWaitingToBePropagated = new ArrayDeque<Literal>();

	/**
	 * just for debugging purposes
	 */
	private Set<Literal> mAlreadyPropagatedLiterals = new HashSet<Literal>();
	
	/**
	 * just for debugging purposes
	 */
	private Set<Literal> mLiteralsThatAreCurrentlySet = new HashSet<Literal>();


	public EprTheory(Theory th, DPLLEngine engine, CClosure cClosure, Clausifier clausifier, boolean solveThroughGrounding) {
		mTheory = th;
		mEngine = engine;
		mClausifier = clausifier;

		mLogger = clausifier.getLogger();

		mEqualityManager = new EqualityManager();
		mStateManager = new EprStateManager(this);
		mGroundAllMode = solveThroughGrounding;
		
		mDawgFactory = new DawgFactory<ApplicationTerm,TermVariable>(mStateManager.getAllConstants(), this);
		mClauseFactory = new EprClauseFactory(this);
		
		mStateManager.setDawgFactory(mDawgFactory);
		mStateManager.setEprClauseFactory(mClauseFactory);
	}

	@Override
	public Clause startCheck() {
		mLogger.debug("EPRDEBUG: startCheck");
		return null;
	}

	@Override
	public void endCheck() {
		mLogger.debug("EPRDEBUG: endCheck");
	}

	@Override
	public Clause setLiteral(Literal literal) {
		if (mGroundAllMode)
			return null;
		mLogger.debug("EPRDEBUG: setLiteral " + literal);
		mLiteralsThatAreCurrentlySet.add(literal);
		
		DPLLAtom atom = literal.getAtom();
		
		if (atom instanceof EprGroundPredicateAtom) {
			// literal is of the form (P c1 .. cn) (no quantification, but an EprPredicate)
			// is being set by the DPLLEngine (the quantified EprPredicateAtoms are not known to the DPLLEngine)

			Clause conflictOrNull = mStateManager.setEprGroundLiteral(literal);
			assert EprHelpers.verifyConflictClause(conflictOrNull, mLogger);
			return conflictOrNull;
		} else if (atom instanceof EprQuantifiedEqualityAtom 
				|| atom instanceof EprQuantifiedPredicateAtom) {

			assert false : "DPLLEngine is setting a quantified EprAtom --> this cannot be..";

		} else if (atom instanceof CCEquality) {
			assert false : "TODO: check handling of equalities";
			if (literal.getSign() == 1) {
				CCEquality eq = (CCEquality) atom;
				
				Clause conflictOrNull = mStateManager.setGroundEquality((CCEquality) atom);
				assert EprHelpers.verifyConflictClause(conflictOrNull, mLogger);
				return conflictOrNull;
			}

			// TODO do ground disequalities have an impact for EPR?

			Clause conflictOrNull = mStateManager.setDpllLiteral(literal);
			assert EprHelpers.verifyConflictClause(conflictOrNull, mLogger);
			return conflictOrNull;
		} else {
			// neither an EprAtom nor an Equality

			Clause conflictOrNull = mStateManager.setDpllLiteral(literal);
			assert EprHelpers.verifyConflictClause(conflictOrNull, mLogger);
			return conflictOrNull;
		}
		return null;
	}

	@Override
	public void backtrackLiteral(Literal literal) {
		if (mGroundAllMode) {
			return;
		}
		mLogger.debug("EPRDEBUG: backtrackLiteral " + literal);

		boolean	success = mLiteralsThatAreCurrentlySet.remove(literal);
		assert success;
		
		unregisterPropagatedLiteral(literal);

		// .. dual to setLiteral
		
		// update the fulfillment states of the remaining clauses
		DPLLAtom atom = literal.getAtom();
		if (atom instanceof EprGroundPredicateAtom) {
			// literal is of the form (P x1 .. xn)

			mStateManager.unsetEprGroundLiteral(literal);

		} else if (atom instanceof EprQuantifiedEqualityAtom
				|| atom instanceof EprQuantifiedPredicateAtom) {

			assert false : "DPLLEngine is unsetting a quantified EprAtom --> this cannot be..";

		} else if (atom instanceof CCEquality) {
			assert atom.getSign() == literal.getSign() : "TODO: treat backtracking of disequality";
			mStateManager.unsetGroundEquality((CCEquality) atom);
			
		} else {
			// neither an EprAtom nor an equality

			mStateManager.unsetDpllLiteral(literal);

		}
	}

	/**
	 * This has to be called, when a literal that was propagated to the dpllengine is backtracked.
	 * That means that we don't need its explanation unit clause anymore and that it may be set freshly for some other reason later.
	 * @param literal
	 */
	private void unregisterPropagatedLiteral(Literal literal) {
		assert !mLiteralsWaitingToBePropagated.contains(literal) : ".. right?..";
		mGroundLiteralsToPropagateToReason.remove(literal);
		
		Set<Literal> literalsRemovedBecauseLiteralWasInReason = new HashSet<Literal>();
		
		Map<Literal, Clause> newGltoptr = new HashMap<Literal, Clause>();
		for (Entry<Literal, Clause> en : mGroundLiteralsToPropagateToReason.entrySet()) {
			if (en.getValue().contains(literal.negate())) {
				// propagation is no more possible because backtracking made the reason clause non-unit.
				mLiteralsWaitingToBePropagated.remove(en.getKey());
				literalsRemovedBecauseLiteralWasInReason.add(en.getKey());
				continue;
			}
			newGltoptr.put(en.getKey(), en.getValue());
		}
		mGroundLiteralsToPropagateToReason = newGltoptr;
		
		// the literals we removed need to be unregistered, too (they may themselves contribute
		// to a (former) reason unit clause not being unit anymore..) 
		//  deeper reason: propagations may base on other propagations
		for (Literal rl : literalsRemovedBecauseLiteralWasInReason) {
			unregisterPropagatedLiteral(rl);
		}
	}

	@Override
	public Clause checkpoint() {
		if (mGroundAllMode)
			return null;
		mLogger.debug("EPRDEBUG: checkpoint");

		assert mLiteralsWaitingToBePropagated.isEmpty() : "have all propagations been done at this point??";

		if (mStoredConflict != null) {
			Clause conflict = mStoredConflict;
			mStoredConflict = null;
			assert EprHelpers.verifyConflictClause(conflict, mLogger);
			return conflict;
		}
		
		// tell the state manager to do propagations, and return a conflict if one appears
		Clause conflict = mStateManager.doPropagations();
		if (conflict != null) {
			if (! mLiteralsWaitingToBePropagated.isEmpty()) {
				//TODO what do we do with that conflict?..
				// (it may only be a conflict to the DPLLEngine after those literals have been propagated)
				// for now, we just ignore it -- we will find it again.. or another one..
				//  --> maybe need to understand the rules better how getPropagatedLiterals() and checkpoint() are called..
				return null;
			}
			assert EprHelpers.verifyConflictClause(conflict, mLogger);
			return conflict;
		}
		
		return null;
	}

	@Override
	public Clause computeConflictClause() {
		if (mGroundAllMode)
			return null;
		mLogger.debug("EPRDEBUG: computeConflictClause");
		
		Clause conflict = mStateManager.eprDpllLoop();
		assert EprHelpers.verifyConflictClause(conflict, mLogger);
		return conflict;
	}

	@Override
	public Literal getPropagatedLiteral() {
		Literal	lit = mLiteralsWaitingToBePropagated.poll();
		
		if (lit == null) {
			mLogger.debug("EPRDEBUG: getPropagatedLiteral -- nothing to propagate");
			return null;
		}

		Clause reasonUnitClause = mGroundLiteralsToPropagateToReason.get(lit);
		assert EprHelpers.verifyUnitClauseBeforePropagation(reasonUnitClause, lit, mLogger);

		mAlreadyPropagatedLiterals.add(lit);
		
		mLogger.debug("EPRDEBUG: getPropagatedLiteral propagating: " + lit);
		return lit;
	}
	
	public void addGroundLiteralToPropagate(Literal l, Clause reason) {
		if (mGroundLiteralsToPropagateToReason.keySet().contains(l)) {
			mLogger.debug("EPRDEBUG: EprTheory.addGroundLiteralToPropagate: already added: " + l);
			return;
		}
		
		// the atom may be new for the dpll engine -- if it is the grounding of a quantified epr atom
		if (l.getAtom() instanceof EprAtom) {
			addAtomToDPLLEngine(l.getAtom());
		}
		
		mLogger.debug("EPRDEBUG: EprTheory.addGroundLiteralToPropagate(..): "
				+ "literal: " + l + " reason: " + reason);
		
		assert EprHelpers.verifyUnitClauseAtEnqueue(l, reason, mLiteralsWaitingToBePropagated, mLogger);

		mLiteralsWaitingToBePropagated.add(l);
		mGroundLiteralsToPropagateToReason.put(l, reason);
	}

	@Override
	public Clause getUnitClause(Literal literal) {
		Clause unitClause = mGroundLiteralsToPropagateToReason.get(literal);
		mLogger.debug("EPRDEBUG: getUnitClause -- returning " + unitClause);
		assert unitClause != null;
		assert EprHelpers.verifyUnitClauseAfterPropagation(unitClause, literal, mLogger);
		// remove the entry from the map -- seems cleaner..
		return unitClause;
	}

	@Override
	public Literal getSuggestion() {
		if (mGroundAllMode)
			return null;
		Literal sug = mGroundDecisionSuggestions.poll();
		if (sug == null) {
			mLogger.debug("EPRDEBUG: (EprTheory): getSuggestion -- no literal to suggest");
		} else {
			mLogger.debug("EPRDEBUG: (EprTheory): getSuggestion, suggesting " + sug);
		}
		return sug;
	}
	
	public void addGroundDecisionSuggestion(Literal l) {
		mGroundDecisionSuggestions.add(l);
	}

	@Override
	public void increasedDecideLevel(int currentDecideLevel) {
		if (mGroundAllMode)
			return;
		// TODO Auto-generated method stub
		mLogger.debug("EPRDEBUG: increasedDecideLevel");

	}

	@Override
	public void decreasedDecideLevel(int currentDecideLevel) {
		if (mGroundAllMode)
			return;
		// TODO Auto-generated method stub
		mLogger.debug("EPRDEBUG: decreasedDecideLevel");

	}

	@Override
	public Clause backtrackComplete() {
		// TODO Auto-generated method stub
		mLogger.info("EPRDEBUG: backtrackComplete");
		return null;
	}

	@Override
	public void restart(int iteration) {
		// TODO Auto-generated method stub
		mLogger.info("EPRDEBUG: restart");

	}

	@Override
	public void removeAtom(DPLLAtom atom) {
		if (mGroundAllMode)
			return;
		// TODO Auto-generated method stub
		mLogger.debug("EPRDEBUG: removeAtom" + atom);
	}

	@Override
	public Object push() {
		mLogger.debug("EPRDEBUG: (EprTheory) PUSH");
		mStateManager.push();
		mAtomsAddedToDPLLEngine.beginScope();
		return null;
	}

	@Override
	public void pop(Object object, int targetlevel) {
		mLogger.debug("EPRDEBUG: (EprTheory) POP");
		for (int i = mClausifier.getStackLevel(); i > targetlevel; i--) {
			mStateManager.pop();
		}
		mAtomsAddedToDPLLEngine.endScope();
	}

	@Override
	public Object[] getStatistics() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void fillInModel(Model model, Theory t, SharedTermEvaluator ste) {
		// TODO Auto-generated method stub

	}
	public void addAtomToDPLLEngine(DPLLAtom atom) {
		assert !(atom instanceof EprQuantifiedEqualityAtom || atom instanceof EprQuantifiedPredicateAtom);
		if (atom instanceof CCEquality)
			return; //added to engine at creation, right?..
		if (!mAtomsAddedToDPLLEngine.contains(atom)) { //TODO not so nice, with the extra set..
			mEngine.addAtom(atom);
			mAtomsAddedToDPLLEngine.add(atom);
		}
	}

	/**
	 * Add an EprClause for a given a non-ground set of literals.
	 * 
	 * Specialty: apply destructive equality reasoning (DER)
	 *  If the clause becomes ground through DER, don't add it as an EprClause, but return the corresponding literals
	 *   instead (in order to be added as a DPLL clause).
	 *  Otherwise return null
	 * 
	 * @param lits literals where at least one variable is free (thus implicitly forall-quantified)
	 * @param hook
	 * @param proof
	 * @return equivalent ground set of literals if DER obtained one, null otherwise
	 */
	public Literal[] addEprClause(Literal[] lits, ClauseDeletionHook hook, ProofNode proof) {
		//TODO: do something about hook and proof..
		
		// we need to track all constants for grounding mode (and other applications??)
		this.addConstants(EprHelpers.collectAppearingConstants(lits, mTheory));

		
		// we remove disequalities occuring in the clause through destructive equality reasoning
		// if the clause is ground afterwards, we just give it back to, the DPLLEngine
		// otherwise we add it as an EprClause
		ApplyDestructiveEqualityReasoning ader = new ApplyDestructiveEqualityReasoning(lits);
		if (ader.isResultGround()) {
			return ader.getResult().toArray(new Literal[ader.getResult().size()]);
		} 
		HashSet<Literal> literals = ader.getResult();
		
		// a new clause may immediately be a conflict clause, and possibly that
		// conflict cannot be resolved in the EprTheory 
		// --> we will return that conflict at the next checkpoint
		Clause groundConflict = mStateManager.getEprClauseManager().createEprClause(literals);
		if (groundConflict != null) {
			assert mStoredConflict == null : "we'll probably need a queue for this..";
			mStoredConflict = groundConflict;
		}
		
		return null;
	}

	/**
	 * A term is an EPR atom, if it is
	 *  - an atom
	 *  - contains free Termvariables (i.e. implicitly quantified variables)
	 *  - an ApplicationTerm with function symbol either "=" or an uninterpreted predicate
	 *  further checks:
	 *  - may not contain function symbols
	 */
	public static boolean isQuantifiedEprAtom(Term idx) {
		if (idx.getFreeVars().length > 0) {
			if (idx instanceof ApplicationTerm) {
				if (isEprPredicate(((ApplicationTerm) idx).getFunction())) 
					return true;
				if ((((ApplicationTerm) idx).getFunction()).getName().equals("=")
						&& !(((ApplicationTerm) idx).getParameters()[0].getSort().getName().equals("Bool")))
					return true;
			}
		}
		return false;
	}

	private static boolean isEprPredicate(FunctionSymbol function) {
		if (function.getName().equals("not")) 
			return false;
		if (function.getName().equals("or")) 
			return false;
		if (function.getName().equals("and")) 
			return false;
		if (function.getName().equals("let")) 
			return false;
		if (function.getName().equals("ite")) 
			return false;
		if (function.getName().equals("=")) 
			return false;
		return true;
	}

	public EprAtom getEprAtom(ApplicationTerm idx, int hash, int assertionStackLevel) {
		if (idx.getFunction().getName().equals("=")) {
			assert idx.getFreeVars().length > 0;
//		    ApplicationTerm subTerm = applyAlphaRenaming(idx, mCollector);
			return new EprQuantifiedEqualityAtom(idx, hash, assertionStackLevel);
		} else {

			EprPredicate pred = getEprPredicate(idx.getFunction());

			if (idx.getFreeVars().length == 0) {
				EprGroundPredicateAtom egpa = 
						(EprGroundPredicateAtom) pred.getAtomForTermTuple(new TermTuple(idx.getParameters()), 
								mTheory, 
								assertionStackLevel);
				pred.addDPLLAtom(egpa);
				return egpa;
			} else {
//				ApplicationTerm substitutedTerm = applyAlphaRenaming(idx, mCollector);
				return pred.getAtomForTermTuple(
						new TermTuple(idx.getParameters()), 
//						new TermTuple(substitutedTerm.getParameters()), 
						mTheory, 
						assertionStackLevel);
			}
		}
	}

	private EprPredicate getEprPredicate(FunctionSymbol fs) {
		EprPredicate pred = mFunctionSymbolToEprPredicate.get(fs);
		if (pred == null) {
			pred = new EprPredicate(fs, this);
			mFunctionSymbolToEprPredicate.put(fs, pred);
			mStateManager.addNewEprPredicate(pred);
		}
		return pred;
	}

	public void notifyAboutNewClause(Object buildClause) {
		// TODO: probably remove
//		mBuildClauseToAlphaRenamingSub.put(buildClause, new HashMap<TermVariable, Term>());
	}
	
	/**
	 * Used for tracking all constants that appear in any clause that is currently asserted.
	 * @param constants
	 */
	public void addConstants(HashSet<ApplicationTerm> constants) {
		mStateManager.addConstants(constants);
	}

	public ArrayList<Literal[]> getAllGroundingsOfLastAddedEprClause() {
		return mAllGroundingsOfLastAddedEprClause;
	}

	public Theory getTheory() {
		return mTheory;
	}
	
	public CClosure getCClosure() {
		return mCClosure;
	}
	
	public EprStateManager getStateManager() {
		return mStateManager;
	}
	
	public DawgFactory<ApplicationTerm, TermVariable> getDawgFactory() {
		return mDawgFactory;
	}
	
	public EprClauseFactory getEprClauseFactory() {
		return mClauseFactory;
	}
	
	public EqualityManager getEqualityManager() {
		return mEqualityManager;
	}
	
	public Clausifier getClausifier() {
		return mClausifier;
	}
	
	public boolean isGroundAllMode() {
		return mGroundAllMode;
	}

	/**
	 * This is called whenever the Clausifier introduces a new constant term.
	 * (The only case I can think of now is at skolemization..,
	 *  but if we want constants handling on-the-fly, we may use this elsewhere, too..)
	 *  --> in groundAll-mode adding a constant means adding further instantiations of the 
	 *    EprClauses
	 * @param skolems
	 * @return 
	 */
	public void addSkolemConstants(Term[] skolems) {

		HashSet<ApplicationTerm> constants = new HashSet<ApplicationTerm>();
		for (Term t : skolems)
			constants.add((ApplicationTerm) t);
		
		mStateManager.addConstants(constants);
	}

	public LogProxy getLogger() {
		return mLogger;
	}

	/**
	 * Apply destructive equality reasoning to the clause consisting of the given
	 * literals.
	 * Procedure:
	 *  - build one big substitution which has one entry for each equality
	 *  - apply the subtitution to each (quantified) literal in the clause
	 *   (it may be a bit suprising that this works, but I think it does,
	 *    example: {x != c, x != d, P(x)} will yield the substitution [x <- c, x <- d], which
	 *           will yield the clause {c != c, c != d, P(c)} which seems right.) //TODO: make sure..
	 */
	class ApplyDestructiveEqualityReasoning {

		HashSet<Literal> mResult;
		boolean mIsResultGround = true;

		public ApplyDestructiveEqualityReasoning(Literal[] literals) {
			applyDER(new HashSet<Literal>(Arrays.asList(literals)));
		}

		private void applyDER(HashSet<Literal> literals) {
			HashSet<Literal> currentClause = new HashSet<Literal>(literals);
			Literal disEquality = findDisequality(currentClause);
			mResult = currentClause;
			mIsResultGround = false;
			while (disEquality != null) {
				currentClause.remove(disEquality);

				TTSubstitution sub = extractSubstitutionFromEquality((EprQuantifiedEqualityAtom) disEquality.getAtom());			

				mResult = new HashSet<Literal>();
				mIsResultGround = true;
				for (Literal l : currentClause) {
					Literal sl = EprHelpers.applySubstitution(sub, l, EprTheory.this, true);
					if (sl.getAtom() instanceof TrueAtom) {
						if (sl.getSign() == 1) {
							// do nothing/just add it to the result (tautology will be detected later)
						} else {
							continue; //omit "false"
						}
					} else if (sl.getAtom() instanceof EprQuantifiedEqualityAtom ||
							sl.getAtom() instanceof EprQuantifiedPredicateAtom) {
						mIsResultGround = false;
					} else if (sl.getAtom() instanceof EprGroundPredicateAtom ||
							sl.getAtom() instanceof CCEquality) {
						addAtomToDPLLEngine(sl.getAtom());
					} else if (sl.getAtom() instanceof NamedAtom) {
						// do nothing/just add it to the result
					} else
						assert false : "case not forseen..";
					mResult.add(sl);
				}
				currentClause = mResult;

				disEquality = findDisequality(currentClause);
			}
		}

		public TTSubstitution extractSubstitutionFromEquality(EprQuantifiedEqualityAtom eea) {
			TermTuple tt = eea.getArgumentsAsTermTuple();
			TermVariable tv = null;
			Term t = null;
			if (tt.terms[0] instanceof TermVariable) {
				tv = (TermVariable) tt.terms[0];
				t = tt.terms[1];
			} else {
				tv = (TermVariable) tt.terms[1];
				t = tt.terms[0];
			}
			return new TTSubstitution(tv, t);
		}

		private Literal findDisequality(HashSet<Literal> literals) {
			for (Literal l : literals) {
				if (l.getSign() != 1 && l.getAtom() instanceof EprQuantifiedEqualityAtom)
					return l;
			}
			return null;
		}

		/**
		 * Applies sub to li and adds the resulting Literal to newLits.
		 * Also updates mIsResultGround (i.e. when a Literal remains non-ground, it is set to false)
		 * @param sub substitution to be applied
		 * @param newLits set to add to
		 * @param li literal whose variables should be substituted
		 */
		public Literal getSubstitutedLiteral(TTSubstitution sub, Literal li) {
			if (li.getAtom() instanceof EprQuantifiedPredicateAtom 
					|| li.getAtom() instanceof EprQuantifiedEqualityAtom) {
				boolean liPositive = li.getSign() == 1;
				TermTuple liTT = ((EprAtom) li.getAtom()).getArgumentsAsTermTuple();

				TermTuple newTT = sub.apply(liTT);

				if (newTT.equals(liTT)) {
					return li;
				}

				if (li.getAtom() instanceof EprQuantifiedEqualityAtom) {
					if (newTT.isGround()) {
						if (newTT.terms[0] == newTT.terms[1] && liPositive) {
							return new DPLLAtom.TrueAtom();
						} else if (newTT.terms[0] == newTT.terms[1] && !liPositive) {
							return new DPLLAtom.TrueAtom().negate();
						}
						throw new UnsupportedOperationException();// how to obtain a fresh CCEquality???
					} else {
						EprQuantifiedEqualityAtom eea = new EprQuantifiedEqualityAtom(mTheory.term("=", newTT.terms),
								0,  //TODO use good hash
								li.getAtom().getAssertionStackLevel());
						return liPositive ? eea : eea.negate();
					}
				} else {
					EprPredicate liPred = ((EprQuantifiedPredicateAtom) li.getAtom()).getEprPredicate();

					EprAtom ea = null;
					if (newTT.isGround()) {
						ea = liPred.getAtomForTermTuple(newTT, mTheory, getClausifier().getStackLevel());
					} else {
						ea = liPred.getAtomForTermTuple(newTT, mTheory, getClausifier().getStackLevel());
					}
					return liPositive ? ea : ea.negate();
				}
			} else {
				return li;
			}
		}

		public HashSet<Literal> getResult() {
			return mResult;
		}

		public boolean isResultGround() {
			return mIsResultGround;
		}
	}
	

	@Override
	public void printStatistics(LogProxy logger) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void dumpModel(LogProxy logger) {
		// TODO Auto-generated method stub
		
	}
}
