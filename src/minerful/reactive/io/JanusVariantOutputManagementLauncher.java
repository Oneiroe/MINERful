package minerful.reactive.io;

import minerful.MinerFulOutputManagementLauncher;
import minerful.concept.TaskChar;
import minerful.concept.TaskCharArchive;
import minerful.concept.constraint.Constraint;
import minerful.params.SystemCmdParameters;
import minerful.params.ViewCmdParameters;
import minerful.reactive.checking.Measures;
import minerful.reactive.checking.MegaMatrixMonster;
import minerful.reactive.params.JanusVariantCmdParameters;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Class to handle the output of Janus
 */
public class JanusVariantOutputManagementLauncher extends MinerFulOutputManagementLauncher {

    /**
     * reads the terminal input parameters and launch the proper output functions
     *
     * @param variantResults
     * @param additionalCnsIndexedInfo
     * @param varParams
     * @param viewParams
     * @param systemParams
     * @param alphabet
     * @param measurementsSpecification1
     * @param measurementsSpecification2
     */
    public void manageVariantOutput(Map<String, Float> variantResults, NavigableMap<Constraint, String> additionalCnsIndexedInfo, JanusVariantCmdParameters varParams, ViewCmdParameters viewParams, SystemCmdParameters systemParams, TaskCharArchive alphabet, Map<String, Float> measurementsSpecification1, Map<String, Float> measurementsSpecification2) {
        File outputFile = null;

        // ************* CSV
        if (varParams.outputCvsFile != null) {
            outputFile = retrieveFile(varParams.outputCvsFile);
            logger.info("Saving variant analysis result as CSV in " + outputFile + "...");
            double before = System.currentTimeMillis();

            exportVariantResultsToCSV(variantResults, outputFile, varParams, alphabet, measurementsSpecification1, measurementsSpecification2);

            double after = System.currentTimeMillis();
            logger.info("Total CSV serialization time: " + (after - before));
        }

        if (viewParams != null && !viewParams.suppressScreenPrintOut) {
            printVariantResultsToScreen(variantResults, varParams, alphabet, measurementsSpecification1, measurementsSpecification2);
        }

        // ************* JSON
        if (varParams.outputJsonFile != null) {
            outputFile = retrieveFile(varParams.outputJsonFile);
            logger.info("Saving variant analysis result as JSON in " + outputFile + "...");

            double before = System.currentTimeMillis();

//            TODO
            logger.info("JSON output yet not implemented");

            double after = System.currentTimeMillis();
            logger.info("Total JSON serialization time: " + (after - before));
        }

    }

    private void printVariantResultsToScreen(Map<String, Float> variantResults, JanusVariantCmdParameters varParams, TaskCharArchive alphabet, Map<String, Float> measurementsSpecification1, Map<String, Float> measurementsSpecification2) {
        //		header row
        System.out.println("--------------------");
        System.out.println("relevant constraints differences");
        System.out.println("CONSTRAINT : P_VALUE");

        Map<Character, TaskChar> translationMap = alphabet.getTranslationMapById();
        for (String constraint : variantResults.keySet()) {
            if (variantResults.get(constraint) <= varParams.pValue) {
                System.out.println(decodeConstraint(constraint, translationMap) + " : " + variantResults.get(constraint).toString() + " [Var1: " + measurementsSpecification1.get(constraint).toString() + " | Var2: " + measurementsSpecification2.get(constraint).toString() + "]");
            }
        }
    }

    private void exportVariantResultsToCSV(Map<String, Float> variantResults, File outputFile, JanusVariantCmdParameters varParams, TaskCharArchive alphabet, Map<String, Float> measurementsSpecification1, Map<String, Float> measurementsSpecification2) {
        //		header row
        String[] header = {"Constraint", "p_value", "Measure_VAR1", "Measure_VAR2", "ABS-Difference", "Natural_Language_Description"};
        try {
            FileWriter fw = new FileWriter(outputFile);
            CSVPrinter printer = new CSVPrinter(fw, CSVFormat.DEFAULT.withHeader(header).withDelimiter(';'));

            Map<Character, TaskChar> translationMap = alphabet.getTranslationMapById();
            for (String constraint : variantResults.keySet()) {
//                decode constraint
                String decodedConstraint = decodeConstraint(constraint, translationMap);
//                Row builder
                float difference = Math.abs(measurementsSpecification1.get(constraint) - measurementsSpecification2.get(constraint));
                if (varParams.oKeep || variantResults.get(constraint) <= varParams.pValue) {
                    printer.printRecord(new String[]{
                            decodedConstraint,
                            variantResults.get(constraint).toString(),
                            measurementsSpecification1.get(constraint).toString(),
                            measurementsSpecification2.get(constraint).toString(),
                            String.valueOf(difference),
                            getNaturalLanguageDescription(decodedConstraint, varParams.measure, measurementsSpecification1.get(constraint), measurementsSpecification2.get(constraint), difference)}
                    );
                }
            }

            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static final Map<String, String> DESCRIPTION = new HashMap<String, String>() {{
        put("RespondedExistence", "If [$1] occurs, also [$2] occurs. \n");
        put("CoExistence", "[$1] and [$2] co-occur. \n");
        put("Succession", "[$1] is followed by [$2] and [$2] is preceded by [$1]. \n");
        put("Precedence", "If [$2] occurs, [$1] occurred before it. \n");
        put("Response", "If [$1] occurs, [$2] will occur afterwards. \n");
        put("AlternateSuccession", "[$1] is followed by [$2] and [$2] is preceded by [$1], without any other occurrence of [$1] and [$2] in between. \n");
        put("AlternatePrecedence", "If [$2] occurs, [$1] occurred before it without any other occurrence of [$2] in between. \n");
        put("AlternateResponse", "If [$1] occurs, [$2] will occur afterwards without any other occurrence of [$1] in between. \n");
        put("ChainSuccession", "[$1] is immediately followed by [$2] and [$2] is immediately preceded by [$1]. \n");
        put("ChainPrecedence", "If [$2] occurs, [$1] occurred immediately before it. \n");
        put("ChainResponse", "If [$1] occurs, [$2] occurs immediately afterwards. \n");
        put("NotCoExistence", "[$1] and [$2] do not occur in together in the same process instance. \n");
        put("NotSuccession", "[$1] is not followed by [$2] and [$2] is not preceded by [$1]. \n");
        put("NotChainSuccession", "[$1] is not immediately followed by [$2] and [$2] is not immediately preceded by [$1]. \n");
        put("Participation", "[$1] occurs in every process instance. \n");
        put("AtMostOne", "[$1] may occur at most one time in a process instance. \n");
        put("End", "The process ends with [$1]. \n");
        put("Init", "The process starts with [$1]. \n");
    }};

    private String getNaturalLanguageDescription(String constraint, String measure, float var1measure, float var2measure, float difference) {
        String template = constraint.split("\\(")[0];
        String result = DESCRIPTION.get(template);
        if (DESCRIPTION.get(template) == null)
            logger.error("Constraint without natural language description: " + template);

        if (constraint.contains(",") == false) {
            String task = constraint.split("\\(")[1].replace(")", "");
            result = result.replace("$1", task);
        } else {
            String task1 = constraint.split("\\(")[1].replace(")", "").split(",")[0];
            String task2 = constraint.split("\\(")[1].replace(")", "").split(",")[1];
            result = result.replace("$1", task1).replace("$2", task2);
        }
        if (Float.isNaN(difference)) {
            String onlyVariant = (Float.isNaN(var1measure)) ? "2" : "1";
            result += "This happens only in variant " + onlyVariant;
//            result += "This is applicable only in variant " + onlyVariant;
        } else {
            String greaterVariance = (var1measure > var2measure) ? "1" : "2";
            String smallerVariance = (var1measure > var2measure) ? "2" : "1";
            result += "In variant " + greaterVariance + " it is " + (difference * 100) + "% more likely than variant" + smallerVariance;
//        3)    .... In [Varaint 1/2] it is [diff %] more likely than [Variant 2/1]
        }
        return result;
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

    public void manageVariantOutput(Map<String, Float> variantResults,
                                    ViewCmdParameters viewParams, JanusVariantCmdParameters varParams, SystemCmdParameters systemParams, TaskCharArchive alphabet, Map<String, Float> measurementsSpecification1, Map<String, Float> measurementsSpecification2) {
        this.manageVariantOutput(variantResults, null, varParams, viewParams, systemParams, alphabet, measurementsSpecification1, measurementsSpecification2);
    }


}
