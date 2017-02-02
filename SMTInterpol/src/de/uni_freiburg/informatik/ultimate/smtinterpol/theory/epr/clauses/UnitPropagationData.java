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
package de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.clauses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Clause;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dpll.Literal;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.DawgFactory;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.dawgs.IDawg;
import de.uni_freiburg.informatik.ultimate.smtinterpol.theory.epr.partialmodel.DslBuilder;

/**
 * 
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 */
public class UnitPropagationData {

	public UnitPropagationData(
			Map<ClauseLiteral, IDawg<ApplicationTerm, TermVariable>> finalClauseLitToUnitPoints, 
			DawgFactory<ApplicationTerm, TermVariable> dawgFactory) {

		final List<DslBuilder> quantifiedPropagations = new ArrayList<DslBuilder>();
		final Map<Literal, Clause> groundPropagations = new HashMap<Literal,Clause>();
		
		for (Entry<ClauseLiteral, IDawg<ApplicationTerm, TermVariable>> en : finalClauseLitToUnitPoints.entrySet()) {
			ClauseLiteral cl = en.getKey();
			if (cl instanceof ClauseEprQuantifiedLiteral) {
				ClauseEprQuantifiedLiteral ceql = (ClauseEprQuantifiedLiteral) cl;
				DslBuilder propB = new DslBuilder(
						ceql.getPolarity(), 
						ceql.getEprPredicate(),
						dawgFactory.translateClauseSigToPredSig(
								en.getValue(), 
								ceql.getTranslationFromClauseToEprPredicate(), 
								ceql.getArgumentsAsObjects(),
								ceql.getEprPredicate().getTermVariablesForArguments()),
						ceql,
						en.getValue(),
						false); 
				quantifiedPropagations.add(propB);
			} else {
				IDawg<ApplicationTerm, TermVariable> groundingDawg = finalClauseLitToUnitPoints.get(cl);
				Set<Clause> groundings = cl.getClause().getGroundings(groundingDawg);
				Clause unitGrounding = groundings.iterator().next();
// 				note that the following assert would be wrong (rightfully), because some atoms of the given clause 
// 				may not be known to the DPLLEngine yet
//				assert EprHelpers.verifyUnitClause(unitGrounding, cl.getLiteral(), dawgFactory.getLogger());
				groundPropagations.put(cl.getLiteral(), unitGrounding);
			}
		}
			
		mQuantifiedPropagations = Collections.unmodifiableList(quantifiedPropagations);
		mGroundPropagations = Collections.unmodifiableMap(groundPropagations);
	}

	public final List<DslBuilder> mQuantifiedPropagations;
//	public final List<Pair<Literal, Clause>> mGroundPropagations;
	public final Map<Literal, Clause> mGroundPropagations;
}
