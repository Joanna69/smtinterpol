/*
 * Copyright (C) 2009-2012 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.linar;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import de.uni_freiburg.informatik.ultimate.logic.MutableRational;
import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.smtinterpol.convert.SharedTerm;


/**
 * Class representing a variable in a linear combination. This might either be
 * a slack variable or a real variable as introduced by the problem. For slack
 * variables we use the object name to refer to the corresponding linear term.
 *
 * Natural order depends on the order of creation of variables. That is every
 * variable is ordered according to their creation time. Ordering on the
 * variables is one requirement to be able to prove termination of the
 * algorithm.
 *
 * Every variable knows all possible bound constraints referring to it. This
 * way, bound propagations can easily be implemented.
 *
 * @author Juergen Christ
 */
public class LinVar implements Comparable<LinVar> {
	/**
	 * Name of the variable.  This is either a SharedTermOld for initially
	 * non-basic variables or a LinTerm for initially basic variables.
	 */
	Object mName;

	LiteralReason mUpperLiteral;
	LiteralReason mLowerLiteral;

	/** Current upper bound and its reason. null if no upper bound. */
	LAReason mUpper;
	/** Current lower bound and its reason. null if no lower bound. */
	LAReason mLower;
	/** Current value. */
	private MutableExactInfinitesimalNumber mCurval;
	// Is value required to be integer?
	boolean mIsInt;
	// List of all bounds on this variable
	NavigableMap<InfinitesimalNumber, BoundConstraint> mConstraints =
		new TreeMap<InfinitesimalNumber, BoundConstraint>();
	// List of all equalities known for this variable
	NavigableMap<InfinitesimalNumber, LAEquality> mEqualities =
		new TreeMap<InfinitesimalNumber, LAEquality>();

	/**
	 * All disequalities asserted on this variable.
	 */
	Map<Rational,LAEquality> mDisequalities;
	// Is this variable currently basic? NOTE: THIS IS THE CURRENT STATUS
	boolean mBasic;
	// Number to sort this variable in the sparse matrix
	final int mMatrixpos;
	int mNumcuts = 0;
	/**
	 * The dummy entry that starts the list for this linvar.
	 * The list follows nextInRow if this is a basic variable,
	 * nextInCol if this is a non-basic variable.
	 */
	MatrixEntry mHeadEntry;

	/**
	 * Number of infinities currently contributing to the upper bound.
	 */
	int mNumUpperInf;
	/**
	 * Number of infinities currently contributing to the lower bound.
	 */
	int mNumLowerInf;
	/**
	 * Number of epsilons currently contributing to the upper bound.
	 */
	int mNumUpperEps;
	/**
	 * Number of epsilons currently contributing to the lower bound.
	 */
	int mNumLowerEps;
	/**
	 * Rational part of upper bound.
	 * This still has to be divided by the head coeff (and may also be a
	 * lower bound if head coeff is negative).
	 */
	private MutableRational mUpperComposite = new MutableRational(Rational.ZERO);
	/**
	 * Rational part of lower bound.
	 * This still has to be divided by the head coeff (and may also be a
	 * upper bound if head coeff is negative).
	 */
	private MutableRational mLowerComposite = new MutableRational(Rational.ZERO);
	LinVar[] mCachedRowVars;
	Rational[] mCachedRowCoeffs;

	int mAssertionstacklevel;

	int mChainlength;

	ExactInfinitesimalNumber mExactVal = null;

	/// --- Construction ---
	/**
	 * Constructs a dummy linear variable.
	 */
	private LinVar() {
		mName = "Dummy";
		mMatrixpos = Integer.MAX_VALUE;
	}
	/**
	 * Constructor for a variable.
	 * @param name  Name of the variable (Needed for printout).
	 * @param isint Is the variable required to be integral valued?
	 * @param assertionstacklevel The number of pushes succeeded before this
	 * 							  variable has been created.
	 * @param num The count for this variable.
	 */
	public LinVar(final Object name,final boolean isint, final int assertionstacklevel, final int num) {
		mName = name;
		mCurval = new MutableExactInfinitesimalNumber();
		mIsInt = isint;
		mBasic = false;
		mMatrixpos = num;

		mHeadEntry = new MatrixEntry();
		mHeadEntry.mColumn = this;
		mHeadEntry.mRow = DUMMY_LINVAR;
		mHeadEntry.mPrevInCol = mHeadEntry;
		mHeadEntry.mNextInCol = mHeadEntry;
		mAssertionstacklevel = assertionstacklevel;
		mChainlength = 0;
	}
	/**
	 * Get the upper bound.
	 * @return Upper bound.
	 */
	public final InfinitesimalNumber getUpperBound() {
		return mUpperLiteral == null ? InfinitesimalNumber.POSITIVE_INFINITY : mUpperLiteral.getBound();
	}
	/**
	 * Get the lower bound.
	 * @return Lower bound.
	 */
	public final InfinitesimalNumber getLowerBound() {
		return mLowerLiteral == null ? InfinitesimalNumber.NEGATIVE_INFINITY : mLowerLiteral.getBound();
	}

	public final InfinitesimalNumber getTightUpperBound() {
		return mUpper == null ? InfinitesimalNumber.POSITIVE_INFINITY : mUpper.getBound();
	}

	public final InfinitesimalNumber getTightLowerBound() {
		return mLower == null ? InfinitesimalNumber.NEGATIVE_INFINITY : mLower.getBound();
	}
	public InfinitesimalNumber getExactUpperBound() {
		return mUpper == null ? InfinitesimalNumber.POSITIVE_INFINITY
			: mUpper.getExactBound();
	}
	public InfinitesimalNumber getExactLowerBound() {
		return mLower == null ? InfinitesimalNumber.NEGATIVE_INFINITY
			: mLower.getExactBound();
	}
	/**
	 * Check whether the upper bound is set.
	 * @return <code>true</code> iff upper bound is finite.
	 */
	public final boolean hasTightUpperBound() {
		return mUpper != null;
	}
	/**
	 * Check whether the lower bound is set.
	 * @return <code>true</code> iff lower bound is finite.
	 */
	public final boolean hasTightLowerBound() {
		return mLower != null;
	}
	/// --- Name stuff ---
	@Override
	public String toString() {
		return mName.toString();
	}
	/// --- Initially basic testing ---
	/**
	 * Check whether this variable is initially basic.
	 * @return <code>true</code> iff this variable corresponds to a linear term
	 */
	public boolean isInitiallyBasic() {
		return mName instanceof LinTerm;
	}

	@Override
	public int hashCode() {
		return mMatrixpos;
	}

	@Override
	public final int compareTo(final LinVar o) {
		return mMatrixpos - o.mMatrixpos;
	}
	/**
	 * Check whether this variable has a value outside its bounds.
	 * @return <code>false</code> iff <code>mlbound<=mcurval<=mubound</code>.
	 */
	public boolean outOfBounds() {
		if (mUpperLiteral != null) {
			if (mCurval.compareTo(mUpperLiteral.getBound()) > 0) {
				return true;
			}
		}
		if (mLowerLiteral != null) {
			if (mCurval.compareTo(mLowerLiteral.getExactBound()) < 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Dummy linear variable marking end of a non-basic chain.
	 */
	final static LinVar DUMMY_LINVAR = new LinVar();

	void addDiseq(final LAEquality ea) {
		if (mDisequalities == null) {
			mDisequalities = new HashMap<Rational,LAEquality>();
		}
		// we only remember the first disequality.  We only care if there
		// is a disequality not which exactly.  The first is also better
		// to explain a conflict.
		if (!mDisequalities.containsKey(ea.getBound())) {
			mDisequalities.put(ea.getBound(),ea);
		}
	}
	void removeDiseq(final LAEquality ea) {
		// only remove the disequality if it is the right one.
		// chronological backtracking will ensure that if we remove
		// the disequality there is no other disequality with the
		// same bound missing.
		// mdisequalities can be null, if the literal was not set, because
		// it already produced a conflict in another theory.
		if (mDisequalities != null
			&& mDisequalities.get(ea.getBound()) == ea) {
			mDisequalities.remove(ea.getBound());
		}
	}
	LAEquality getDiseq(final Rational bound) {
		if (mDisequalities != null) {
			return mDisequalities.get(bound);
		}
		return null;
	}
	public void addEquality(final LAEquality ea) {
		mEqualities.put(new InfinitesimalNumber(ea.getBound(), 0), ea);
	}
	boolean unconstrained() {
		return mConstraints.isEmpty() && mEqualities.isEmpty();
	}
	boolean isCurrentlyUnconstrained() {
		return mLower == null && mUpper == null;
	}
	public boolean isInt() {
		return mIsInt;
	}
	public InfinitesimalNumber getEpsilon() {
		return mIsInt ? InfinitesimalNumber.ONE : InfinitesimalNumber.EPSILON;
	}

	public final void moveBounds(final LinVar other) {
		mNumUpperInf = other.mNumUpperInf;
		mNumLowerInf = other.mNumLowerInf;
		mNumUpperEps = other.mNumUpperEps;
		mNumLowerEps = other.mNumLowerEps;
		mUpperComposite = other.mUpperComposite;
		mLowerComposite = other.mLowerComposite;
		other.mUpperComposite = null;
		other.mLowerComposite = null;
	}

	public void mulUpperLower(final Rational r) {
		mUpperComposite.mul(r);
		mLowerComposite.mul(r);
	}

	public final void updateUpper(
			final BigInteger coeff, final InfinitesimalNumber oldBound, final InfinitesimalNumber newBound) {
		if (oldBound.isInfinity()) {
			if (newBound.isInfinity()) {
				return;
			}
			mNumUpperInf--;
			mUpperComposite.addmul(newBound.mReal, coeff);
		} else if (newBound.isInfinity()) {
			mNumUpperInf++;
			mUpperComposite.addmul(oldBound.mReal.negate(), coeff);
		} else {
			mUpperComposite.addmul(newBound.mReal.sub(oldBound.mReal), coeff);
		}
		mNumUpperEps += (newBound.mEps - oldBound.mEps) * coeff.signum();
	}

	public final void updateLower(
			final BigInteger coeff, final InfinitesimalNumber oldBound, final InfinitesimalNumber newBound) {
		if (oldBound.isInfinity()) {
			if (newBound.isInfinity()) {
				return;
			}
			mNumLowerInf--;
			mLowerComposite.addmul(newBound.mReal, coeff);
		} else if (newBound.isInfinity()) {
			mNumLowerInf++;
			mLowerComposite.addmul(oldBound.mReal.negate(), coeff);
		} else {
			mLowerComposite.addmul(newBound.mReal.sub(oldBound.mReal), coeff);
		}
		mNumLowerEps += (newBound.mEps - oldBound.mEps) * coeff.signum();
	}

	public void updateUpperLowerSet(final BigInteger coeff, final LinVar nb) {
		InfinitesimalNumber ubound = nb.getTightUpperBound();
		InfinitesimalNumber lbound = nb.getTightLowerBound();
		if (coeff.signum() < 0) {
			final InfinitesimalNumber swap = ubound;
			ubound = lbound;
			lbound = swap;
		}
		if (ubound.isInfinity()) {
			mNumUpperInf++;
		} else {
			mUpperComposite.addmul(ubound.mReal, coeff);
		}
		mNumUpperEps += ubound.mEps * coeff.signum();
		if (lbound.isInfinity()) {
			mNumLowerInf++;
		} else {
			mLowerComposite.addmul(lbound.mReal, coeff);
		}
		mNumLowerEps += lbound.mEps * coeff.signum();
	}
	public void updateUpperLowerClear(final BigInteger coeff, final LinVar nb) {
		InfinitesimalNumber ubound = nb.getTightUpperBound().negate();
		InfinitesimalNumber lbound = nb.getTightLowerBound().negate();
		if (coeff.signum() < 0) {
			final InfinitesimalNumber swap = ubound;
			ubound = lbound;
			lbound = swap;
		}
		if (ubound.isInfinity()) {
			mNumUpperInf--;
		} else {
			mUpperComposite.addmul(ubound.mReal, coeff);
		}
		mNumUpperEps += ubound.mEps * coeff.signum();
		if (lbound.isInfinity()) {
			mNumLowerInf--;
		} else {
			mLowerComposite.addmul(lbound.mReal, coeff);
		}
		mNumLowerEps += lbound.mEps * coeff.signum();
	}
	public InfinitesimalNumber getUpperComposite() {
		if (mHeadEntry.mCoeff.signum() < 0) {
			if (mNumUpperInf != 0) {
				return InfinitesimalNumber.POSITIVE_INFINITY;
			}
			final Rational denom = mUpperComposite.toRational()
				.mul(Rational.valueOf(BigInteger.valueOf(-1), mHeadEntry.mCoeff));
			return new InfinitesimalNumber(denom, InfinitesimalNumber.normEpsilon(mNumUpperEps));
		} else {
			if (mNumLowerInf != 0) {
				return InfinitesimalNumber.POSITIVE_INFINITY;
			}
			final Rational denom = mLowerComposite.toRational()
				.mul(Rational.valueOf(BigInteger.valueOf(-1), mHeadEntry.mCoeff));
			return new InfinitesimalNumber(denom, -InfinitesimalNumber.normEpsilon(mNumLowerEps));
		}
	}

	public InfinitesimalNumber getLowerComposite() {
		if (mHeadEntry.mCoeff.signum() < 0) {
			if (mNumLowerInf != 0) {
				return InfinitesimalNumber.NEGATIVE_INFINITY;
			}
			final Rational denom = mLowerComposite.toRational()
				.mul(Rational.valueOf(BigInteger.valueOf(-1), mHeadEntry.mCoeff));
			return new InfinitesimalNumber(denom, InfinitesimalNumber.normEpsilon(mNumLowerEps));
		} else {
			if (mNumUpperInf != 0) {
				return InfinitesimalNumber.NEGATIVE_INFINITY;
			}
			final Rational denom = mUpperComposite.toRational()
				.mul(Rational.valueOf(BigInteger.valueOf(-1), mHeadEntry.mCoeff));
			return new InfinitesimalNumber(denom, -InfinitesimalNumber.normEpsilon(mNumUpperEps));
		}
	}

	void resetComposites() {
		mLowerComposite.setValue(Rational.ZERO);
		mUpperComposite.setValue(Rational.ZERO);
		mNumUpperInf = 0;
		mNumLowerInf = 0;
		mNumUpperEps = 0;
		mNumLowerEps = 0;
		mCachedRowCoeffs = null;
		mCachedRowVars = null;
	}

	/**
	 * Get the linear term from which this basic linvar was created.
	 * @throws ClassCastException if this is not an initially basic variable.
	 * @return the LinTerm.
	 */
	public Map<LinVar,BigInteger> getLinTerm() {
		return ((LinTerm) mName).mCoeffs;
	}

	/**
	 * Get the shared term from which this non-basic linvar was created.
	 * @throws ClassCastException if this is not an initially non-basic variable.
	 * @return the shared term.
	 */
	public SharedTerm getSharedTerm() {
		return (SharedTerm) mName;
	}

	public int getAssertionStackLevel() {
		return mAssertionstacklevel;
	}
	public boolean checkBrpCounters() {
		assert mBasic;
		int cntLower = 0, cntLowerEps = 0;
		int cntUpper = 0, cntUpperEps = 0;
		final MutableExactInfinitesimalNumber value = new MutableExactInfinitesimalNumber();
		final MutableExactInfinitesimalNumber lbound = new MutableExactInfinitesimalNumber();
		final MutableExactInfinitesimalNumber ubound = new MutableExactInfinitesimalNumber();
		for (MatrixEntry entry = mHeadEntry.mNextInRow;
			 entry != mHeadEntry; entry = entry.mNextInRow) {
			value.addmul(entry.mColumn.mCurval.toNumber(), Rational.valueOf(entry.mCoeff, BigInteger.ONE));
			if (entry.mCoeff.signum() < 0) {
				if (entry.mColumn.hasTightUpperBound()) {
					lbound.addmul(entry.mColumn.getTightUpperBound(), entry.mCoeff);
				} else {
					cntLower++;
				}
				cntLowerEps -= entry.mColumn.getTightUpperBound().mEps;
				if (entry.mColumn.hasTightLowerBound()) {
					ubound.addmul(entry.mColumn.getTightLowerBound(), entry.mCoeff);
				} else {
					cntUpper++;
				}
				cntUpperEps -= entry.mColumn.getTightLowerBound().mEps;
			} else {
				if (entry.mColumn.hasTightUpperBound()) {
					ubound.addmul(entry.mColumn.getTightUpperBound(), entry.mCoeff);
				} else {
					cntUpper++;
				}
				cntUpperEps += entry.mColumn.getTightUpperBound().mEps;
				if (entry.mColumn.hasTightLowerBound()) {
					lbound.addmul(entry.mColumn.getTightLowerBound(), entry.mCoeff);
				} else {
					cntLower++;
				}
				cntLowerEps += entry.mColumn.getTightLowerBound().mEps;
			}
		}
		value.div(Rational.valueOf(mHeadEntry.mCoeff.negate(), BigInteger.ONE));
		assert cntLower == mNumLowerInf && cntUpper == mNumUpperInf;
		assert lbound.mReal.equals(mLowerComposite);
		assert lbound.mEps.signum() == Integer.signum(mNumLowerEps);
		assert cntLowerEps == mNumLowerEps;
		assert ubound.mReal.equals(mUpperComposite);
		assert ubound.mEps.signum() == Integer.signum(mNumUpperEps);
		assert cntUpperEps == mNumUpperEps;
		assert value.mReal.equals(mCurval.mReal);
		return true;
	}

	public boolean checkCoeffChain() {
		if (!mBasic) {
			return true;
		}
		assert mHeadEntry.mRow == mHeadEntry.mColumn;
		//assert headEntry.nextInCol == headEntry;
		//assert headEntry.nextInCol == headEntry.prevInCol;
		final MutableAffineTerm mat = new MutableAffineTerm();
		MatrixEntry entry = mHeadEntry;
		do {
			assert entry.mRow == this;
			assert entry.mRow == entry.mColumn || !entry.mColumn.mBasic;
			assert entry.mNextInRow.mPrevInRow == entry;
			mat.add(Rational.valueOf(entry.mCoeff, BigInteger.ONE), entry.mColumn);
			entry = entry.mNextInRow;
		} while (entry != mHeadEntry);
		assert mat.isConstant() && mat.getConstant().equals(InfinitesimalNumber.ZERO);
		return true;
	}
	public boolean isFixed() {
		return mUpper != null && mLower != null
			&& mUpper.getBound().equals(mLower.getBound());
	}

	boolean checkChainlength() {
		if (mBasic) {
			int length = 0;
			for (MatrixEntry entry = mHeadEntry.mNextInRow; entry != mHeadEntry;
			entry = entry.mNextInRow) {
				++length;
			}
			assert(length == mChainlength);
		} else {
			int length = 0;
			for (MatrixEntry entry = mHeadEntry.mNextInCol; entry != mHeadEntry;
			entry = entry.mNextInCol) {
				++length;
			}
			assert(length == mChainlength);
		}
		return true;
	}

	public LAEquality getEquality(final InfinitesimalNumber bound) {
		return mEqualities.get(bound);
	}

	public final ExactInfinitesimalNumber getValue() {
		return mCurval.toNumber();
	}

	public final void setValue(final MutableExactInfinitesimalNumber value) {
		mCurval.assign(value);
	}

	public final void addValue(final ExactInfinitesimalNumber value) {
		mCurval.add(value);
	}

	private boolean checkReasonChain(LAReason reason, LiteralReason litreason) {
		while (reason != null) {
			if (reason instanceof LiteralReason) {
				assert reason == litreason;
				litreason = litreason.getOldLiteralReason();
			}
			reason = reason.getOldReason();
		}
		assert litreason == null;
		return true;
	}

	public boolean checkReasonChains() {
		return checkReasonChain(mUpper, mUpperLiteral)
				&& checkReasonChain(mLower, mLowerLiteral);
	}
}
