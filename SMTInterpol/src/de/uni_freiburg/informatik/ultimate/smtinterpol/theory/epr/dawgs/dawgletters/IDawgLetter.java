/*
 * Copyright (C) 2016-2017 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2016-2017 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.dawgletters;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * An {@code IDawgLetter<LETTER>} represents a set of LETTER. There are basic set operations for these letters.
 *
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 * @param <LETTER>
 * @param <COLNAMES>
 */
public interface IDawgLetter<LETTER> {

	Object getSortId();

	Set<IDawgLetter<LETTER>> complement();

	Set<IDawgLetter<LETTER>> difference(IDawgLetter<LETTER> other);

	/**
	 * Compute a DawgLetter whose denotation is the intersection of the denotation of the given two DawgLetters.
	 *
	 * Note that this does not take into account equalities that may hold between LETTERs. E.g., {a}.intersect({b}) will
	 *  yield the EmptyDawgLetter even if our current decide state says that a equals b.
	 *
	 * @param other
	 * @return
	 */
	IDawgLetter<LETTER> intersect(IDawgLetter<LETTER> other);

	boolean matches(LETTER ltr, List<LETTER> word);

	/**
	 * Returns all LETTERs (typically constants) that this DawgLetter refers to (and that are
	 *  currently known, see below).
	 *
	 * NOTE:
	 * This method is special in that it needs the current AllConstants set.
	 * It is (and perhaps should be) only used in DawgIterator.
	 *  -- and possibly in other locations where it is obviously necessary to know what the
	 *    constants are, currently..
	 *
	 * @param word
	 * @return
	 */
	Collection<LETTER> allLettersThatMatch(List<LETTER> word);

	/**
	 *
	 * TODO: perhaps replace the implementations through a reduction to intersect with a singletonDawgLetter?
	 */
	IDawgLetter<LETTER> restrictToLetter(LETTER selectLetter);

	IDawgLetter<LETTER> union(IDawgLetter<LETTER> other);
}