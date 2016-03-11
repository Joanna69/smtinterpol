package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.FunctionSymbol;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.Theory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.Clausifier;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Clause;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.ClauseDeletionHook;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.DPLLAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.DPLLEngine;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.ITheory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Literal;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.NamedAtom;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.SimpleList;
import de.uni_freiburg.informatik.ultimate.smtinterpol.model.Model;
import de.uni_freiburg.informatik.ultimate.smtinterpol.model.SharedTermEvaluator;
import de.uni_freiburg.informatik.ultimate.smtinterpol.proof.ProofNode;

public class EprTheory implements ITheory {


//	SimpleList<Clause> mEprClauses = new SimpleList<>(); //cant have a SimpleList instersecting with others..
	

	//we keep updated lists of EprClauses that are fulfilled in the current decision state
	// typically an EprClause would be fulfilled when one of its non-quantified-Epr-literals is set to "true" 
	// through setLiteral
	// TODO: probably should do something more efficient

	//TODO: replace arraylist, simplelist made problems..
	ArrayList<Clause> mFulfilledEprClauses = new ArrayList<>();
	ArrayList<Clause> mNotFulfilledEprClauses = new ArrayList<>();
	
//	SimpleList<Clause> mFulfilledEprClauses = new SimpleList<>();
//	SimpleList<Clause> mNotFulfilledEprClauses = new SimpleList<>();
	
	HashMap<FunctionSymbol, EprPredicate> mEprPredicates = new HashMap<>();

	private Theory mTheory;
	private DPLLEngine mEngine;

//	private Term mAlmostAllConstant;
//	
	public EprTheory(Theory th, DPLLEngine engine) {
		mTheory = th;
		mEngine = engine;
//		mAlmostAllConstant = th.term("@0");
	}

	@Override
	public Clause startCheck() {
//		throw new UnsupportedOperationException();
		// TODO Auto-generated method stub
		System.out.println("EPRDEBUG: startCheck");
		return null;
	}

	@Override
	public void endCheck() {
		// TODO Auto-generated method stub

//		throw new UnsupportedOperationException();
		System.out.println("EPRDEBUG: endCheck");
	}

	@Override
	public Clause setLiteral(Literal literal) {
		System.out.println("EPRDEBUG: setLiteral " + literal);

		// does this literal occur in any of the EprClauses?
		// --> then check for satisfiability
		//   return a conflict clause in case
		//   possibly do (or cache) propagations
		// --> otherwise update the predicate model (held in the corresponding EprPredicate) accordingly

		DPLLAtom atom = literal.getAtom();
		
		if (atom instanceof EprPredicateAtom) {
			// literal is of the form (P c1 .. cn) (no quantification, but an EprPredicate)
			// it being set by the DPLLEngine (the quantified EprPredicateAtoms are not known to the DPLLEngine)
			assert atom.getSMTFormula(mTheory).getFreeVars().length == 0 : 
				"An atom that contains quantified variables should not be known to the DPLLEngine.";
			
			//update model
			// no conflict can arise from setting a point, reasons:
			//  - if that point was set by the opposite literal, the DPLLEngine sees the conflict
			//  - a point is never in conflict to an almost-all atom
			//   -- if both say "false", then there is no problem anyway
			//   -- otherwise, "almost-all" comes into play 
			//         (alternatively, one could store the exceptions in the AlmostAllAtoms 
			//				--> not sure what that would mean.., this way, the final check has to check the exceptions)
			EprPredicate eprPred = ((EprPredicateAtom) atom).eprPredicate;
//			if (literal.getSign() == 1) eprPred.setPointPositive(new TermTuple(((EprPredicateAtom) atom).getArguments()));
//			else 						eprPred.setPointNegative(new TermTuple(((EprPredicateAtom) atom).getArguments()));
			if (literal.getSign() == 1) eprPred.setPointPositive((EprPredicateAtom) atom);
			else 						eprPred.setPointNegative((EprPredicateAtom) atom);

//			boolean success;
//			if (literal.getSign() == 1) success = eprPred.setPointPositive(new TermTuple(((EprPredicateAtom) atom).getArguments()));
//			else 						success = eprPred.setPointNegative(new TermTuple(((EprPredicateAtom) atom).getArguments()));
			//return a unit clause saying that the point is already set negatively
			// question: can this occur at all?? when?
//			if (!success)
//				
			
			// 
			
			markEprClausesFulfilled(literal);

			return null;
//		} else if (atom instanceof EprAlmostAllAtom) {
//			// setting an "almost all" auxilliary propositional variable
//			// sth like <P v1 ... vn>
//			// --> notify the corresponding EprPredicate (P)
//			// --> a concflict may occur for instance if for example (assume P is binary)
//			//     <P v1 v1> is already set in the model and we are setting (not <P v1 v2>)
//			//     then we return the conflict clause {<P v1 v2>, (not <P v1 v1>)}
//			EprPredicate eprPred = ((EprAlmostAllAtom) atom).eprPredicate;
//
//			Clause conflict;
//			if (literal.getSign() == 1) conflict = eprPred.setAlmostAllAtomPositive((EprAlmostAllAtom) atom, this);
//			else 						conflict = eprPred.setAlmostAllAtomNegative((EprAlmostAllAtom) atom, this);
//
//			System.out.println("EPRDEBUG: setLiteral --> almost-all atom conflict clause: " + conflict);
//
//			return conflict;
		} else if (atom instanceof EprEqualityAtom) {
			//this should not happen because an EprEqualityAtom always has at least one
			// quantified variable, thus the DPLLEngine should not know about that atom
			assert false : "DPLLEngine is setting a quantified EprAtom --> this cannot be..";
			return null;
		} else {
			// not an EprAtom 
			// --> check if it occurs in one of the EPR-clauses
			//      if it fulfills the clause, mark the clause as fulfilled
			//      otherwise do nothing, because the literal means nothing to EPR 
			//          --> other theories may report their own conflicts..?
			// (like standard DPLL)
			markEprClausesFulfilled(literal);

			System.out.println("EPRDEBUG: setLiteral, new fulfilled clauses: " + mFulfilledEprClauses);
			System.out.println("EPRDEBUG: setLiteral, new not fulfilled clauses: " + mNotFulfilledEprClauses);

			return null;
		}
	}

	/**
	 * Changes the status of all EPR clauses from not fulfilled to fulfilled that contain
	 * the given literal. (To be called by setLiteral)
	 * @param literal
	 */
	private void markEprClausesFulfilled(Literal literal) {
		ArrayList<Clause> toRemove = new ArrayList<>();
		for (Clause c : mNotFulfilledEprClauses) {
			if (c.contains(literal)) {
				//TODO: when switching back to SimpleList some time: this code seems dubious, and there were some problems where the iterator returned a SimpleList --> connected??
//					c.removeFromList();
//					mFulfilledEprClauses.append(c);
				toRemove.add(c);
				mFulfilledEprClauses.add(c);
			}
		}
		mNotFulfilledEprClauses.removeAll(toRemove);
	}

	@Override
	public void backtrackLiteral(Literal literal) {
		System.out.println("EPRDEBUG: backtrackLiteral");

		// .. dual to setLiteral

		DPLLAtom atom = literal.getAtom();
		
		if (atom instanceof EprPredicateAtom) {
			// literal is of the form (P x1 .. xn)
			
			//update model
			EprPredicate eprPred = ((EprPredicateAtom) atom).eprPredicate;
			if (literal.getSign() == 1) eprPred.unSetPointPositive((EprPredicateAtom) atom);
			else 						eprPred.unSetPointNegative((EprPredicateAtom) atom);
			
			// update (non)fulfilled clauses
			markEprClausesNotFulfilled(literal);
			return;
//		} else if (atom instanceof EprAlmostAllAtom) {
//			EprPredicate eprPred = ((EprAlmostAllAtom) atom).eprPredicate;
//
//			if (literal.getSign() == 1) eprPred.unSetAlmostAllAtomPositive((EprAlmostAllAtom) atom);
//			else 						eprPred.unSetAlmostAllAtomNegative((EprAlmostAllAtom) atom);
//
//			return;
		} else if (atom instanceof EprEqualityAtom) {
			assert false : "DPLLEngine is unsetting a quantified EprAtom --> this cannot be..";
			return;
		} else {
			markEprClausesNotFulfilled(literal);

			System.out.println("EPRDEBUG: backtrackLiteral, new fulfilled clauses: " + mFulfilledEprClauses);
			System.out.println("EPRDEBUG: backtrackLiteral, new not fulfilled clauses: " + mNotFulfilledEprClauses);

			return;
		}
	}

	/**
	 * Changes the status of all EPR clauses from fulfilled to not fulfilled that contain
	 * the given literal. (To be called by backtrackLiteral)
	 * @param literal
	 */
	private void markEprClausesNotFulfilled(Literal literal) {
		ArrayList<Clause> toRemove = new ArrayList<>();
		for (Clause c : mFulfilledEprClauses) {
			//check if this was the only literal that made the clause fulfilled
			// if that is the case, mark it unfulfilled
			if (c.contains(literal)) {
				
				boolean stillfulfilled = false;
				for (int i = 0; i < c.getSize(); i++) {
					Literal l  = c.getLiteral(i);
					if (l == literal)
						continue;
					boolean isSet = l == l.getAtom().getDecideStatus();
					stillfulfilled |= isSet;
				}
				if (!stillfulfilled) {
//						c.removeFromList();
//						mNotFulfilledEprClauses.append(c);
					toRemove.add(c);
					mNotFulfilledEprClauses.add(c);
				}
			}
		}
		mFulfilledEprClauses.removeAll(toRemove);
	}

	@Override
	public Clause checkpoint() {
		System.out.println("EPRDEBUG: checkpoint");
		
		//plan:
		// for each epr clause c
		//  if c is already fulfilled: skip
		//  else:
		//   collect the the positive EprEqualityAtoms 
		//             (there are no negative ones, because destructive equality reasoning eliminated them) 
		//   for each EprPredicateAtom epa:
		//    look up if in the current state (setLiteral stuff) there is a contradiction with epa
		//   if all epa had a contradiction:
		//    return the conflicting points as conflict clause
		// if no clause yielded a conflict:
		//  return null
		

//		boolean checkResult = true;
		
		for (Clause c : mNotFulfilledEprClauses) {
			EprClause eprClause = (EprClause)	c;
			
			// an epr clause looks like this:
			// x1 =/!= x2 \/ ... \/ xn+1 = c1 ... \/ (P1 ci/xi ... cj/xj) \/ ... \/ (non-EPR literals)
			// we have
			// - (dis)equalities over two quantified variables
			// - equalities over a quantified variable and a constant each
			// - predicates over quantified variables and/or constants
			// - non-epr literals (in mNotFulfilledEprClauses, they are all false (maybe unset??))
			Clause conflict = eprClause.check();
//			checkResult &= conflict == null;
			if (conflict != null)
				return conflict;
		}
		
		return null;
	}

	@Override
	public Clause computeConflictClause() {
//		throw new UnsupportedOperationException();
		System.out.println("EPRDEBUG: computeConflictClause");
		for (Clause c : mNotFulfilledEprClauses) {
			EprClause eprClause = (EprClause)	c;
			
			// an epr clause looks like this:
			// x1 =/!= x2 \/ ... \/ xn+1 = c1 ... \/ (P1 ci/xi ... cj/xj) \/ ... \/ (non-EPR literals)
			// we have
			// - (dis)equalities over two quantified variables
			// - equalities over a quantified variable and a constant each
			// - predicates over quantified variables and/or constants
			// - non-epr literals (in mNotFulfilledEprClauses, they are all false (maybe unset??))
			Clause conflict = eprClause.check();
//			checkResult &= conflict == null;
			if (conflict != null)
				return conflict;
		}
		return null;
	}

	@Override
	public Literal getPropagatedLiteral() {
		
		// unit propagation for EPR clauses ?
//		for (Clause c : mNotFulfilledEprClauses) {
//			
//		}
		
		// pure literal propagation for EPR clauses?

		System.out.println("EPRDEBUG: getPropagatedLiteral");
		return null;
	}

	@Override
	public Clause getUnitClause(Literal literal) {
		// TODO Auto-generated method stub
//		return null;
//		throw new UnsupportedOperationException();
		System.out.println("EPRDEBUG: getUnitClause");
		return null;
	}

	@Override
	public Literal getSuggestion() {
		// TODO Auto-generated method stub
//		return null;
//		throw new UnsupportedOperationException();
		System.out.println("EPRDEBUG: getSuggestion");
		return null;
	}

	@Override
	public void printStatistics(Logger logger) {
		// TODO Auto-generated method stub
//		throw new UnsupportedOperationException();
		System.out.println("EPRDEBUG: printStatistics");
	}

	@Override
	public void dumpModel(Logger logger) {
		// TODO Auto-generated method stub
		System.out.println("EPRDEBUG: dumpmodel");

	}

	@Override
	public void increasedDecideLevel(int currentDecideLevel) {
		// TODO Auto-generated method stub
		System.out.println("EPRDEBUG: increasedDecideLevel");

	}

	@Override
	public void decreasedDecideLevel(int currentDecideLevel) {
		// TODO Auto-generated method stub
		System.out.println("EPRDEBUG: decreasedDecideLevel");

	}

	@Override
	public Clause backtrackComplete() {
		// TODO Auto-generated method stub
		System.out.println("EPRDEBUG: backtrackComplete");
		return null;
	}

	@Override
	public void restart(int iteration) {
		// TODO Auto-generated method stub
		System.out.println("EPRDEBUG: restart");

	}

	@Override
	public void removeAtom(DPLLAtom atom) {
		// TODO Auto-generated method stub
		System.out.println("EPRDEBUG: removeAtom" + atom);

	}

	@Override
	public Object push() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void pop(Object object, int targetlevel) {
		// TODO Auto-generated method stub

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
	/**
	 * Given some literals where at least one variable is free (thus implicitly forall-quantified), 
	 * inserts the clause into the eprTheory,
	 * and returns the corresponding almost-all clause which is to be added in the DPLLEngine
	 * @param lits
	 * @param hook
	 * @param proof
	 * @return
	 */
	public void addEprClause(Literal[] lits, ClauseDeletionHook hook, ProofNode proof) {
		//TODO: do something about hook and proof..
		EprClause eprClause = new EprClause(lits, mTheory);
		mNotFulfilledEprClauses.add(eprClause);
	}

//	/**
//	 * Given some literals where at least one variable is free (thus implicitly forall-quantified), 
//	 * inserts the clause into the eprTheory,
//	 * and returns the corresponding almost-all clause which is to be added in the DPLLEngine
//	 * @param lits
//	 * @param hook
//	 * @param proof
//	 * @return
//	 */
//	public Literal[] createEprClause(Literal[] lits, ClauseDeletionHook hook, ProofNode proof) {
//		//TODO: do something about hook and proof..
//
//		EprClause eprClause = new EprClause(lits);
//		
//		int noAlmostAllLiterals = lits.length;
//		
//		ArrayList<EprEqualityAtom> equalities = new ArrayList<>();
//
//		for (Literal l : lits) {
//			if (l.getAtom() instanceof EprPredicateAtom
//					&& ((EprPredicateAtom) l.getAtom()).isQuantified) {
//				// Have the EprPredicates point to the clauses and literals they occur in.
//				EprPredicate pred = ((EprPredicateAtom) l.getAtom()).eprPredicate;
//				pred.addQuantifiedOccurence(l, eprClause);
//			}
//			
//			if (l.getAtom() instanceof EprEqualityAtom) {
//				noAlmostAllLiterals--;
//				equalities.add((EprEqualityAtom) l.getAtom());
//			}
//		}
//		
////		mNotFulfilledEprClauses.append(eprClause);
//		mNotFulfilledEprClauses.add(eprClause);
//		
//		//compute the "almost-all-clause", which will be inserted into the DPLL-engine
//		// - quantified equalities are left out
//		// - quantified predicates are converted to EprAlmostAllAtoms
//		// - everything else is added to the clause as is
//		Literal[] almostallClause = new Literal[noAlmostAllLiterals];
//		for (Literal l : lits) {
//			if (l.getAtom() instanceof EprEqualityAtom)
//				continue;
//
//			if (l.getAtom() instanceof EprPredicateAtom 
//					&& ((EprPredicateAtom) l.getAtom()).isQuantified) {
//				EprPredicateAtom eprPred = (EprPredicateAtom) l.getAtom();
//					
//				EprAlmostAllAtom eaaa = getEprAlmostAllAtom(
//								l.getAtom().getAssertionStackLevel(), 
//								eprPred.eprPredicate, 
//								eprPred.getArguments(),
//								equalities,
//								l.getSign() == -1);
//
//				almostallClause[--noAlmostAllLiterals] = l.getSign() == 1 ? eaaa : eaaa.negate();
//
//				continue;
//			}
//			
//			almostallClause[--noAlmostAllLiterals] = l;
//		}
//
//		return almostallClause;
//	}
	
//	/**
//	 * Compute a the almost-all signature from an ApplicationTerm.
//	 * (Basically this means discovering which arguments repeat, and how.)
//	 * @param arguments repetitions here are used to compute the signature
//	 * @return
//	 */
//	private AAAtomSignature computeSignature(Term[] arguments, ArrayList<EprEqualityAtom> equations, boolean negated) {
//		HashMap<Term, HashSet<Integer>> argToOccurences = new HashMap<>();
//		for (int i = 0; i < arguments.length; i++) {
//			Term t = arguments[i];
//			HashSet<Integer> occ = argToOccurences.get(t);
//			if (occ == null) {
//				occ = new HashSet<>();
//				argToOccurences.put(t, occ);
//			}
//			occ.add(i);
//		}
//		ArrayList<HashSet<Integer>> repetitionSig = new ArrayList<HashSet<Integer>>(arguments.length);
//		for (int i = 0; i < arguments.length; i++) {
//			repetitionSig.add(argToOccurences.get(arguments[i]));
//		}
//		
//		ArrayList<HashSet<Integer>> nonReflSig = new ArrayList<>();
//		for (EprEqualityAtom eea : equations) {
//			if (eea.areBothQuantified()) {
//				HashSet<Integer> eq = new HashSet<>();
//				eq.addAll(argToOccurences.get(eea.getLhs()));
//				eq.addAll(argToOccurences.get(eea.getRhs()));
//				nonReflSig.add(eq);
//			}
//		}
//
//		return new AAAtomSignature(repetitionSig, nonReflSig, negated);
//	}
//	
//	HashMap<EprPredicate, HashMap<AAAtomSignature, EprAlmostAllAtom>> mAlmostAllAtomsStore = new HashMap<>();

//	/**
//	 * Looks up a fitting atom in the store, makes a new one if there is none.
//	 * @param assertionStackLevel
//	 * @param eprPredicate
//	 * @param argumentsForSignatureComputation
//	 * @return
//	 */
//	private EprAlmostAllAtom getEprAlmostAllAtom(int assertionStackLevel, 
//			EprPredicate eprPredicate, 
//			Term[] argumentsForSignatureComputation, 
//			ArrayList<EprEqualityAtom> equalities, 
//			boolean negated) {
//		//TODO: maybe replace the AlmostAllAtomsStore by a HashSet??
//		AAAtomSignature signature = computeSignature(argumentsForSignatureComputation, equalities, negated);
//		
//		return getEprAlmostAllAtom(assertionStackLevel, eprPredicate, signature);
//	}
//
//	EprAlmostAllAtom getEprAlmostAllAtom(int assertionStackLevel, EprPredicate eprPredicate,
//			AAAtomSignature signature) {
//		HashMap<AAAtomSignature, EprAlmostAllAtom> itm = mAlmostAllAtomsStore.get(eprPredicate);
//		if (itm == null) {
//			itm = new HashMap<>();
//			mAlmostAllAtomsStore.put(eprPredicate, itm);
//		}
//		EprAlmostAllAtom eaaa = itm.get(signature);
//		if (eaaa == null) {
//			//TODO: good hash value
//			Term t = mTheory.constant("<" + eprPredicate.functionSymbol.getName() + signature.toString() + ">", mTheory.getBooleanSort());
//			eaaa = new EprAlmostAllAtom(t, 0, assertionStackLevel, eprPredicate, signature);
//			mEngine.addAtom(eaaa);
//			itm.put(signature, eaaa);
//		} 
//		return eaaa;
//	}


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
				if (isEprPredicate(((ApplicationTerm) idx).getFunction())) return true;
				if ((((ApplicationTerm) idx).getFunction()).getName().equals("=")) return true;
			}
		}
		return false;
	}

	private static boolean isEprPredicate(FunctionSymbol function) {
		//TODO: we should collect all the predicates that are managed by EPR --> implement a check accordingly, here
		if (function.getName().equals("not")) return false;
		if (function.getName().equals("or")) return false;
		return true;
	}
	
	public List<Clause> getFulfilledClauses() {
		return mFulfilledEprClauses;
	}

	public List<Clause> getNotFulfilledClauses() {
		return mNotFulfilledEprClauses;
	}

	public EprAtom getEprAtom(ApplicationTerm idx, int hash, int assertionStackLevel) {
		if (idx.getFunction().getName().equals("=")) {
			return new EprEqualityAtom(idx, hash, assertionStackLevel);
		} else {
			EprPredicate pred = mEprPredicates.get(idx.getFunction());
			if (pred == null) {
				pred = new EprPredicate(idx.getFunction(), idx.getParameters().length);
				mEprPredicates.put(idx.getFunction(), pred);
			}
			return new EprPredicateAtom(idx, hash, assertionStackLevel, pred);
		}
	}

	
//	public Term getAlmostAllConstant() {
//		return mAlmostAllConstant;
//	}
}
