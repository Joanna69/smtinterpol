package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.EprHelpers;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.EprTheory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.clauses.ClauseLiteral;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.partialmodel.DecideStackLiteral;

public class DawgFactory<LETTER, COLNAMES> {
	
	EprTheory mEprTheory;
	Set<LETTER> mAllConstants;
	
	public DawgFactory(Set<LETTER> allConstants, EprTheory eprTheory) {
		mEprTheory = eprTheory;
		mAllConstants = allConstants;
	}

//	public IDawg<LETTER, COLNAMES> createEmptyDawg(COLNAMES[] termVariables) {
	public IDawg<LETTER, COLNAMES> createEmptyDawg(SortedSet<COLNAMES> termVariables) {
		assert termVariables != null;
		//TODO freeze the current allConstants set, here?? or can it just change transparently?? 
		return new NaiveDawg<LETTER, COLNAMES>(termVariables, mAllConstants);
	}

	public IDawg<LETTER, COLNAMES> createEmptyDawg(int arity) {
		assert false : "TODO: implement";
		return null;
	}

	/**
	 * Creates and returns a Dawg that accepts all words in Sigma^n.
	 * (where n = termVariables.length)
	 *
	 * @param termVariables
	 * @return
	 */
	public IDawg<LETTER, COLNAMES> createFullDawg(SortedSet<COLNAMES> termVariables) {
		assert termVariables != null;
		return new NaiveDawg<LETTER, COLNAMES>(termVariables, mAllConstants).complement();
	}

	public IDawg<LETTER, COLNAMES> copyDawg(IDawg<LETTER, COLNAMES> dawg) {
		NaiveDawg<LETTER, COLNAMES> nd = (NaiveDawg<LETTER, COLNAMES>) dawg;
		return new NaiveDawg<LETTER, COLNAMES>(nd);
	}
	
	
	/**
	 * Returns a dawg that is the same as the input dawg, but the columns have been renamed according 
	 * to the given map.
	 * Note that the map may introduce repetitions in the column names.
	 *   For example the signature of dawg might be (u, v, w), and the map might be [u -> x, v -> x, w -> y].
	 *   The consequence is that the signature of the new dawg will be (x, y). We will only take points
	 *   from the input dawg that have the same entry for u and v.
	 *     (select + project)
	 *     
	 * 
	 * @param dawg a dawg whose column names are to be changed
	 * @param translation map that gives every column a new name
	 * @return
	 */
	public IDawg<LETTER, COLNAMES> renameColumnsOfDawg(
//			IDawg<LETTER, COLNAMES> dawg, DawgTranslation<COLNAMES> translation) {
			IDawg<LETTER, COLNAMES> dawg, Map<COLNAMES, COLNAMES> translation) {
		NaiveDawg<LETTER, COLNAMES> nd = (NaiveDawg<LETTER, COLNAMES>) dawg;
		return new NaiveDawg<LETTER, COLNAMES>(nd, translation);
	}

	public IDawg<LETTER, COLNAMES> createJoinDawg(Map<ClauseLiteral, DecideStackLiteral> mHistory,
			ClauseLiteral mWatchedLiteral) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Join two dawgs where the column labels are just the position -- i.e., basically ignore the column names..
	 * @param points
	 * @param dawg
	 * @return
	 */
	public IDawg<LETTER, COLNAMES> joinByPosition(IDawg<LETTER, COLNAMES> points,
			IDawg<LETTER, COLNAMES> dawg) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Computes a Dawgs that represents a natural join on two Dawgs. 
	 * Also allows to give a renaming for the columns second dawg that is applied before the join.
	 * The column names taken for the natural join are the COLNAMES of the dawgs.
	 * 
	 * @param d1
	 * @param d2
	 * @param translation is applied to d2. Allows to rename its columns before the join.
	 * @return
	 */
	public IDawg<LETTER, COLNAMES> join(IDawg<LETTER, COLNAMES> d1,
			IDawg<LETTER, COLNAMES> d2,
			Map<COLNAMES, COLNAMES> translation) {
		/*
		 * - align the column names by the translation
		 * - call normal join
		 */
		IDawg<LETTER, COLNAMES> rnd2 = 
				 renameColumnsOfDawg(d2, translation);
		
		return join(d1, rnd2);
	}

	/**
	 * Computes a Dawg that represents a natural join on the two input Dawgs. 
	 * 
	 * @param d1
	 * @param d2
	 * @return
	 */
	public IDawg<LETTER, COLNAMES> join(IDawg<LETTER, COLNAMES> d1, IDawg<LETTER, COLNAMES> d2) {
//		/*
//		 * - make a new joint signature
//		 * - create a dawg over the new signature
//		 * - add the points
//		 */
//
//		for (COLNAMES cn : d2.getColnames()) {
//			if (!newSignature.contains(cn)) {
//				newSignature.add(cn);
//			}
//		}
//		
//		// right now: work with NaiveDawgs
//		IDawg<LETTER, COLNAMES> newDawg = 
//				(NaiveDawg<LETTER, COLNAMES>) createEmptyDawg(newSignature);
//		
//		newDawg.addAllWithSubsetSignature(d1);
//		newDawg.addAllWithSubsetSignature(d2);
//		return newDawg;
		return d1.join(d2);
	}

	/**
	 * Create a dawg from the input dawg where only the points are selected that match the given mapping.
	 * The mappings says for some columns what value they must have.
	 * (this is a special version of the normal select operation sigma_phi, where phi has the form x=a, 
	 *  for a column name x and a letter a)
	 * 
	 * @param dawg
	 * @param selectMap (possibly) restricts some COLNAMES in the signature to only one LETTER
	 * @return
	 */
	@Deprecated 
	public IDawg<ApplicationTerm, TermVariable> select(IDawg<ApplicationTerm, TermVariable> dawg,
			Map<TermVariable, ApplicationTerm> selectMap) {
		return dawg.select(selectMap);
	}

	public IDawg<LETTER, COLNAMES> renameSelectAndProject(
			IDawg<LETTER, COLNAMES> other, Map<COLNAMES, Object> translation) {

		
		COLNAMES colNamesInstance = other.getColnames().first();
		
		// the signature of the new dawg has only the non-duplicated colnames 
		// and also omits constants (i.e. objects not of the type COLNAMES)
		SortedSet<COLNAMES> newSignature = new TreeSet<COLNAMES>(EprHelpers.getColumnNamesComparator());
		for (Object o : translation.values()) {
			if (colNamesInstance.getClass().isInstance(o)) {
				newSignature.add((COLNAMES) o);
			}
		}
		
		Map<COLNAMES, Integer> newSigColNamesToIndex = EprHelpers.computeColnamesToIndex(newSignature);
		
		NaiveDawg<LETTER, COLNAMES> otherNd = (NaiveDawg<LETTER, COLNAMES>) other;
		Set<List<LETTER>> newBacking = new HashSet<List<LETTER>>();
		for (List<LETTER> point : otherNd.mBacking) {

			List<LETTER> newPoint = new ArrayList<LETTER>(newSignature.size());
			// set up the new point (need to fill it, or List.set(..) won't work)
			for (int i = 0; i < newSignature.size(); i++) {
				newPoint.add(null);
			}
			
			// tracks if a column name has been seen, and what letter it had been assigned (does select_x=x so to say)
			Map<COLNAMES, LETTER> variableAssignmentInPoint = new HashMap<COLNAMES, LETTER>();
			
			Iterator<COLNAMES> ptColIt = other.getColnames().iterator();
			for (int i = 0; i < point.size(); i++) {
				LETTER ptLtr = point.get(i);
				COLNAMES ptColnameInOldSig = ptColIt.next();

				Object trans = translation.get(ptColnameInOldSig);
				if (colNamesInstance.getClass().isInstance(trans)) {
					COLNAMES ptColnameInNewSig = (COLNAMES) trans;
					
					LETTER vaip = variableAssignmentInPoint.get(ptColnameInNewSig);
					if (vaip != null && vaip != ptLtr) {
						// violation of select_x=x
						newPoint = null;
						break;
					} else {
						newPoint.set(newSigColNamesToIndex.get(ptColnameInNewSig), ptLtr);
						// store that at the current oldColumn-name we used letter ptLtr
						variableAssignmentInPoint.put(ptColnameInOldSig, ptLtr);
					}
					
				} else {
					// we have a constant in the column where this letter in the point is supposed to "land"
					// select_x=c so to say..
					if (ptLtr.equals(trans)) {
						// the constant matches go on (add nothing to the new point)
					} else {
						// point is filtered by the select that checks the constants
						newPoint = null;
						break;
					}
				}

			}
			if (newPoint != null) {
				newBacking.add(newPoint);
			}
		}

		NaiveDawg<LETTER, COLNAMES> result = new NaiveDawg<LETTER, COLNAMES>(newSignature, mAllConstants, newBacking);
		return result;
	}

	


//	public IDawg<LETTER, COLNAMES> translateDawg(IDawg<LETTER, COLNAMES> dawg,
//			DawgTranslation<COLNAMES> translation) {
//		IDawg<LETTER, COLNAMES> nd = createEmptyDawg(translation.getNewSignature());
//		nd.addAll(dawg);
//		if (nd instanceof NaiveDawg<?, ?>) {
//			nd.
//		}
//		return null;
//	}
	
	/**
	 *  Some tests for the DawgFactory
	 * @param args
	 */
	public static void main(String[] args) {
		
		// setup 
		
		Set<Character> constants = new HashSet<Character>();
		constants.add('a');
		constants.add('b');
		constants.add('c');
		
		
		DawgFactory<Character, String> df = 
				new DawgFactory<Character, String>(constants, null);
		
		SortedSet<String> colNames1 = new TreeSet<String>();
		colNames1.add("one");
		colNames1.add("two");
//		colNames1.add("three");
//		colNames1.add("four");
//		colNames1.add("five");
		
		SortedSet<String> colNames2 = new TreeSet<String>();
		colNames2.add("alpha");
		colNames2.add("beta");
		colNames2.add("gamma");
//		colNames1.add("delta");


		IDawg<Character, String> d1 = df.createFullDawg(colNames1);
		IDawg<Character, String> d2 = df.createEmptyDawg(colNames2);
		List<Character> word1 = new ArrayList<Character>();
		word1.add('a');
		word1.add('a');
		word1.add('b');
		d2.add(word1);
		
		List<Character> word2 = new ArrayList<Character>();
		word2.add('a');
		word2.add('b');
		word2.add('b');
		d2.add(word2);

		
		
		Map<String, Object> translation3 = new HashMap<String, Object>();
		translation3.put("alpha", "bla");
		translation3.put("beta", "bla");
		translation3.put("gamma", "blub");

		IDawg<Character, String> d3 = df.renameSelectAndProject(d2, translation3);
		
		Map<String, Object> translation4 = new HashMap<String, Object>();
		translation4.put("alpha", "bla");
		translation4.put("beta", "bla");
		translation4.put("gamma", 'a');

		IDawg<Character, String> d4 = df.renameSelectAndProject(d2, translation4);

		
		System.out.println(d1);
		System.out.println(d2);
		System.out.println(d3);
		System.out.println(d4);
		
	}
	
}
