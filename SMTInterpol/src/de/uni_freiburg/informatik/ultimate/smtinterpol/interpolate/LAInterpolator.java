/*
 * Copyright (C) 2009-2019 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.smtinterpol.interpolate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import de.uni_freiburg.informatik.ultimate.logic.AnnotatedTerm;
import de.uni_freiburg.informatik.ultimate.logic.Annotation;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.ConstantTerm;
import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.logic.Theory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.SMTAffineTerm;
import de.uni_freiburg.informatik.ultimate.smtinterpol.interpolate.Interpolator.LitInfo;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.linar.InfinitesimalNumber;

/**
 * The Interpolator for linear arithmetic. This computes the interpolants with the algorithm described in "Proof Tree
 * Preserving Interpolation" in the version "newtechreport.pdf" in this repository. In particular we need to compute
 * leaf interpolants for trichotomy
 *
 * <pre>
 * a < b \/ a == b \/ a > b
 * </pre>
 *
 * and for simple conflicts with Farkas coefficients.
 *
 * @author Jochen Hoenicke, Alexander Nutz, Tanja Schindler
 */
public class LAInterpolator {
	public final static String ANNOT_LA = ":LA";

	Interpolator mInterpolator;

	/**
	 * Create a new linear arithmetic interpolator for an LA lemma.
	 *
	 * @param interpolator
	 *            the global interpolator.
	 * @param laLemma
	 *            the lemma that is interpolated.
	 */
	public LAInterpolator(final Interpolator interpolator) {
		mInterpolator = interpolator;
	}

	/**
	 * Create an {@code LA(s,k,F)} term. This is now represented as an annotated term {@code (! F :LA (s k))}
	 *
	 * @param s
	 *            The affine term {@code s} with {@code s >= 0 ==> F}.
	 * @param k
	 *            The interval length {@code k} with {@code s + k < 0 ==> ~F}.
	 * @param formula
	 *            The formula F.
	 */
	public static Term createLATerm(final InterpolatorAffineTerm s, final InfinitesimalNumber k, final Term formula) {
		final Theory theory = formula.getTheory();
		return theory.annotatedTerm(new Annotation[] { new Annotation(ANNOT_LA, new Object[] { s, k }) }, formula);
	}

	/**
	 * Check if a term is an {@code LA(s,k,F)} term.
	 *
	 * @param term
	 *            The term to check.
	 * @return true if it is an LA term, false otherwise.
	 */
	public static boolean isLATerm(final Term term) {
		if (term instanceof AnnotatedTerm) {
			final Annotation[] annot = ((AnnotatedTerm) term).getAnnotations();
			return annot.length == 1 && annot[0].getKey().equals(ANNOT_LA);
		}
		return false;
	}

	/**
	 * Get the s part of an {@code LA(s,k,F)} term. This assumes the term is an LA term.
	 *
	 * @param term
	 *            The LA term.
	 * @return the s part.
	 */
	public static InterpolatorAffineTerm getS(final Term term) {
		assert isLATerm(term);
		return (InterpolatorAffineTerm) ((Object[]) ((AnnotatedTerm) term).getAnnotations()[0].getValue())[0];
	}

	/**
	 * Get the k part of an {@code LA(s,k,F)} term. This assumes the term is an LA term.
	 *
	 * @param term
	 *            The LA term.
	 * @return the k part.
	 */
	public static InfinitesimalNumber getK(final Term term) {
		assert isLATerm(term);
		return (InfinitesimalNumber) ((Object[]) ((AnnotatedTerm) term).getAnnotations()[0].getValue())[1];
	}

	/**
	 * Compute the literals and corresponding Farkas coefficients for this LA lemma
	 */
	private HashMap<Term, Rational> getFarkasCoeffs(final InterpolatorClauseTermInfo clauseInfo) {
		final HashMap<Term, Rational> coeffMap = new HashMap<Term, Rational>();
		Term term;
		Rational coeff;
		final Term[] lits = clauseInfo.getLiterals();
		final Object[] coeffs = (Object[]) clauseInfo.getLemmaAnnotation();
		if (coeffs == null) {
			// trichotomy
			assert lits.length == 3;
			for (int i = 0; i < 3; i++) {
				final Term atom = mInterpolator.getAtom(lits[i]);
				final InterpolatorAtomInfo atomTermInfo = mInterpolator.getAtomTermInfo(atom);
				if (atomTermInfo.isLAEquality()) {
					coeffMap.put(lits[i], Rational.ONE);
				} else {
					coeffMap.put(lits[i], lits[i] != atom ? Rational.ONE : Rational.MONE);
				}
			}
			return coeffMap;
		}
		for (int i = 0; i < coeffs.length; i++) {
			term = lits[i];
			coeff = SMTAffineTerm.convertConstant((ConstantTerm) coeffs[i]);
			coeffMap.put(term, coeff);
		}
		return coeffMap;
	}

	/**
	 * Interpolate an LA lemma. Normally, the interpolant is computed by summing up the A-part of all literals minding
	 * the Farkas coefficients. For trichotomy clauses we have to return the special trichotomy interpolant,
	 *
	 * <pre>
	 * LA(x1 + x2 &lt= 0, 0, x1 + x2 &lt= 0 and
	 *         (x1 + x2 &lt 0 or EQ(x, x1)))
	 * </pre>
	 *
	 * in the mixed case.
	 *
	 * @param lemma
	 *            the LA lemma that is interpolated.
	 * @return an array containing the partial tree interpolants.
	 */
	public Term[] computeInterpolants(final Term lemma) {
		final InterpolatorAffineTerm[] ipl = new InterpolatorAffineTerm[mInterpolator.mNumInterpolants + 1];
		for (int part = 0; part < ipl.length; part++) {
			ipl[part] = new InterpolatorAffineTerm();
		}
		@SuppressWarnings("unchecked")
		final ArrayList<TermVariable>[] auxVars = new ArrayList[mInterpolator.mNumInterpolants];
		/*
		 * these variables are used for trichotomy clauses. The inequalityInfo will remember the information for one of
		 * the inequalities to get the aux literal. The equality will remember the equality literal (AnnotatedTerm), the
		 * eqApp the equality (ApplicationTerm), and equalityInfo will remember its info.
		 */
		Term equality = null;
		Term eqApp = null;
		LitInfo equalityOccurenceInfo = null;
		Interpolator.Occurrence inequalityInfo = null;

		/*
		 * Add the A-part of the literals in this LA lemma.
		 */
		final InterpolatorClauseTermInfo lemmaInfo = mInterpolator.getClauseTermInfo(lemma);

		for (final Entry<Term, Rational> entry : getFarkasCoeffs(lemmaInfo).entrySet()) {
			final Term atom = mInterpolator.getAtom(entry.getKey());
			final InterpolatorAtomInfo atomTermInfo = mInterpolator.getAtomTermInfo(atom);
			// Is the literal negated in conflict?  I.e. not negated in clause.
			final boolean isNegated = atom == entry.getKey();
			final Rational factor = entry.getValue();
			if (atomTermInfo.isBoundConstraint() || (!isNegated && atomTermInfo.isLAEquality())) {
				final LitInfo occurenceInfo = mInterpolator.getAtomOccurenceInfo(atom);
				inequalityInfo = occurenceInfo;

				final InterpolatorAffineTerm lv = new InterpolatorAffineTerm(atomTermInfo.getAffineTerm());
				/* for negated literals subtract epsilon because we need the inverse bound */
				if (isNegated) {
					lv.add(atomTermInfo.getEpsilon().negate());
				}
				int part = occurenceInfo.mInB.nextClearBit(0);
				while (part < ipl.length) {
					if (occurenceInfo.isMixed(part)) {
						/* ab-mixed interpolation */
						assert occurenceInfo.mMixedVar != null;
						ipl[part].add(factor, occurenceInfo.getAPart(part));
						ipl[part].add(factor.negate(), occurenceInfo.mMixedVar);

						if (auxVars[part] == null) {
							auxVars[part] = new ArrayList<>();
						}
						auxVars[part].add(occurenceInfo.mMixedVar);
					}
					if (occurenceInfo.isALocal(part)) {
						/* Literal in A: add to sum */
						ipl[part].add(factor, lv);
					}
					part++;
				}
			} else {
				assert isNegated && atomTermInfo.isLAEquality();
				// we have a Trichotomy Clause
				equality = atom;
				// a trichotomy clause must contain exactly three parts
				assert lemmaInfo.getLiterals().length == 3;// NOCHECKSTYLE
				assert equalityOccurenceInfo == null;
				// safe the equality and its occurrence info for later.
				equalityOccurenceInfo = mInterpolator.getAtomOccurenceInfo(equality);
				eqApp = mInterpolator.unquote(equality);
				assert eqApp instanceof ApplicationTerm;
				assert factor.abs() == Rational.ONE;

				int part = equalityOccurenceInfo.mInB.nextClearBit(0);
				while (part < ipl.length) {
					if (equalityOccurenceInfo.isALocal(part)) {
						/* Literal in A: add epsilon to sum */
						ipl[part].add(atomTermInfo.getEpsilon());
					}
					part++;
				}
			}
		}
		assert ipl[ipl.length - 1].isConstant() && ipl[ipl.length - 1].getConstant().signum() > 0;

		/*
		 * Save the interpolants computed for this leaf into the result array.
		 */
		final Term[] interpolants = new Term[mInterpolator.mNumInterpolants];
		for (int part = 0; part < auxVars.length; part++) {
			final Rational normFactor = ipl[part].isConstant() ? Rational.ONE : ipl[part].getGcd().inverse().abs();
			ipl[part].mul(normFactor);
			/*
			 * Round up the (negated) constant if all terms in the interpolant are known to be integer. This is sound
			 * since x <= 0 is equivalent to ceil(x) <= 0.
			 */
			if (ipl[part].isInt()) {
				final InfinitesimalNumber constant = ipl[part].getConstant();
				ipl[part].add(constant.ceil().sub(constant));
			}

			if (auxVars[part] != null) { // NOPMD
				/*
				 * This is a mixed interpolant with auxiliary variables. Prepare an LATerm that wraps the interpolant.
				 */
				InfinitesimalNumber k;
				Term F;
				if (equalityOccurenceInfo != null) { // NOPMD
					/*
					 * This is a mixed trichotomy clause. This requires a very special interpolant.
					 */
					assert equalityOccurenceInfo.isMixed(part);
					assert auxVars[part].size() == 2;
					assert normFactor == Rational.ONE;
					final InterpolatorAffineTerm less = new InterpolatorAffineTerm(ipl[part]);
					less.add(InfinitesimalNumber.EPSILON);
					k = InfinitesimalNumber.ZERO;
					F = mInterpolator.mTheory.and(ipl[part].toLeq0(mInterpolator.mTheory), mInterpolator.mTheory.or(
							less.toLeq0(mInterpolator.mTheory),
							mInterpolator.mTheory.term(Interpolator.EQ, equalityOccurenceInfo.getMixedVar(),
									auxVars[part].iterator().next())));
				} else {
					/* Just the inequalities are mixed. */
					if (ipl[part].isInt()) {
						k = InfinitesimalNumber.ONE.negate();
					} else {
						k = InfinitesimalNumber.EPSILON.negate();
					}
					F = ipl[part].toLeq0(mInterpolator.mTheory);
				}
				interpolants[part] = createLATerm(ipl[part], k, F);
			} else {
				assert equalityOccurenceInfo == null || !equalityOccurenceInfo.isMixed(part);
				if (equalityOccurenceInfo != null && ipl[part].isConstant()
						&& equalityOccurenceInfo.isALocal(part) != inequalityInfo.isALocal(part)) {
					// special case: Nelson-Oppen conflict, a <= b and b <= a in one partition, a != b in the other.
					// If a != b is in A, the interpolant is simply a != b.
					// If a != b is in B, the interpolant is simply a == b.
					final Term thisIpl =
							equalityOccurenceInfo.isALocal(part) ? mInterpolator.mTheory.not(eqApp) : eqApp;
					interpolants[part] = thisIpl;
				} else {
					interpolants[part] = ipl[part].toLeq0(mInterpolator.mTheory);
				}
			}
		}
		return interpolants;
	}
}
