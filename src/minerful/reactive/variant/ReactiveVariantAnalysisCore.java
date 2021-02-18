package minerful.reactive.variant;

import minerful.concept.ProcessModel;
import minerful.concept.TaskChar;
import minerful.logparser.LogParser;
import minerful.logparser.LogTraceParser;
import minerful.reactive.automaton.SeparatedAutomatonOfflineRunner;
import minerful.reactive.checking.MegaMatrixMonster;
import minerful.reactive.checking.ReactiveCheckingOfflineQueryingCore;
import minerful.reactive.params.JanusCheckingCmdParameters;
import minerful.reactive.params.JanusVariantCmdParameters;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * Class to organize the variant analysis
 */
public class ReactiveVariantAnalysisCore {

    protected static Logger logger;
    private final LogParser logParser_1;  // original log1 parser
    private ProcessModel processSpecification1;  // original set of constraints mined from log1
    private final LogParser logParser_2; // original log2 parser
    private ProcessModel processSpecification2;  // original set of constraints mined from log2
    private final JanusVariantCmdParameters janusVariantParams;  // input parameter of the analysis

    private Map<String, Map<String, Float>> lCoded; // encoded log for efficient permutations
    private float[][] lCodedIndex; // encoded log for efficient permutations
    private Set mTot;  // total set of constraints to analyse, i.e., union of process specification 1 adn 2
    private ProcessModel processSpecificationUnion;  // total set of constraints to analyse, i.e., union of process specification 1 adn 2
    private Set mDiffs;  // initial differences of specification 1 and 2 to be checked through the analysis
    private ProcessModel processSpecificationDifference;  // initial differences of specification 1 and 2 to be checked through the analysis
    private double pValueThreshold;
    private static final String MEASURE = "Confidence";  // expose measure selection to the user
    private static final int MEASURE_INDEX = 1;
    private static final double MEASURE_THRESHOLD = 0.8;

    private Map<Integer, String> indexToTraceMap;
    private Map<String, Integer> traceToIndexMap;
    private Map<Integer, String> indexToConstraintMap;
    private Map<String, Integer> constraintToIndexMap;

    private static class PermutationResult {
        float[][] result1; // permutation test results for first group
        float[][] result2; // permutation test results for second group
        String[] constraints;  // list of constraint names
        float[] test;  // test statistics for the difference of the groups, mind that constraints and permutations indices as swapped wrt the permutation results

        public PermutationResult(float[][] result1, float[][] result2, String[] constraints) {
            this.result1 = result1;
            this.result2 = result2;
            this.constraints = constraints;
            this.test = new float[constraints.length];
        }

        public PermutationResult(float[][] result1, float[][] result2) {
            this.result1 = result1;
            this.result2 = result2;
        }
    }

    {
        if (logger == null) {
            logger = Logger.getLogger(ReactiveCheckingOfflineQueryingCore.class.getCanonicalName());
        }
    }

    /**
     * Constructor
     *
     * @param logParser_1
     * @param logParser_2
     * @param janusVariantParams
     */
    public ReactiveVariantAnalysisCore(LogParser logParser_1, ProcessModel processSpecification1, LogParser logParser_2, ProcessModel processSpecification2, JanusVariantCmdParameters janusVariantParams) {
        this.logParser_1 = logParser_1;
        this.processSpecification1 = processSpecification1;
        this.logParser_2 = logParser_2;
        this.processSpecification2 = processSpecification2;
        this.janusVariantParams = janusVariantParams;
        this.pValueThreshold = 0.05;
    }

    public ReactiveVariantAnalysisCore(LogParser logParser_1, ProcessModel processSpecification1, LogParser logParser_2, ProcessModel processSpecification2, JanusVariantCmdParameters janusVariantParams, double pValueThreshold) {
        this(logParser_1, processSpecification1, logParser_2, processSpecification2, janusVariantParams);
        this.pValueThreshold = pValueThreshold;
    }

    /**
     * Launcher for variant analysis of two logs
     *
     * @return
     */
    public Map<String, Float> check() {
        logger.info("Variant Analysis start");
//        PREPROCESSING
        double before = System.currentTimeMillis();
        //        1. Models differences
        setModelsDifferences(processSpecification1, processSpecification2);
        //        2. Models Union (total set of rules to check afterwards)
        setModelsUnion(processSpecification1, processSpecification2);
        //        3. Encode log (create efficient log structure for the permutations)
        //        4. Precompute all possible results for the Encoded Log
        encodeLogs(logParser_1, logParser_2, processSpecificationUnion);
        encodeLogsIndex(lCoded);
        double after = System.currentTimeMillis();
        logger.info("Preprocessing time: " + (after - before));

//        PERMUTATION TEST
        before = System.currentTimeMillis();
        logger.info("Permutations processing...");
//        PermutationResult pRes = permuteResults(lCoded, janusVariantParams.nPermutations, true);
        PermutationResult pRes = permuteResultsIndex(lCodedIndex, janusVariantParams.nPermutations, true);
        logger.info("Significance testing...");
//        Map<String, Double> results = significanceTest(pRes, pValueThreshold);
        Map<String, Float> results = significanceTestIndex(pRes, pValueThreshold);
        after = System.currentTimeMillis();
        logger.info("Permutation test time: " + (after - before));
        return results;
    }

    /**
     * Checks the significance of the permutation test results
     *
     * @param pRes
     * @param pValueThreshold
     * @return
     */
    private Map<String, Float> significanceTestIndex(PermutationResult pRes, double pValueThreshold) {
        Map<String, Float> result = new HashMap<String, Float>();
        int nConstraints = processSpecificationUnion.howManyConstraints();
        int nPermutations = pRes.result1.length;
        pRes.test = new float[nConstraints];
        for (int cIndex = 0; cIndex < nConstraints; cIndex++) {
//            double initialreference = pRes.result1[0][cIndex] - pRes.result2[0][cIndex];  // for NEGATIVE/POSITIVE DISTANCE
            double initialreference = Math.abs(pRes.result1[0][cIndex] - pRes.result2[0][cIndex]); // for ABSOLUTE DISTANCE
            boolean negRef = initialreference < 0;
            pRes.test[cIndex] += 1.0;
            for (int permutation = 1; permutation < nPermutations; permutation++) {
//                NEGATIVE/POSITIVE DISTINCTION:
//                consider the difference in the permutation only if it is extreme in the same sign of the reference difference
//                if (negRef) {
//                    if (pRes.result1[permutation][cIndex] - pRes.result2[permutation][cIndex] <= initialreference) {
//                        pRes.test[cIndex] += 1.0;
//                    }
//                } else {
//                    if (pRes.result1[permutation][cIndex] - pRes.result2[permutation][cIndex] >= initialreference) {
//                        pRes.test[cIndex] += 1.0;
//                    }
//                }
//                ABSOLUTE DISTANCE:
//                consider the absolute difference, regardless of the direction
                if (Math.abs(pRes.result1[permutation][cIndex] - pRes.result2[permutation][cIndex]) >= initialreference) {
                    pRes.test[cIndex] += 1.0;
                }
            }
            pRes.test[cIndex] = pRes.test[cIndex] / nPermutations;
//            if (pRes.test[cIndex]<=pValueThreshold) System.out.println(pRes.constraints[cIndex] + " p_vale=" + pRes.test[cIndex]);
//            System.out.println(pRes.constraints[cIndex] + " p_vale=" + pRes.test[cIndex]);
            result.put(indexToConstraintMap.get(cIndex), pRes.test[cIndex]);
        }
        return result;
    }

    /**
     * Permutation test in which is taken the encoded results.
     *
     * @param lCodedIndex
     * @param nPermutations
     * @param nanCheck
     * @return
     */
    private PermutationResult permuteResultsIndex(float[][] lCodedIndex, int nPermutations, boolean nanCheck) {
        int nConstraints = processSpecificationUnion.howManyConstraints();
        float[][] result1 = new float[nPermutations][nConstraints];
        float[][] result2 = new float[nPermutations][nConstraints];

        int log1Size = logParser_1.length();
        int log2Size = logParser_2.length();
        List<String> permutableTracesList = new LinkedList<>();
        for (Iterator<LogTraceParser> it = logParser_1.traceIterator(); it.hasNext(); ) {
            permutableTracesList.add(it.next().printStringTrace());
        }
        for (Iterator<LogTraceParser> it = logParser_2.traceIterator(); it.hasNext(); ) {
            permutableTracesList.add(it.next().printStringTrace());
        }
        List<Integer> permutableTracesIndexList = new LinkedList<>();
        for (String t : permutableTracesList) {
            permutableTracesIndexList.add(traceToIndexMap.get(t));
        }

        int step = 25;
        for (int i = 0; i < nPermutations; i++) {
            if (i % step == 0)
                System.out.print("\rPermutation: " + i + "/" + nPermutations);  // Status counter "current trace/total trace"

            for (int c = 0; c < nConstraints; c++) {
                int traceIndex = -1;
                int nanTraces1 = 0;
                int nanTraces2 = 0;
                for (int t : permutableTracesIndexList) {
                    traceIndex++;
                    if (traceIndex < log1Size) {
                        if (nanCheck & Float.isNaN(lCodedIndex[t][c])) {
                            nanTraces1++;
                            continue; // TODO expose in input
                        }
                        result1[i][c] += lCodedIndex[t][c];
                    } else {
                        if (nanCheck & Float.isNaN(lCodedIndex[t][c])) {
                            nanTraces2++;
                            continue; // TODO expose in input
                        }
                        result2[i][c] += lCodedIndex[t][c];
                    }
                }
                result1[i][c] = result1[i][c] / (log1Size - nanTraces1);
                result2[i][c] = result2[i][c] / (log2Size - nanTraces2);
            }
//            permutation "0" are the original logs
            Collections.shuffle(permutableTracesIndexList);
        }
        System.out.print("\rPermutation: " + nPermutations + "/" + nPermutations);
        System.out.println();

        return new PermutationResult(result1, result2);
    }

    /**
     * Transform the encoded map into a matrix where traces and constraints are referred by indices.
     * compute the encoding and return the reference mappings
     *
     * @param lCoded
     */
    private void encodeLogsIndex(Map<String, Map<String, Float>> lCoded) {
        indexToTraceMap = new HashMap<>();
        traceToIndexMap = new HashMap<>();
        indexToConstraintMap = new HashMap<>();
        constraintToIndexMap = new HashMap<>();

        lCodedIndex = new float[lCoded.size()][processSpecificationUnion.howManyConstraints()]; // lCodedIndex[trace index][constraint index]

        int cIndex = 0;
        for (String c : lCoded.values().iterator().next().keySet()) {
            indexToConstraintMap.put(cIndex, c);
            constraintToIndexMap.put(c, cIndex);
            cIndex++;
        }
        int traceIndex = 0;
        for (String t : lCoded.keySet()) {
            indexToTraceMap.put(traceIndex, t);
            traceToIndexMap.put(t, traceIndex);
            traceIndex++;
        }

        for (int t = 0; t < lCodedIndex.length; t++) {
            for (int c = 0; c < lCodedIndex[0].length; c++) {
                lCodedIndex[t][c] = lCoded.get(indexToTraceMap.get(t)).get(indexToConstraintMap.get(c));
            }
        }

    }


    /**
     * Precompute the evaluation and encode a map where each distinct trace is linked to all the constraints measumentents
     *
     * @param logParser
     * @param model
     * @return
     */
    private Map<String, Map<String, Float>> encodeLog(LogParser logParser, ProcessModel model) {
        Map<String, Map<String, Float>> result = new HashMap();
        JanusCheckingCmdParameters janusCheckingParams = new JanusCheckingCmdParameters(false, 0, true, false);
        ReactiveCheckingOfflineQueryingCore reactiveCheckingOfflineQueryingCore = new ReactiveCheckingOfflineQueryingCore(
                0, logParser, janusCheckingParams, null, logParser.getTaskCharArchive(), null, model.bag);
        double before = System.currentTimeMillis();
        MegaMatrixMonster measures = reactiveCheckingOfflineQueryingCore.check();
        double after = System.currentTimeMillis();

        logger.info("Total KB checking time: " + (after - before));

//      compute only the desired measure
        float[][] tracesMeasure = measures.computeTracesSingleMeasure(MEASURE, janusCheckingParams.nanTraceSubstituteFlag, janusCheckingParams.nanTraceSubstituteValue);

        int currentTrace = 0;
        for (Iterator<LogTraceParser> it = logParser.traceIterator(); it.hasNext(); ) {
            LogTraceParser tr = it.next();
            String stringTrace = tr.printStringTrace();
            if (!result.containsKey(stringTrace)) {
                result.put(stringTrace, new HashMap<String, Float>());
                int cIndex = 0;
                for (SeparatedAutomatonOfflineRunner c : measures.getAutomata()) {
                    result.get(stringTrace).put(
                            c.toString(),
                            tracesMeasure[currentTrace][cIndex]
//                            measures.getSpecificMeasure(currentTrace, cIndex, MEASURE_INDEX)
                    );
                    cIndex++;
                }
            }
            currentTrace++;
        }

        return result;
    }

    /**
     * Encode the input traces for efficient permutation.
     * the result is a Map where the keys are the hash of the traces and the content in another map with key:value as constrain:measure.
     * In this way we check only here the constraints in each trace and later we permute only the results
     *
     * @param logParser_1
     * @param logParser_2
     * @param model
     * @return
     */
    private Map<String, Map<String, Float>> encodeLogs(LogParser logParser_1, LogParser logParser_2, ProcessModel model) {
        lCoded = new HashMap<String, Map<String, Float>>();
        lCoded.putAll(encodeLog(logParser_1, model));
        lCoded.putAll(encodeLog(logParser_2, model));
        return lCoded;
    }

    /**
     * Computes the union of the two models. It store the results in mTot and returns it in output
     *
     * @param processSpecification1
     * @param processSpecification2
     * @return
     */
    private Set setModelsUnion(ProcessModel processSpecification1, ProcessModel processSpecification2) {
        mTot = new HashSet();
        mTot.addAll(processSpecification1.getAllConstraints());
        mTot.addAll(processSpecification2.getAllConstraints());
        processSpecificationUnion = ProcessModel.union(processSpecification1, processSpecification2);
        return mTot;
    }


    /**
     * Computes the differences of the two models. It store the results in mDiffs and returns it in output
     *
     * @param processSpecification1
     * @param processSpecification2
     * @return
     */
    private Set setModelsDifferences(ProcessModel processSpecification1, ProcessModel processSpecification2) {
        mDiffs = new HashSet<ProcessModel>();
        HashSet<ProcessModel> temp1 = new HashSet(processSpecification1.getAllConstraints());
        HashSet<ProcessModel> temp2 = new HashSet(processSpecification2.getAllConstraints());
        temp1.removeAll(processSpecification2.getAllConstraints());
        temp2.removeAll(processSpecification1.getAllConstraints());
        mDiffs.addAll(temp1);
        mDiffs.addAll(temp2);

        processSpecificationDifference = ProcessModel.difference(processSpecification1, processSpecification2);
        return mDiffs;
    }

    private Map<String, Float> getMeasurementsOfOneVariant(boolean nanCheck, LogParser logParser) {
        Map<String, Float> result = new HashMap<>(); // constraint->measurement
        int logSize = logParser.length();
        List<String> permutableTracesList = new LinkedList<>();
        for (Iterator<LogTraceParser> it = logParser.traceIterator(); it.hasNext(); ) {
            permutableTracesList.add(it.next().printStringTrace());
        }
        List<Integer> permutableTracesIndexList = new LinkedList<>();
        for (String t : permutableTracesList) {
            permutableTracesIndexList.add(traceToIndexMap.get(t));
        }
        int nConstraints = processSpecificationUnion.howManyConstraints();
        float[] result1 = new float[nConstraints];
        for (int c = 0; c < nConstraints; c++) {
            int nanTraces = 0;
            for (int t : permutableTracesIndexList) {
                if (nanCheck & Float.isNaN(lCodedIndex[t][c])) {
                    nanTraces++;
                    continue; // TODO expose in input
                }
                result1[c] += lCodedIndex[t][c];

            }
            result1[c] = result1[c] / (logSize - nanTraces);
        }

        for (int c = 0; c < nConstraints; c++) {
            result.put(indexToConstraintMap.get(c), result1[c]);
        }
        return result;
    }

    public Map<String, Float> getMeasurementsVar1(boolean nanCheck) {
        return getMeasurementsOfOneVariant(nanCheck, logParser_1);
    }

    public Map<String, Float> getMeasurementsVar2(boolean nanCheck) {
        return getMeasurementsOfOneVariant(nanCheck, logParser_2);
    }

    private String decodeConstraint(String encodedConstraint, Map<Character, TaskChar> translationMap) {
        StringBuilder resultBuilder = new StringBuilder();
        String constraint = encodedConstraint.substring(0, encodedConstraint.indexOf("("));
        resultBuilder.append(constraint);
        String[] encodedVariables = encodedConstraint.substring(encodedConstraint.indexOf("(")).replace("(", "").replace(")", "").split(",");
        resultBuilder.append("(");
        String decodedActivator = translationMap.get(encodedVariables[0].charAt(0)).toString();
        resultBuilder.append(decodedActivator);
        if (encodedVariables.length > 1) { //constraints with 2 variables
            resultBuilder.append(",");
            String decodedTarget = translationMap.get(encodedVariables[1].charAt(0)).toString();
            resultBuilder.append(decodedTarget);
        }
        resultBuilder.append(")");
        return resultBuilder.toString();
    }


}
