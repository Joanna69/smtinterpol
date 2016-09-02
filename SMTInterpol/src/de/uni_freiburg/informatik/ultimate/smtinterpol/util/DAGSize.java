/*
 * Copyright (C) 2013 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.smtinterpol.util;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.AnnotatedTerm;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.ConstantTerm;
import de.uni_freiburg.informatik.ultimate.logic.FormulaUnLet;
import de.uni_freiburg.informatik.ultimate.logic.LetTerm;
import de.uni_freiburg.informatik.ultimate.logic.NonRecursive;
import de.uni_freiburg.informatik.ultimate.logic.QuantifiedFormula;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;

public class DAGSize extends NonRecursive {

	private class TermOnceWalker extends NonRecursive.TermWalker {

		public TermOnceWalker(Term term) {
			super(term);
		}

		@Override
		public void walk(NonRecursive walker) {
			if (mSeen.add(mTerm)) {
				++mSize;
				super.walk(walker);
			}
		}

		@Override
		public void walk(NonRecursive walker, ConstantTerm term) {
			// No subterms to enqueue
		}

		@Override
		public void walk(NonRecursive walker, AnnotatedTerm term) {
			// TODO Do we want to count annotations???
			walker.enqueueWalker(new TermOnceWalker(term.getSubterm()));
		}

		@Override
		public void walk(NonRecursive walker, ApplicationTerm term) {
			for (final Term t : term.getParameters()) {
				walker.enqueueWalker(new TermOnceWalker(t));
			}
		}

		@Override
		public void walk(NonRecursive walker, LetTerm term) {
			throw new InternalError("Input should be unletted");
		}

		@Override
		public void walk(NonRecursive walker, QuantifiedFormula term) {
			walker.enqueueWalker(new TermOnceWalker(term.getSubformula()));
		}

		@Override
		public void walk(NonRecursive walker, TermVariable term) {
			// TODO Do we want to count this???  It is unified...
			// No subterms to enqueue
		}
		
	}
	
	private final static class ConstantEmptySet extends AbstractSet<Term> {
		
		private final static Set<Term> EMPTY = new ConstantEmptySet();

		@Override
		public Iterator<Term> iterator() {
			// Collections.emptyIterator() is Java 1.7!!!
			return Collections.<Term>emptySet().iterator();
		}

		@Override
		public int size() {
			return 0;
		}

		@Override
		public boolean add(Term e) {
			// Intentionally break the contract of add...
			return true;
		}
		
	}
	
	private Set<Term> mSeen;
	private int mSize;
	
	public DAGSize() {
		mSeen = new HashSet<Term>();
		mSize = 0;
	}
	
	@Override
	public void reset() {
		super.reset();
		mSeen.clear();
		mSize = 0;
	}
	
	public int size(Term term) {
		run(new TermOnceWalker(new FormulaUnLet().unlet(term)));
		return mSize;
	}
	
	public int treesize(Term term) {
		final Set<Term> old = mSeen;
		mSeen = ConstantEmptySet.EMPTY;
		run(new TermOnceWalker(new FormulaUnLet().unlet(term)));
		mSeen = old;
		return mSize;
	}
}
