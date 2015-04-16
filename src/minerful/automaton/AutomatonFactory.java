package minerful.automaton;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import minerful.automaton.utils.AutomatonUtils;
import minerful.concept.constraint.Constraint;
import minerful.concept.constraint.TaskCharRelatedConstraintsBag;
import minerful.index.LinearConstraintsIndexFactory;

import org.apache.log4j.Logger;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;

public class AutomatonFactory {
	public static final int NO_LIMITS_IN_ACTIONS_FOR_SUBAUTOMATA = 0;
	public static final int NO_TRACE_LENGTH_CONSTRAINT = 0;
	private static Logger logger = Logger.getLogger(AutomatonFactory.class
			.getCanonicalName());
	public static final int THREAD_MAX = 8;

	public static Collection<SubAutomaton> subAutomataFromRegularExpressionsInMultiThreading(
			NavigableMap<Character, Collection<String>> regExpsMap,
			Collection<Character> alphabet) {
		return subAutomataFromRegularExpressionsInMultiThreading(regExpsMap,
				alphabet, NO_LIMITS_IN_ACTIONS_FOR_SUBAUTOMATA);
	}

	public static Collection<SubAutomaton> subAutomataFromRegularExpressionsInMultiThreading(
			NavigableMap<Character, Collection<String>> regExpsMap,
			Collection<Character> alphabet, int maxActions) {
		Collection<String> regExps = null;
		Collection<DimensionalityHeuristicBasedCallableSubAutomataMaker> autoMakers = new ArrayList<DimensionalityHeuristicBasedCallableSubAutomataMaker>(
				regExpsMap.size());
		Collection<SubAutomaton> partialAutomata = new ArrayList<SubAutomaton>(
				alphabet.size());
		ExecutorService executor = Executors.newFixedThreadPool(THREAD_MAX);

		for (Character tIdChr : regExpsMap.keySet()) {
			regExps = new ArrayList<String>();
			regExps.addAll(regExpsMap.get(tIdChr));
			if (maxActions <= AutomatonFactory.NO_LIMITS_IN_ACTIONS_FOR_SUBAUTOMATA) {
				autoMakers
						.add(new DimensionalityHeuristicBasedCallableSubAutomataMaker(
								tIdChr, regExps));
			} else {
				autoMakers
						.add(new DimensionalityHeuristicBasedCallableBriefSubAutomataMaker(
								tIdChr, regExps, maxActions));
			}
		}

		try {
			for (Future<SubAutomaton> resultWithNewAutomaton : executor
					.invokeAll(autoMakers)) {
				partialAutomata.add(resultWithNewAutomaton.get());
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		executor.shutdown();
		return partialAutomata;
	}

	public static Automaton fromRegularExpressionsByDimensionalityHeuristicInMultiThreading(
			AbstractMap<Character, Collection<String>> regExpsMap,
			Collection<Character> alphabet) {
		SortedSet<Automaton> partialAutomata = new TreeSet<Automaton>(
				new DimensionalityHeuristicBasedAutomataIntersector.AutomataAscendingDimensionComparator());

		Automaton processAutomaton = null;

		for (Automaton otherProcessAutomaton : partialAutomata) {
			if (processAutomaton == null) {
				processAutomaton = otherProcessAutomaton;
			} else {
				logger.trace("Intersecting automaton...");
				processAutomaton = processAutomaton
						.intersection(otherProcessAutomaton);
				processAutomaton.minimize();
				logger.trace("Automaton states: "
						+ processAutomaton.getNumberOfStates()
						+ "; automaton transitions: "
						+ processAutomaton.getNumberOfTransitions());
			}
		}

		return processAutomaton;
	}

	public static Automaton fromRegularExpressions(Collection<String> regExps,
			Collection<Character> alphabet, int minLen, int maxLen) {
		boolean traceLengthLimitSet = (minLen != NO_TRACE_LENGTH_CONSTRAINT || maxLen != NO_TRACE_LENGTH_CONSTRAINT);
		Collection<String> regularExpressions = new ArrayList<String>(
				regExps.size() +
				(traceLengthLimitSet ? 2 : 1)
		);
		// limit the alphabet
		regularExpressions.add(AutomatonUtils.createRegExpLimitingTheAlphabet(alphabet));
		regularExpressions.addAll(regExps);
		// limit the minimum and maximum length of runs, if needed 
		if (traceLengthLimitSet) {
			regularExpressions.add(AutomatonUtils.createRegExpLimitingTraceLength(minLen, maxLen));
		}

		return new CallableAutomataMaker(regularExpressions).makeAutomaton();
	}

	public static Automaton fromRegularExpressions(Collection<String> regExps,
			Collection<Character> basicAlphabet) {
		return fromRegularExpressions(regExps, basicAlphabet, NO_TRACE_LENGTH_CONSTRAINT, NO_TRACE_LENGTH_CONSTRAINT);
	}

	@Deprecated
	public static Automaton fromRegularExpressionsDeprecated(
			Collection<String> regExps, Collection<Character> alphabet) {
		// Turn strings into Regular Expressions and attach them
		Automaton processAutomaton = null, nuConstraintAutomaton = null;
		String nuRegExp = null;

		logger.trace("Preparing the automaton...");

		if (regExps.size() > 0) {
			Iterator<String> regExpsIterator = regExps.iterator();
			if (regExpsIterator.hasNext()) {
				processAutomaton = new RegExp(
						AutomatonUtils.createRegExpLimitingTheAlphabet(alphabet))
						.toAutomaton();
				// DEBUGGING!
				// processAutomaton = processAutomaton.intersection(new
				// RegExp(".{1," + alphabet.size() * 2 + "}").toAutomaton());
				// processAutomaton = processAutomaton.intersection(new
				// RegExp(".{1," + ((int)(alphabet.size() * 1.25)) +
				// "}").toAutomaton());
				// processAutomaton = processAutomaton.intersection(new
				// RegExp(".{1,24}").toAutomaton());
				// E.O. DEBUGGING

				while (regExpsIterator.hasNext()) {
					nuRegExp = regExpsIterator.next();

					logger.trace("Intersecting the automaton with the accepting for: "
							+ nuRegExp);

					nuConstraintAutomaton = new RegExp(nuRegExp).toAutomaton();
					processAutomaton = processAutomaton
							.intersection(nuConstraintAutomaton);
					processAutomaton.minimize();

					logger.trace("Automaton states: "
							+ processAutomaton.getNumberOfStates()
							+ "; automaton transitions: "
							+ processAutomaton.getNumberOfTransitions());
				}
			}
			// DEBUGGING!
			// processAutomaton = processAutomaton.intersection(new
			// RegExp("[^a]?[^a]a[^a][^a]?").toAutomaton());
			// E.O. DEBUGGING
		}
		return processAutomaton;
	}


	public static Automaton buildAutomaton(TaskCharRelatedConstraintsBag bag, Collection<Character> basicAlphabet) {
		return buildAutomaton(bag, basicAlphabet, NO_TRACE_LENGTH_CONSTRAINT, NO_TRACE_LENGTH_CONSTRAINT);
	}
	
	public static Automaton buildAutomaton(TaskCharRelatedConstraintsBag bag, Collection<Character> basicAlphabet,
			int minLen, int maxLen) {
		Collection<String> regularExpressions = null;
		Collection<Constraint> constraints = LinearConstraintsIndexFactory.getAllConstraintsSortedByBoundsSupportFamilyConfidenceInterestFactorHierarchyLevel(bag);

		regularExpressions = new ArrayList<String>(constraints.size());
		for (Constraint con : constraints) {
			regularExpressions.add(con.toRegularExpression());
		}

		if (minLen != NO_TRACE_LENGTH_CONSTRAINT || maxLen != NO_TRACE_LENGTH_CONSTRAINT) {
			return AutomatonFactory.fromRegularExpressions(regularExpressions, basicAlphabet, minLen, maxLen);	
		}
		return AutomatonFactory.fromRegularExpressions(regularExpressions, basicAlphabet);
	}
}