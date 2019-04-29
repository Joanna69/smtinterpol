/*
 * Copyright (C) 2019 University of Freiburg
 *
 * This file is part of SMTInterpol.
 *
 * SMTInterpol is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMTInterpol is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with SMTInterpol.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.quant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.FunctionSymbol;
import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.Clausifier;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.EqualityProxy;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.SMTAffineTerm;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.SharedTerm;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Clause;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Literal;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.cclosure.CCTerm;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.clauses.EprClauseState;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.linar.InfinitesimalNumber;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.linar.MutableAffineTerm;

/**
 * This class takes care of clause, literal and term instantiation.
 *
 * Literals and clauses are only created if necessary, in particular, false literals as well as clauses that would
 * contain true literals are not created.
 *
 * @author Tanja Schindler
 *
 */
public class InstantiationManager {

	private final Clausifier mClausifier;
	private final QuantifierTheory mQuantTheory;

	public InstantiationManager(Clausifier clausifier, QuantifierTheory quantTheory) {
		mClausifier = clausifier;
		mQuantTheory = quantTheory;
	}

	/**
	 * Find all instances of clauses that would be a conflict or unit clause if the corresponding theories had known the
	 * literals at creation of the instance.
	 *
	 * @return A Set of potentially conflicting and unit instances.
	 */
	public Set<List<Literal>> findConflictAndUnitInstances() {
		final Set<List<Literal>> conflictAndUnitClauses = new LinkedHashSet<>();
		for (QuantClause quantClause : mQuantTheory.getQuantClauses()) {
			if (quantClause.getState() == EprClauseState.Fulfilled) {
				continue;
			}
			final Set<List<SharedTerm>> allInstantiations = computeAllInstantiations(quantClause);
			for (List<SharedTerm> inst : allInstantiations) {
				final InstanceValue clauseValue = evaluateClauseInstance(quantClause, inst);
				if (clauseValue != InstanceValue.TRUE) {
					final List<Term> termSubs = new ArrayList<>();
					for (int i = 0; i < inst.size(); i++) {
						termSubs.add(inst.get(i).getTerm());
					}
					// TODO: Should this create an InstClause?
					final List<Literal> instLits = computeClauseInstance(quantClause, termSubs);
					if (instLits != null) {
						conflictAndUnitClauses.add(instLits);
						// quantClause.addInstance(inst);
					}
				}
			}
		}
		return conflictAndUnitClauses;
	}

	/**
	 * In the final check, check if all interesting instantiations of all clauses lead to satisfied instances. As soon
	 * as an instance is found that is not yet satisfied, stop. The newly created literals will be decided by the ground
	 * theories next and may lead to new conflicts.
	 * 
	 * If an actual conflict is found, return it (TODO This should not happen, checkpoint should have found it).
	 * 
	 * @return an actual conflict clause, if it exists; null otherwise.
	 */
	public Clause instantiateAll() {
		for (QuantClause quantClause : mQuantTheory.getQuantClauses()) {
			if (quantClause.getState() == EprClauseState.Fulfilled) {
				continue;
			}
			final Set<List<SharedTerm>> allInstantiations = computeAllInstantiations(quantClause);

			outer: for (List<SharedTerm> inst : allInstantiations) {
				final List<Term> termSubs = new ArrayList<>();
				for (int i = 0; i < inst.size(); i++) {
					termSubs.add(inst.get(i).getTerm());
				}
				// TODO Check that the instance wouldn't be true before creating literals
				// evaluateClauseInstance doesn't work for this, atm.
				final List<Literal> instLits = computeClauseInstance(quantClause, termSubs);
				if (instLits != null) {
					boolean isConflict = true;
					for (Literal lit : instLits) {
						if (lit.getAtom().getDecideStatus() == lit) { // instance satisfied
							continue outer;
						}
						if (lit.getAtom().getDecideStatus() == null) {
							isConflict = false;
						}
					}
					if (isConflict) {
						// quantClause.addInstance(inst);
						return new Clause(instLits.toArray(new Literal[instLits.size()]));
					} else { // a new not yet satisfied instance has been created
						return null;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Compute all instantiations for a given clause.
	 *
	 * @return a Set containing interesting instantiations for the clause.
	 */
	private Set<List<SharedTerm>> computeAllInstantiations(QuantClause quantClause) {
		Set<List<SharedTerm>> allSubs = new LinkedHashSet<List<SharedTerm>>();
		allSubs.add(new ArrayList<SharedTerm>());
		for (int i = 0; i < quantClause.getVars().length; i++) {
			Set<List<SharedTerm>> partialSubs = new LinkedHashSet<List<SharedTerm>>();
			for (final List<SharedTerm> oldSub : allSubs) {
				if (quantClause.getInterestingTerms()[i].isEmpty()) {
					// TODO Use lambda
				} else {
					for (final SharedTerm ground : quantClause.getInterestingTerms()[i].values()) {
						List<SharedTerm> newSub = new ArrayList<SharedTerm>(oldSub);
						newSub.add(ground);
						partialSubs.add(newSub);
					}
				}
			}
			allSubs.clear();
			allSubs.addAll(partialSubs);
		}
		// allSubs.removeAll(quantClause.getInstantiations());
		return allSubs;
	}

	/**
	 * Evaluate which value a potential instance of a given clause would have. We distinguish three values: FALSE if all
	 * literals would be false, ONE_UNDEF if all but one literal would be false and this one undefined, and TRUE for all
	 * other cases.
	 *
	 * @param quantClause
	 *            the quantified clause which we evaluate an instance for.
	 * @param instantiation
	 *            the ground terms to instantiate.
	 * @return the value of the potential instance.
	 */
	private InstanceValue evaluateClauseInstance(final QuantClause quantClause, final List<SharedTerm> instantiation) {
		InstanceValue clauseValue = InstanceValue.FALSE;

		// Check ground literals first.
		for (Literal groundLit : quantClause.getGroundLits()) {
			if (groundLit.getAtom().getDecideStatus() == groundLit) {
				assert quantClause.getState() == EprClauseState.Fulfilled;
				return InstanceValue.TRUE;
			} else if (groundLit.getAtom().getDecideStatus() == null) {
				clauseValue.combine(InstanceValue.ONE_UNDEF);
			} else {
				assert groundLit.getAtom().getDecideStatus() != groundLit;
			}
		}
		// Only check clauses where all or all but one ground literals are set.
		// TODO Or only where all ground lits are set?
		if (clauseValue == InstanceValue.TRUE) {
			return clauseValue;
		}

		// Check quantified literals. TODO Where do we check for trivially true or false literals?
		for (QuantLiteral quantLit : quantClause.getQuantLits()) {
			InstanceValue litValue = InstanceValue.ONE_UNDEF;
			final boolean isNeg = quantLit.isNegated();
			final QuantLiteral atom = quantLit.getAtom();
			if (atom instanceof QuantVarEquality) {
				// TODO Should we first check both sides for QuantAffineTerms?
				final QuantVarEquality varEq = (QuantVarEquality) atom;
				assert !varEq.isBothVar(); // x!=y was used for DER, x=y is not supported
				final SharedTerm left =
						instantiation.get(quantClause.getVarPos(varEq.getLeftVar()));
				final SharedTerm right = varEq.getGroundTerm().getSharedTerm();
				litValue = evaluateEquality(left, right);
			} else if (atom instanceof QuantEUEquality) {
				// TODO Should we first check for QuantAffineTerms?
				final QuantEUEquality euEq = (QuantEUEquality) atom;
				final SharedTerm left =
						findEquivalentShared(euEq.getLhs(), Arrays.asList(quantClause.getVars()), instantiation);
				final SharedTerm right =
						findEquivalentShared(euEq.getRhs(), Arrays.asList(quantClause.getVars()), instantiation);
				if (left == null || right == null) {
					litValue = InstanceValue.ONE_UNDEF;
				} else {
					litValue = evaluateEquality(left, right);
				}
			} else if (atom instanceof QuantVarConstraint) {
				final QuantVarConstraint varCons = (QuantVarConstraint) atom;
				final SharedTerm lower = (varCons.isBothVar() || !varCons.isLowerBound()) ? instantiation.get(quantClause.getVarPos(varCons.getLowerVar()))
								: varCons.getGroundBound().getSharedTerm();
				final SharedTerm upper = (varCons.isBothVar() || varCons.isLowerBound())
						? instantiation.get(quantClause.getVarPos(varCons.getUpperVar()))
								: varCons.getGroundBound().getSharedTerm();
				final SMTAffineTerm smtAff = new SMTAffineTerm(lower.getTerm());
				smtAff.add(Rational.MONE, upper.getTerm());
				litValue = evaluateBoundConstraint(smtAff);
			} else {
				assert atom instanceof QuantEUBoundConstraint;
				final QuantEUBoundConstraint euBoundConstr = (QuantEUBoundConstraint) atom;
				// final SharedTerm shared = findEquivalentShared(((QuantEUBoundConstraint) atom).getAffineTerm());
				final List<Term> termSubs = new ArrayList<>();
				for (int i = 0; i < instantiation.size(); i++) {
					termSubs.add(instantiation.get(i).getTerm());
				}
				final SMTAffineTerm smtAff = instantiateEUTerm(euBoundConstr.getAffineTerm(),
						Arrays.asList(quantClause.getVars()), termSubs);
				litValue = evaluateBoundConstraint(smtAff);
			}

			if (isNeg) {
				litValue = litValue.negate();
			}
			clauseValue = clauseValue.combine(litValue);
			if (clauseValue == InstanceValue.TRUE) {
				return InstanceValue.TRUE;
			}
		}
		return clauseValue;
	}

	/**
	 * Instantiate a clause with a given substitution. Note that partial instantiation is not supported.
	 *
	 * @param clause
	 *            a clause containing at least one quantified literal
	 * @param substitution
	 *            pairs of variable and ground term that is used to instantiate the corresponding variable.
	 *
	 * @return the set of ground literals, or null if the clause would be true.
	 */
	private List<Literal> computeClauseInstance(final QuantClause clause, final List<Term> instantiation) {
		// Compute proxies for literal instances
		final ArrayList<Term> litProxies = new ArrayList<Term>();
		for (int i = 0; i < clause.getQuantLits().length; i++) {
			final boolean isNeg = clause.getQuantLits()[i].isNegated();
			final QuantLiteral atom = clause.getQuantLits()[i].getAtom();
			Term prox = computeLitInstanceAsTerm(atom, instantiation, clause);
			if (!isNeg && prox.equals(mClausifier.getTheory().mTrue)
					|| isNeg && prox.equals(mClausifier.getTheory().mFalse)) {
				return null; // Don't instantiate the clause if a literal would be trivially true.
			} else if (!isNeg && prox.equals(mClausifier.getTheory().mFalse)
					|| isNeg && prox.equals(mClausifier.getTheory().mTrue)) {
				// Don't instantiate a literal that would be trivially false.
			} else {
				if (isNeg) {
					prox = mClausifier.getTheory().not(prox);
				}
				if (litProxies.contains(mClausifier.getTheory().not(prox))) {
					return null; // Don't instantiate literals that would lead to tautologies.
				}
				if (!litProxies.contains(prox)) {
					litProxies.add(prox);
				}
			}
		}
		// None of the literal proxys is trivially true, so build the clause.
		final int instLength = litProxies.size();
		List<Literal> instClause = new ArrayList<Literal>(Arrays.asList(clause.getGroundLits()));
		for (int i = 0; i < instLength; i++) {
			final Term litProxy = litProxies.get(i);
			assert litProxy instanceof ApplicationTerm;
			ApplicationTerm atomProxy = (ApplicationTerm) litProxy;
			boolean isNeg = false;
			if (atomProxy.getFunction().getName().equals("not")) {
				assert atomProxy.getParameters()[0] instanceof ApplicationTerm;
				atomProxy = (ApplicationTerm) atomProxy.getParameters()[0];
				isNeg = true;
			}
			Literal inst;
			if (atomProxy.getFunction().getName().equals("=")) {
				final SharedTerm left = mClausifier.getSharedTerm(atomProxy.getParameters()[0], clause.getSource());
				final SharedTerm right = mClausifier.getSharedTerm(atomProxy.getParameters()[1], clause.getSource());
				final EqualityProxy eq = left.createEquality(right);
				inst = eq.getLiteral(clause.getSource());
			} else {
				assert atomProxy.getFunction().getName().equals("<=") || atomProxy.getFunction().getName().equals("<"); // TODO Only <=?
				boolean strict = false;
				if (atomProxy.getFunction().getName().equals("<")) {
					strict = true;
				}
				final SMTAffineTerm sum = new SMTAffineTerm(atomProxy.getParameters()[0]);
				final MutableAffineTerm at = mClausifier.createMutableAffinTerm(sum, clause.getSource());
				inst = mQuantTheory.mLinArSolve.generateConstraint(at, strict);
			}
			if (isNeg) {
				inst = inst.negate();
			}
			if (!instClause.contains(inst)) {
				instClause.add(inst);
			}
		}
		return instClause;
	}

	/**
	 * Build a proxy instance for a QuantLiteral and a given instantiation.
	 *
	 * @param atom
	 *            the underlying atom of a QuantLiteral
	 * @return a term as proxy for the literal instance
	 */
	private Term computeLitInstanceAsTerm(QuantLiteral atom, List<Term> instantiation,
			QuantClause clause) {
		assert !atom.isNegated();
		final Term litProxy;
		if (atom instanceof QuantEUEquality) {
			final QuantEUEquality eUeq = (QuantEUEquality) atom;
			final Term left = instantiateEUTerm(eUeq.getLhs(), Arrays.asList(clause.getVars()), instantiation);
			final Term right = instantiateEUTerm(eUeq.getRhs(), Arrays.asList(clause.getVars()), instantiation);
			litProxy = computeEqualityLitAsTerm(left, right);
		} else if (atom instanceof QuantVarEquality) {
			final QuantVarEquality varEq = (QuantVarEquality) atom;
			assert !varEq.isBothVar();
			final Term left = instantiation.get(clause.getVarPos(varEq.getLeftVar()));
			final Term right = varEq.getGroundTerm().getTerm();
			litProxy = computeEqualityLitAsTerm(left, right);
		} else if (atom instanceof QuantEUBoundConstraint) {
			final QuantEUBoundConstraint qBoundConstr = (QuantEUBoundConstraint) atom;
			final SMTAffineTerm smtAff =
					instantiateEUTerm(qBoundConstr.getAffineTerm(), Arrays.asList(clause.getVars()), instantiation);
			final Sort sort = qBoundConstr.getAffineTerm().getSort();
			litProxy = computeBoundConstraintLitAsTerm(smtAff, false, sort);
		} else {
			assert atom instanceof QuantVarConstraint;
			final QuantVarConstraint varCons = (QuantVarConstraint) atom;
			final Term lower =
					(varCons.isBothVar() || !varCons.isLowerBound())
							? instantiation.get(clause.getVarPos(varCons.getLowerVar()))
							: varCons.getGroundBound().getTerm();
			final Term upper =
					(varCons.isBothVar() || varCons.isLowerBound())
							? instantiation.get(clause.getVarPos(varCons.getUpperVar()))
							: varCons.getGroundBound().getTerm();
			final SMTAffineTerm smtAff = SMTAffineTerm.create(lower);
			smtAff.add(Rational.MONE, upper);
			final Sort sort = lower.getSort();
			litProxy = computeBoundConstraintLitAsTerm(smtAff, false, sort);
		}
		assert litProxy != null;
		return litProxy;
	}

	/**
	 * Create a proxy instance for an equality literal.
	 *
	 * This method simplifies trivially true or false instances.
	 *
	 * @return an equality term.
	 */
	private Term computeEqualityLitAsTerm(final Term left, final Term right) {
		final SMTAffineTerm diff = new SMTAffineTerm(left);
		diff.negate();
		diff.add(new SMTAffineTerm(right));
		if (diff.isConstant()) {
			if (diff.getConstant().equals(Rational.ZERO)) {
				return mClausifier.getTheory().mTrue;
			} else {
				return mClausifier.getTheory().mFalse;
			}
		}
		diff.div(diff.getGcd());
		Sort sort = left.getSort();
		// Normalize equality to integer logic if all variables are integer.
		if (mClausifier.getTheory().getLogic().isIRA() && sort.getName().equals("Real") && diff.isAllIntSummands()) {
			sort = mClausifier.getTheory().getSort("Int");
		}
		// Check for unsatisfiable integer formula, e.g. 2x + 2y = 1.
		if (sort.getName().equals("Int") && !diff.getConstant().isIntegral()) {
			return mClausifier.getTheory().mFalse;
		}
		// TODO Do we need to normalize more? (see Clausifier.createEqualityProxy(...))
		return mClausifier.getTheory().term("=", left, right);
	}

	/**
	 * Create a proxy instance for an inequality literal.
	 *
	 * This method simplifies trivially true or false instances.
	 *
	 * @return an equality term.
	 */
	private Term computeBoundConstraintLitAsTerm(SMTAffineTerm smtAff, boolean isStrict, Sort sort) {
		if (smtAff.isConstant()) {
			if (isStrict && smtAff.getConstant().isNegative()
					|| !isStrict && !smtAff.getConstant().negate().isNegative()) {
				return mClausifier.getTheory().mTrue;
			} else {
				return mClausifier.getTheory().mFalse;
			}
		}
		if (isStrict) {
			return mClausifier.getTheory().term("<", smtAff.toTerm(mClausifier.getTermCompiler(), sort),
					Rational.ZERO.toTerm(sort));
		} else {
			return mClausifier.getTheory().term("<=", smtAff.toTerm(mClausifier.getTermCompiler(), sort),
					Rational.ZERO.toTerm(sort));
		}
	}

	/**
	 * Compute an instance of a given EUTerm and a given substitution. This should return a normalized term.
	 */
	private Term instantiateEUTerm(final EUTerm euTerm, final List<TermVariable> vars,
			final List<Term> termSubst) {
		if (euTerm instanceof GroundTerm) {
			return ((GroundTerm) euTerm).getTerm();
		} else if (euTerm instanceof QuantAppTerm) {
			final QuantAppTerm euApp = (QuantAppTerm) euTerm;
			final QuantAppArg[] quantArgs = euApp.getArgs();
			final Term[] args = new Term[quantArgs.length];
			for (int i = 0; i < args.length; i++) {
				if (quantArgs[i].isVar()) {
					args[i] = termSubst.get(vars.indexOf(quantArgs[i].getVar()));
				} else {
					args[i] = instantiateEUTerm(quantArgs[i].getEUTerm(), vars, termSubst);
				}
			}
			return mClausifier.getTheory().term(euApp.getFunc(), args);
		} else {
			assert euTerm instanceof QuantAffineTerm;
			final QuantAffineTerm euAffine = (QuantAffineTerm) euTerm;
			final SMTAffineTerm instAff = instantiateEUTerm(euAffine, vars, termSubst);
			return instAff.toTerm(mClausifier.getTermCompiler(), euTerm.getTerm().getSort());
		}
	}

	/**
	 * Compute an instance of a given QuantAffineTerm and a given substitution.
	 *
	 * @return an SMTAffineTerm for this instance.
	 */
	private SMTAffineTerm instantiateEUTerm(final QuantAffineTerm euAffine, final List<TermVariable> vars,
			final List<Term> termSubst) {
		final Map<Term, Rational> summands = new LinkedHashMap<Term, Rational>();
		for (final EUTerm euSummand : euAffine.getSummands().keySet()) {
			final Term instSummand = instantiateEUTerm(euSummand, vars, termSubst);
			Rational factor = euAffine.getSummands().get(euSummand);
			if (summands.containsKey(instSummand)) {
				factor = factor.add(summands.get(instSummand));
				summands.remove(instSummand);
			}
			if (factor == Rational.ZERO) {
				summands.remove(instSummand);
			} else {
				summands.put(instSummand, factor);
			}
		}
		return new SMTAffineTerm(summands, euAffine.getConstant());
	}

	/**
	 * For a given EUTerm and an instantiation, find an equivalent SharedTerm.
	 *
	 * @param euTerm
	 *            The EUTerm which we search an equivalent SharedTerm for.
	 * @param vars
	 *            The variables in the clause of this EUTerm.
	 * @param instantiation
	 *            The ground terms that should be instantiated for the variables.
	 * @return a SharedTerm equivalent to the instance of the given EUTerm if it exists, null otherwise.
	 */
	private SharedTerm findEquivalentShared(final EUTerm euTerm, final List<TermVariable> vars,
			final List<SharedTerm> instantiation) {
		if (euTerm instanceof GroundTerm) {
			return ((GroundTerm) euTerm).getSharedTerm();
		} else if (euTerm instanceof QuantAppTerm) {
			final QuantAppTerm euApp = (QuantAppTerm) euTerm;
			final FunctionSymbol func = euApp.getFunc();
			final QuantAppArg[] euArgs = euApp.getArgs();
			final Term[] instArgs = new Term[euArgs.length];
			for (int i = 0; i < euArgs.length; i++) {
				if (euArgs[i].isVar()) {
					instArgs[i] = instantiation.get(vars.indexOf(euArgs[i].getVar())).getTerm();
				} else {
					final SharedTerm sharedArg = findEquivalentShared(euArgs[i].getEUTerm(), vars, instantiation);
					if (sharedArg == null) {
						return null;
					}
					instArgs[i] = sharedArg.getTerm();
				}
			}
			final Term instAppTerm = mClausifier.getTheory().term(func, instArgs);
			final CCTerm ccTermRep = mQuantTheory.getCClosure().getCCTermRep(instAppTerm);
			if (ccTermRep != null) {
				if (ccTermRep.getSharedTerm() != null) {
					return ccTermRep.getSharedTerm();
				} else {
					return ccTermRep.getFlatTerm();
				}
			} else {
				return null;
			}
		} else {
			assert euTerm instanceof QuantAffineTerm;
			return findEquivalentShared((QuantAffineTerm) euTerm);
		}
	}

	private SharedTerm findEquivalentShared(final QuantAffineTerm quantAff) {
		// TODO
		return null;
	}

	/**
	 * Determine the value that an equality literal between two given terms would have.
	 *
	 * TODO Should this do the simplification for trivially false/true literals?
	 *
	 * @param left
	 *            The left side of the equality.
	 * @param right
	 *            The right side of the equality.
	 * @return Value True if the two terms are in the same congruence class, False if they are definitely distinct,
	 *         Undef else.
	 */
	private InstanceValue evaluateEquality(final SharedTerm left, final SharedTerm right) {
		final CCTerm leftCC = left.getCCTerm();
		final CCTerm rightCC = right.getCCTerm();
		if (leftCC != null && rightCC != null) {
			if (mQuantTheory.getCClosure().isEqSet(leftCC, rightCC)) {
				return InstanceValue.TRUE;
			} else if (mQuantTheory.getCClosure().isDiseqSet(leftCC, rightCC)) {
				return InstanceValue.FALSE;
			}
		} else {
			final SMTAffineTerm smtAff = new SMTAffineTerm(left.getTerm());
			smtAff.add(Rational.MONE, right.getTerm());
			final InfinitesimalNumber upperBound = mQuantTheory.mLinArSolve.getUpperBound(mClausifier, smtAff);
			smtAff.negate();
			final InfinitesimalNumber lowerBound = mQuantTheory.mLinArSolve.getUpperBound(mClausifier, smtAff);
			if (upperBound.lesseq(InfinitesimalNumber.ZERO) && lowerBound.lesseq(InfinitesimalNumber.ZERO)) {
				return InstanceValue.TRUE;
			} else if (!upperBound.isInfinity() && !upperBound.lesseq(InfinitesimalNumber.ZERO)
					|| !lowerBound.isInfinity() && !lowerBound.lesseq(InfinitesimalNumber.ZERO)) {
				return InstanceValue.FALSE;
			}
		}
		return InstanceValue.ONE_UNDEF;
	}

	/**
	 * Determine the value that a bound constraint "term <= 0" would have.
	 *
	 * TODO Should this do the simplification for trivially false/true literals?
	 *
	 * TODO Is it sufficient to have ONE_UNDEF?
	 *
	 * @param affine
	 *            The linear term for a constraint "term <= 0".
	 * @return Value True if the term has an upper bound <= 0, False if -term has a lower bound < 0, or Undef otherwise.
	 */
	private InstanceValue evaluateBoundConstraint(final SMTAffineTerm affine) {
		final InfinitesimalNumber upperBound = mQuantTheory.mLinArSolve.getUpperBound(mClausifier, affine);
		if (upperBound.lesseq(InfinitesimalNumber.ZERO)) {
			return InstanceValue.TRUE;
		} else {
			affine.negate();
			final InfinitesimalNumber lowerBound = mQuantTheory.mLinArSolve.getUpperBound(mClausifier, affine);
			if (lowerBound.less(InfinitesimalNumber.ZERO)) {
				return InstanceValue.FALSE;
			} else {
				return InstanceValue.ONE_UNDEF;
			}
		}
	}

	private enum InstanceValue {
		TRUE, FALSE, ONE_UNDEF;

		private InstanceValue combine(InstanceValue other) {
			if (this == InstanceValue.TRUE) {
				return this;
			} else if (this == InstanceValue.FALSE) {
				return other;
			} else {
				if (other == InstanceValue.FALSE) {
					return this;
				} else { // Note: UNDEF + UNDEF = TRUE
					return InstanceValue.TRUE;
				}
			}
		}

		private InstanceValue negate() {
			if (this == TRUE) {
				return InstanceValue.FALSE;
			} else if (this == FALSE) {
				return InstanceValue.TRUE;
			} else {
				return InstanceValue.ONE_UNDEF;
			}
		}
	}
}
