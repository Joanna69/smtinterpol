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
package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.EprHelpers;

import java.util.Map.Entry;

/**
 * 
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 * @param <LETTER>
 * @param <COLNAMES>
 */
public class UnionOrIntersectionDawgBuilder<LETTER, COLNAMES> {
	
	private final DawgState mResultInitialState;
	private final DawgStateFactory<LETTER, COLNAMES> mDawgStateFactory;
	private final DeterministicDawgTransitionRelation<DawgState, IDawgLetter<LETTER, COLNAMES>, DawgState> mResultTransitionRelation;
	private final Dawg<LETTER, COLNAMES> mFirstInputDawg;
	private final Dawg<LETTER, COLNAMES> mSecondInputDawg;
	private final DawgLetterFactory<LETTER, COLNAMES> mDawgLetterFactory;
	private final DawgFactory<LETTER, COLNAMES> mDawgFactory;

	UnionOrIntersectionDawgBuilder(Dawg<LETTER, COLNAMES> first, Dawg<LETTER, COLNAMES> second, 
			DawgFactory<LETTER, COLNAMES> df) {
		assert first.mColNames.equals(second.mColNames) : "signatures don't match!";
		mDawgFactory = df;
		mDawgStateFactory = df.getDawgStateFactory();
		mDawgLetterFactory = df.getDawgLetterFactory();
		
		mFirstInputDawg = first; 
		mSecondInputDawg = second;
		
		mResultTransitionRelation = new DeterministicDawgTransitionRelation<DawgState, IDawgLetter<LETTER,COLNAMES>,DawgState>();

		mResultInitialState = new PairDawgState(first.mInitialState, second.mInitialState);
		
	}
	
	Dawg<LETTER, COLNAMES> buildUnion() {
		return build(true);
	}
	
	Dawg<LETTER, COLNAMES> buildIntersection() {
		return build(false);
	}
	
	/**
	 * 
	 * @param doUnion if this flag is true, build a dawg that recognizes the union of mFirst and 
	 *   mSecond, otherwise build a dawg that recognizes the intersection of the two
	 * @return
	 */
	private Dawg<LETTER, COLNAMES> build(boolean doUnion) {
		Set<PairDawgState> currentStates = new HashSet<PairDawgState>();
		currentStates.add((PairDawgState) mResultInitialState);
		
		for (int i = 0; i < mFirstInputDawg.getColnames().size(); i++) {
			final Set<PairDawgState> nextStates = new HashSet<PairDawgState>();
			
			for (PairDawgState cs : currentStates) {
				
				if (!cs.mFirstIsSink && !cs.mSecondIsSink) {

					for (Pair<IDawgLetter<LETTER, COLNAMES>, DawgState> firstOutEdge :
						mFirstInputDawg.getTransitionRelation().getOutEdgeSet(cs.getFirst())) {
						final IDawgLetter<LETTER, COLNAMES> firstDl = firstOutEdge.getFirst();
						final DawgState firstTarget = firstOutEdge.getSecond();

						for (Pair<IDawgLetter<LETTER, COLNAMES>, DawgState> secondOutEdge :
							mSecondInputDawg.getTransitionRelation().getOutEdgeSet(cs.getSecond())) {
							final IDawgLetter<LETTER, COLNAMES> secondDl = secondOutEdge.getFirst();
							final DawgState secondTarget = secondOutEdge.getSecond();

							IDawgLetter<LETTER, COLNAMES> intersectionDl = firstDl.intersect(secondDl);

							if (intersectionDl != null && !(intersectionDl instanceof EmptyDawgLetter)) {
								// dawgletters do intersect --> add transition and new state
								final PairDawgState newState = mDawgStateFactory.getOrCreatePairDawgState(
										firstTarget, secondTarget);

								nextStates.add(newState);
								mResultTransitionRelation.put(cs, intersectionDl, newState);
								assert !EprHelpers.areStatesUnreachable(mResultTransitionRelation, mResultInitialState, nextStates);
								assert EprHelpers.isDeterministic(mResultTransitionRelation);
								assert !EprHelpers.hasDisconnectedTransitions(mResultTransitionRelation, 
												mResultInitialState);
							}

							/**
							 * If, in union mode, one of the input dawgs makes a transition that the other cannot make
							 * we make that transition to a special PairDawgState where one of the dawgs is in a sink state
							 */
							if (doUnion) {
								final Set<IDawgLetter<LETTER, COLNAMES>> firstWithoutSecondDls = firstDl.difference(secondDl);
								if (!firstWithoutSecondDls.isEmpty()) {
									final PairDawgState fwsDs = mDawgStateFactory.getOrCreatePairDawgState(firstTarget, false, true);
									nextStates.add(fwsDs);
									for (IDawgLetter<LETTER, COLNAMES> dl : firstWithoutSecondDls) {
										mResultTransitionRelation.put(cs, dl, fwsDs);
										assert !EprHelpers.areStatesUnreachable(mResultTransitionRelation, mResultInitialState, nextStates);
										assert EprHelpers.isDeterministic(mResultTransitionRelation);
										assert !EprHelpers.hasDisconnectedTransitions(mResultTransitionRelation, 
												mResultInitialState);
									}
								}

								final Set<IDawgLetter<LETTER, COLNAMES>> secondWithoutFirstDls = secondDl.difference(firstDl);
								if (!secondWithoutFirstDls.isEmpty()) {
									final PairDawgState swfDs = mDawgStateFactory.getOrCreatePairDawgState(secondTarget, true, false);
									nextStates.add(swfDs);
									for (IDawgLetter<LETTER, COLNAMES> dl : secondWithoutFirstDls) {
										mResultTransitionRelation.put(cs, dl, swfDs);
										assert !EprHelpers.areStatesUnreachable(mResultTransitionRelation, mResultInitialState, nextStates);
										assert EprHelpers.isDeterministic(mResultTransitionRelation);
										assert !EprHelpers.hasDisconnectedTransitions(mResultTransitionRelation, 
												mResultInitialState);
									}
								}
							}
						}
					}
				} else if (doUnion && cs.mSecondIsSink) {
					for (Pair<IDawgLetter<LETTER, COLNAMES>, DawgState> firstOutEdge : 
						mFirstInputDawg.getTransitionRelation().getOutEdgeSet(cs.getFirst())) {

						final PairDawgState ds = mDawgStateFactory.getOrCreatePairDawgState(firstOutEdge.getSecond(), false, true);
						nextStates.add(ds);
						mResultTransitionRelation.put(cs, firstOutEdge.getFirst(), ds);
						assert !EprHelpers.areStatesUnreachable(mResultTransitionRelation, mResultInitialState, nextStates);
						assert EprHelpers.isDeterministic(mResultTransitionRelation);
						assert !EprHelpers.hasDisconnectedTransitions(mResultTransitionRelation, 
								mResultInitialState);
					}
				} else if (doUnion && cs.mFirstIsSink) {
					for (Pair<IDawgLetter<LETTER, COLNAMES>, DawgState> secondOutEdge : 
						mSecondInputDawg.getTransitionRelation().getOutEdgeSet(cs.getSecond())) {
						final PairDawgState ds = mDawgStateFactory.getOrCreatePairDawgState(secondOutEdge.getSecond(), true, false);
						nextStates.add(ds);
						mResultTransitionRelation.put(cs, secondOutEdge.getFirst(), ds);
						assert !EprHelpers.areStatesUnreachable(mResultTransitionRelation, mResultInitialState, nextStates);
						assert EprHelpers.isDeterministic(mResultTransitionRelation);
						assert !EprHelpers.hasDisconnectedTransitions(mResultTransitionRelation, 
								mResultInitialState);
					}
				}
			}
			currentStates = nextStates;
		}
		
		return new Dawg<LETTER, COLNAMES>(mDawgFactory, mFirstInputDawg.getLogger(), 
				mFirstInputDawg.getAllConstants(),  mFirstInputDawg.getColnames(), mResultTransitionRelation, mResultInitialState);
	}
}
