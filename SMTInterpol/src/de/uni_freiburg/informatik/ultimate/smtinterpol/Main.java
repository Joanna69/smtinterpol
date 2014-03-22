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
package de.uni_freiburg.informatik.ultimate.smtinterpol;

import de.uni_freiburg.informatik.ultimate.logic.LoggingScript;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.smtinterpol.aiger.AIGERFrontEnd;
import de.uni_freiburg.informatik.ultimate.smtinterpol.dimacs.DIMACSParser;
import de.uni_freiburg.informatik.ultimate.smtinterpol.smtlib.SMTLIBParser;
import de.uni_freiburg.informatik.ultimate.smtinterpol.smtlib2.SMTLIB2Parser;

/**
 * Generic frontend that dispatches to the different parsers supported by
 * SMTInterpol.
 * @author Juergen Christ
 */
public final class Main {
	
	private Main() {
		// Hide constructor
	}

	private static void usage() {
		System.err.println("USAGE:");
		System.err.println(
"smtinterpol [-transform <output>] [-script <class>] [-no-success] [-q] [-v] [-t <num>] [-r <num>] [-smt2] [-smt] [-d] [inputfile]");// NOCHECKSTYLE
		System.err.println("\t-transform <output>\t\tTransform to smtlib2 file.");// NOCHECKSTYLE
		System.err.println("\t-script <class>\t\tUse different script.");
		System.err.println("\t-no-success\t\tDon't print success messages.");
		System.err.println("\t-q\t\tRun in quiet mode");
		System.err.println("\t-v\t\tRun in verbose mode");
		System.err.println("\t-t <num>\tSet timeout to <num> seconds");
		System.err.println("\t-r <num>\tSet random seed to <num>");
		System.err.println("\t-smt2\t\tParse input as SMTLIB 2 script");
		System.err.println("\t-smt\t\tParse input as SMTLIB benchmark");
		System.err.println("\t-d\t\tParse input as DIMACS benchmark");
	}
	
	/**
	 * @param param Command line arguments.
	 */
	public static void main(String[] param) throws Exception {
		String verbosity = null;
		String timeout = null;
		String seed = null;
		IParser parser = new SMTLIB2Parser();
		Script solver = null;
		boolean printSuccess = true;
		int paramctr = 0;
        while (paramctr < param.length
        		&& param[paramctr].startsWith("-")) {
        	if (param[paramctr].equals("--")) {
        		paramctr++;
        		break;
        	} else if (param[paramctr].equals("-transform")
     			   && paramctr + 1 < param.length) {
    			paramctr++;
        		solver = new LoggingScript(param[paramctr], true);
        	} else if (param[paramctr].equals("-script")
     			   && paramctr + 1 < param.length) {
     			paramctr++;
     			Class<?> scriptClass = Class.forName(param[paramctr]);
     			solver = (Script) scriptClass.newInstance();
        	} else if (param[paramctr].equals("-no-success")) {
        		printSuccess = false;
        	} else if (param[paramctr].equals("-v")) {
        		verbosity = "5";
        	} else if (param[paramctr].equals("-q")) {
        		verbosity = "2";
        	} else if (param[paramctr].equals("-t")
        			&& ++paramctr < param.length) {
       			timeout = param[paramctr];
        	} else if (param[paramctr].equals("-r")
        			&& ++paramctr < param.length) {
       			seed = param[paramctr];
        	} else if (param[paramctr].equals("-smt2")) {
        		parser = new SMTLIB2Parser();
        	} else if (param[paramctr].equals("-smt")) {
        		parser = new SMTLIBParser();
        	} else if (param[paramctr].equals("-d")) {
        		parser = new DIMACSParser();
        	} else if (param[paramctr].equals("-a")) {
        		parser = new AIGERFrontEnd();
        	} else if (param[paramctr].equals("-trace")) {
        		verbosity = "6";
        	} else {
        		usage();
        		return;
        	}
        	++paramctr;
        }
        String filename = null;
		if (paramctr < param.length)
			filename = param[paramctr++];
		if (paramctr != param.length) {
			usage();
			return;
		}
		int exitCode = parser.run(
			solver, filename, printSuccess, verbosity, timeout, seed);
		System.exit(exitCode);
	}

}
