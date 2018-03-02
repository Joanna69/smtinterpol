/*
 * Copyright (C) 2017 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.dawgletters.EmptyDawgLetter;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.dawgletters.IDawgLetter;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.dawgletters.SimpleDawgLetter;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.dawgstates.DawgState;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.util.Pair;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.util.Triple;

public class DawgIterator<LETTER, COLNAMES> implements Iterator<List<LETTER>> {

	private final DeterministicDawgTransitionRelation<DawgState, IDawgLetter<LETTER, COLNAMES>, DawgState> mTransitionRelation;
	private final DawgState mInitialState;
	private final DawgSignature<COLNAMES> mSignature;

	final Stack<DawgPath> mOpenIncompleteDawgPaths = new Stack<DawgPath>();
	final Deque<DawgPath> mOpenCompleteDawgPaths = new ArrayDeque<DawgPath>();
	Iterator<List<LETTER>> mCurrentCompleteDawgPathIterator;
	private final boolean mIsDawgEmpty;
	
	/*
	 * By convention for each instance of DawgIterator we only either call next() or nextDawgPath() (because their path
	 * discovery could interfere)
	 * The following to flags are meant to ensure this.
	 */
	private boolean mDawgPathMode = false;
	private boolean mWordMode = false;


	public DawgIterator(
			DeterministicDawgTransitionRelation<DawgState, IDawgLetter<LETTER, COLNAMES>, DawgState> transitionRelation,
			DawgState initialState,
			DawgSignature<COLNAMES> signature) {
		mTransitionRelation = transitionRelation;
		mInitialState = initialState;
		mSignature = signature;
		
		assert (mTransitionRelation == null) == (mInitialState == null);
		
		mIsDawgEmpty = mTransitionRelation == null;

		/*
		 * initialize the dawg path sets with all the outgoing edges of the initial state
		 */
		if (!mIsDawgEmpty) {
			for (Pair<IDawgLetter<LETTER, COLNAMES>, DawgState> outEdge : 
				mTransitionRelation.getOutEdgeSet(mInitialState)) {
				final DawgPath newPath = new DawgPath(mInitialState, outEdge.getFirst(), outEdge.getSecond());
				if (newPath.isComplete()) {
					mOpenCompleteDawgPaths.addLast(newPath);
				} else {
					mOpenIncompleteDawgPaths.push(newPath);
				}
			}
		}
	}

	@Override
	public boolean hasNext() {
		if (mIsDawgEmpty) {
			return false; 
		}
		if (mCurrentCompleteDawgPathIterator != null && mCurrentCompleteDawgPathIterator.hasNext()) {
			return true;
		}
		while (!mOpenCompleteDawgPaths.isEmpty()) {
			mCurrentCompleteDawgPathIterator = mOpenCompleteDawgPaths.pop().iterator();
			if( mCurrentCompleteDawgPathIterator.hasNext()) {
				return true;
			}
		}
		// we need to search for a new complete DawgPath
		while (true) {
			final DawgPath newCompletePath = lookForNewCompletePath();
			if (newCompletePath == null) {
				return false;
			} else if (!newCompletePath.iterator().hasNext()) {
				// the completed DawgPath is empty (relative to the current AllConstants)
				// --> go through the loop again..
			} else {
				mOpenCompleteDawgPaths.addLast(newCompletePath);
				assert mOpenCompleteDawgPaths.peekLast().iterator().hasNext();
				return true;
			}
		}
	}

	private DawgPath lookForNewCompletePath() {
		while (!mOpenIncompleteDawgPaths.isEmpty()) {
			final DawgPath dawgPath = mOpenIncompleteDawgPaths.pop();
			assert !dawgPath.isComplete();

			for (Pair<IDawgLetter<LETTER, COLNAMES>, DawgState> outEdge : 
				mTransitionRelation.getOutEdgeSet(dawgPath.lastState())) {

				final DawgPath newPath = dawgPath.cons(dawgPath.lastState(), 
						outEdge.getFirst(), 
						outEdge.getSecond());

				if (newPath.isComplete()) {
					return newPath;
				}

				mOpenIncompleteDawgPaths.push(newPath);
			}
		}
		return null;
	}

	@Override
	public List<LETTER> next() {
		assert !mDawgPathMode;
		mWordMode = true;
		/*
		 *  the side effect of the following call to hasNext() is important for the remaining code of this method, 
		 *  because only hasNext completes possibly present incomplete DawgPaths!!
		 */
		if (!hasNext()) {
			throw new AssertionError("Check hasNext() before calling next()!");
		}

		if (mCurrentCompleteDawgPathIterator != null && mCurrentCompleteDawgPathIterator.hasNext()) {
			return mCurrentCompleteDawgPathIterator.next();
		}

		if (!mOpenCompleteDawgPaths.isEmpty()) {
			DawgPath completePath = mOpenCompleteDawgPaths.pop();
			mCurrentCompleteDawgPathIterator = completePath.iterator();
			assert mCurrentCompleteDawgPathIterator.hasNext() : "is there an empty letter on the path??";
			return mCurrentCompleteDawgPathIterator.next();
		}

		assert false : "hasNext should ensure that we don't reach here";

		return null;
	}
	
	public boolean hasNextDawgPath() {
		if (!mOpenCompleteDawgPaths.isEmpty()) {
			return true;
		}

		while (true) {
			final DawgPath newCompletePath = lookForNewCompletePath();
			if (newCompletePath == null) {
				return false;
			} else {
				mOpenCompleteDawgPaths.addLast(newCompletePath);
				return true;
			}
		}
	}
	
	public DawgPath nextDawgPath() {
		assert !mWordMode;
		mDawgPathMode = true;
		/*
		 * side effect needed for discovery of DawgPaths
		 */
		if (!hasNextDawgPath()) {
			throw new AssertionError("Check hasNextDawgPath() before calling nextDawgPath()!");
		}

		return mOpenCompleteDawgPaths.pop();
	}

	class DawgPath implements Iterable<List<LETTER>>{
		
		private final boolean mIsSingleton;

		private final List<Triple<DawgState, IDawgLetter<LETTER, COLNAMES>, DawgState>> mEdges = 
				new ArrayList<Triple<DawgState,IDawgLetter<LETTER,COLNAMES>,DawgState>>();

		/**
		 * Creates a DawgPath of length one. The edge is constructed from the arguments.
		 * @param source
		 * @param letter
		 * @param target
		 */
		DawgPath(DawgState source, IDawgLetter<LETTER, COLNAMES> letter, DawgState target) {
			assert !(letter instanceof EmptyDawgLetter<?, ?>);
			addEdge(source, letter, target);
			mIsSingleton = isLetterSingleton(letter);
		}

		/**
		 * copy constructor
		 */
		private DawgPath(DawgPath original) {
			boolean isSingleton = true;
			for (Triple<DawgState, IDawgLetter<LETTER, COLNAMES>, DawgState> edge : original.mEdges) {
				mEdges.add(edge);
				isSingleton &= isLetterSingleton(edge.getSecond());
				assert !(edge.getSecond() instanceof EmptyDawgLetter<?, ?>);
			}
			mIsSingleton = isSingleton;
		}

		private boolean isLetterSingleton(IDawgLetter<LETTER, COLNAMES> letter) {
			return letter instanceof SimpleDawgLetter<?, ?> 
				&& ((SimpleDawgLetter<LETTER , COLNAMES>) letter).getLetters().size() == 1;
		}

		/**
		 * return true iff this DawgPath ends at a final state
		 * (i.e. it has the length of the corresponding dawg's signature)
		 * 
		 * @return
		 */
		boolean isComplete() {
			return mEdges.size() == mSignature.getNoColumns();
		}

		DawgPath cons(DawgState source, IDawgLetter<LETTER, COLNAMES> letter, DawgState target) {
			DawgPath result = new DawgPath(this);
			result.addEdge(source, letter, target);
			return result;
		}

		private void addEdge(DawgState source, IDawgLetter<LETTER, COLNAMES> letter, DawgState target) {
			mEdges.add(new Triple<DawgState, IDawgLetter<LETTER,COLNAMES>, DawgState>(source, letter, target));
		}

		DawgState lastState() {
			return mEdges.get(mEdges.size() - 1).getThird();

		}
		
		/**
		 * Returns true iff this DawgPath denotes exactly one word.
		 *  .. which is true iff each of its letters is a SimpleDawgLetter with one letter.
		 * @return
		 */
		public boolean isSingletonDawgPath() {
			return mIsSingleton;
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			
			sb.append(mEdges.get(0).getFirst());
			
			for (Triple<DawgState, IDawgLetter<LETTER, COLNAMES>, DawgState> edge : mEdges) {
				sb.append(String.format(" --%s--> %s" , edge.getSecond(), edge.getThird()));
			}
			
			return sb.toString();
		}
		
		
		@Override
		public Iterator<List<LETTER>> iterator() {
			return new Iterator<List<LETTER>>() {
				
				final Stack<ColumnLetterPrefix> mOpenClps = new Stack<ColumnLetterPrefix>();
				{
					List<LETTER> emptyPrefix = Collections.emptyList();
					Iterator<LETTER> letterIt = mEdges.get(0).getSecond()
							.allLettersThatMatch(emptyPrefix, mSignature.getColNameToIndex())
							.iterator();//TODO do something about null/colNamesToIndex
//					assert letterIt.hasNext();
					/*
					 *  it can happen that we have a dawgPath without any words because a complementDawgLetter may be
					 *  empty relative to the current AllConstants (but not absolutely empty, as constants may be added..)
					 */
					if (letterIt.hasNext()) {
						mOpenClps.push(new ColumnLetterPrefix(emptyPrefix, letterIt.next(), letterIt));
					}
				}
				
				List<LETTER> mNextWord;

				@Override
				public boolean hasNext() {
					if (mNextWord != null) {
						return true;
					}
					List<LETTER> newWord = sampleWord();
					if (newWord == null) {
						return false;
					}
					mNextWord = newWord;
					return true;
				}

				@Override
				public List<LETTER> next() {
					if (mNextWord == null) {
						final List<LETTER> result = sampleWord();
						assert result != null : "no more words available, should have been checked via hasNext()";
						assert result.size() == mSignature.getNoColumns();
						return result;
					}
					final List<LETTER> result = mNextWord;
					mNextWord = null;
					assert result.size() == mSignature.getNoColumns();
					return result;
				}
				
				
				List<LETTER> sampleWord() {
					while (!mOpenClps.isEmpty()) {
						final ColumnLetterPrefix currentClp = mOpenClps.pop();

						if (currentClp.getColumnIndex() < mSignature.getNoColumns() - 1) {
							// clp can still be prolonged 
							// --> push the prolonged version on the stack ("horizontal") 
							// --> also push the version with the next letter on the stack, if there is one ("vertical")
							Iterator<LETTER> horiNextLetterIt = mEdges.get(currentClp.getColumnIndex() + 1)
									.getSecond().allLettersThatMatch(currentClp.getPrefix(), mSignature.getColNameToIndex())
									.iterator();//TODO do something about null/colNamesToIndex
//							assert horiNextLetterIt.hasNext() : "do we have an empty dawgLetter?";
							if (horiNextLetterIt.hasNext()) {
								ColumnLetterPrefix horizontalNewClp = 
										new ColumnLetterPrefix(currentClp.getPrefix(), horiNextLetterIt.next(), horiNextLetterIt);
								mOpenClps.push(horizontalNewClp);
							}
							
							if (currentClp.getLetterIterator().hasNext()) {
								final ColumnLetterPrefix verticalNewClp = 
										new ColumnLetterPrefix(
												currentClp.getPrefix().subList(0, currentClp.getPrefix().size() - 1), 
												currentClp.getLetterIterator().next(), currentClp.getLetterIterator());
								mOpenClps.push(verticalNewClp);
							}
						} else {
							// is a full word 
							// --> push the variant with the last letter replaced by the next one and return clp's word
							List<LETTER> resultWord = currentClp.mPrefix;
							if (currentClp.getLetterIterator().hasNext()) {
								ColumnLetterPrefix newClp = 
										new ColumnLetterPrefix(
												currentClp.getPrefix().subList(0, currentClp.getPrefix().size() - 1), 
												currentClp.getLetterIterator().next(), currentClp.getLetterIterator());
								mOpenClps.push(newClp);
							}
							assert resultWord.size() == mSignature.getNoColumns();
							return resultWord;
						}
					}
					return null;
				}

				class ColumnLetterPrefix {
					final List<LETTER> mPrefix;
					final Iterator<LETTER> mLetterIterator;
					
					public ColumnLetterPrefix(List<LETTER> prefix, LETTER letter, Iterator<LETTER> letterIt) {
						mPrefix = new ArrayList<LETTER>(prefix);
						mPrefix.add(letter);
						mLetterIterator = letterIt;
					}

					int getColumnIndex() {
						return mPrefix.size() - 1;
					}
					
					LETTER lastLetter() {
						return mPrefix.get(mPrefix.size() - 1);
					}
					
					List<LETTER> getPrefix() {
						return mPrefix;
					}

					public Iterator<LETTER> getLetterIterator() {
						return mLetterIterator;
					}
				}
			};
		}
	}
}
