/*
 * Copyright (C) 2012-2013 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.smtinterpol.delta;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.PrintTerm;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.Theory;

final class ReplaceByFreshTerm extends Substitution {

	public ReplaceByFreshTerm(Term match) {
		super(match);
	}

	private Cmd mAdd;

	public final static String FRESH_PREFIX = "@DELTA_DEBUG_FRESH_";
	private static int freshnum = 0; // NOCHECKSTYLE since not multi-threaded

	private static String getNextFreshName() {
		return FRESH_PREFIX + (++freshnum);
	}

	public static void ensureNotFresh(int num) {
		if (freshnum <= num) {
			freshnum = num + 1;
		}
	}

	@Override
	public Term apply(Term input) {
		final String funname = getNextFreshName();
		mAdd = new DeclareFun(funname, Script.EMPTY_SORT_ARRAY, input.getSort());
		final Theory t = input.getTheory();
		return t.term(
				t.declareFunction(
						funname, Script.EMPTY_SORT_ARRAY, input.getSort()));
	}

	@Override
	public Cmd getAdditionalCmd(Term input) {
		return mAdd;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		final PrintTerm pt = new PrintTerm();
		pt.append(sb, getMatch());
		sb.append(" ==> fresh");
		return sb.toString();
	}

	public static boolean isFreshTerm(ApplicationTerm at) {
		return at.getParameters().length == 0
				&& at.getFunction().getName().startsWith(FRESH_PREFIX);
	}

}
