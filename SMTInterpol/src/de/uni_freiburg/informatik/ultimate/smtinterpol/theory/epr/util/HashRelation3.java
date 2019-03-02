
/*
 * Copyright (C) 2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE Util Library.
 *
 * The ULTIMATE Util Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE Util Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Util Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Util Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Util Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.util;
//package de.uni_freiburg.informatik.ultimate.util.datastructures.relation;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.DeterministicDawgTransitionRelation;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.dawgletters.DawgLetter;

/**
 * Ternary relation implemented via nested HashMaps.
 * @author Matthias Heizmann
 *
 */
public class HashRelation3<K1, K2 extends DawgLetter<?>, K3> {
	private final NestedMap3<K1, K2, K3, IsContained> mBackingMap = new NestedMap3<K1, K2, K3, IsContained>();

	/**
	 * Creates a HashRelation3 form a nestedMap2
	 * (that relation will have the fuction property..)
	 */
	public HashRelation3(
			final DeterministicDawgTransitionRelation<K1, K2, K3> map) {
		for (final Triple<K1, K2, K3> triple : map.entrySet()) {
			addTriple(triple.getFirst(), triple.getSecond(), triple.getThird());
		}
	}

	/**
	 * constructor for empty relation
	 */
	public HashRelation3() {
	}

	public boolean addTriple(final K1 fst, final K2 snd, final K3 trd) {
		final IsContained isContained = mBackingMap.put(fst, snd, trd, IsContained.IsContained);
		return isContained == IsContained.IsContained;
	}

	public Set<K1> projectToFst() {
		return mBackingMap.keySet();
	}

	public Set<K2> projectToSnd(final K1 k1) {
		 final NestedMap2<K2, K3, IsContained> snd2trd2ic = mBackingMap.get(k1);
		 if (snd2trd2ic == null) {
			 return Collections.emptySet();
		 } else {
			 return snd2trd2ic.keySet();
		 }
	}

	public Set<K3> projectToTrd(final K1 k1, final K2 k2) {
		 final Map<K3, IsContained> trd2ic  = mBackingMap.get(k1, k2);
		 if (trd2ic == null) {
			 return Collections.emptySet();
		 } else {
			 return trd2ic.keySet();
		 }
	}

	@Override
	public String toString() {
		if (mBackingMap.keySet().isEmpty()) {
			return "Empty Hashrelation3";
		}
		final StringBuilder sb = new StringBuilder();

		for (final K1 k1 : projectToFst()) {
			for (final K2 k2 : projectToSnd(k1)) {
				for (final K3 k3 : projectToTrd(k1, k2)) {
					sb.append(String.format("(%s, %s, %s)\n", k1, k2, k3));
				}
			}
		}

		return sb.toString();
	}
}
