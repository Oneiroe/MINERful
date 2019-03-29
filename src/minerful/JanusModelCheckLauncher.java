package minerful;

import minerful.checking.ProcessSpecificationFitnessEvaluator;
import minerful.checking.params.CheckingCmdParameters;
import minerful.checking.relevance.dao.ModelFitnessEvaluation;
import minerful.concept.ProcessModel;
import minerful.io.ProcessModelLoader;
import minerful.io.params.InputModelParameters;
import minerful.logparser.LogParser;
import minerful.logparser.LogTraceParser;
import minerful.miner.core.MinerFulPruningCore;
import minerful.params.InputLogCmdParameters;
import minerful.params.SystemCmdParameters;
import minerful.postprocessing.params.PostProcessingCmdParameters;
import minerful.reactive.miner.ReactiveMinerOfflineQueryingCore;
import minerful.utils.MessagePrinter;
import org.processmining.plugins.declareminer.visualizing.AssignmentModel;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

/**
 * Class for launching JanusZ model checker
 */
public class JanusModelCheckLauncher {
	public static MessagePrinter logger = MessagePrinter.getInstance(JanusModelCheckLauncher.class);

	private ProcessModel processSpecification;
	private LogParser eventLog;
	private CheckingCmdParameters chkParams;

	private JanusModelCheckLauncher(CheckingCmdParameters chkParams) {
		this.chkParams = chkParams;
	}

	public JanusModelCheckLauncher(AssignmentModel declareMapModel, LogParser inputLog, CheckingCmdParameters chkParams) {
		this(chkParams);
		this.processSpecification = new ProcessModelLoader().loadProcessModel(declareMapModel);
		this.eventLog = inputLog;
	}

	public JanusModelCheckLauncher(ProcessModel minerFulProcessModel, LogParser inputLog, CheckingCmdParameters chkParams) {
		this(chkParams);
		this.processSpecification = minerFulProcessModel;
		this.eventLog = inputLog;
	}

	public JanusModelCheckLauncher(InputModelParameters inputParams, PostProcessingCmdParameters preProcParams,
                                   InputLogCmdParameters inputLogParams, CheckingCmdParameters chkParams, SystemCmdParameters systemParams) {
		this(chkParams);

		if (inputParams.inputFile == null) {
			systemParams.printHelpForWrongUsage("Input process model file missing!");
			System.exit(1);
		}
		this.eventLog = MinerFulMinerLauncher.deriveLogParserFromLogFile(inputLogParams);

		// Load the process specification from the file
		this.processSpecification =
				new ProcessModelLoader().loadProcessModel(inputParams.inputLanguage, inputParams.inputFile, this.eventLog.getTaskCharArchive());
		// Apply some preliminary pruning
//		MinerFulPruningCore pruniCore = new MinerFulPruningCore(this.processSpecification, preProcParams);
//		this.processSpecification.bag = pruniCore.massageConstraints();



		MessagePrinter.configureLogging(systemParams.debugLevel);
	}

	public ProcessModel getProcessSpecification() {
		return processSpecification;
	}

	public LogParser getEventLog() {
		return eventLog;
	}

	/**
	 * Check the input model against the input log.
	 * TODO Return the MegaMatrix
	 */
	public void checkModel() {
		processSpecification.bag.initAutomataBag();
		ReactiveMinerOfflineQueryingCore minerFulQueryingCore = new ReactiveMinerOfflineQueryingCore(
				0, eventLog, null, null, eventLog.getTaskCharArchive(), null, processSpecification.bag);
		double before = System.currentTimeMillis();
		minerFulQueryingCore.discover();
		double after = System.currentTimeMillis();

		logger.info("Total KB querying time: " + (after - before));
	}

	public ModelFitnessEvaluation check() {
		ProcessSpecificationFitnessEvaluator evalor = new ProcessSpecificationFitnessEvaluator(
				this.eventLog.getEventEncoderDecoder(), this.processSpecification);

		ModelFitnessEvaluation evalon = evalor.evaluateOnLog(this.eventLog);

		reportOnEvaluation(evalon);

	    return evalon;
	}

	public ModelFitnessEvaluation check(LogTraceParser trace) {
		ProcessSpecificationFitnessEvaluator evalor = new ProcessSpecificationFitnessEvaluator(
				this.eventLog.getEventEncoderDecoder(), this.processSpecification);

		ModelFitnessEvaluation evalon = evalor.evaluateOnTrace(trace);

		reportOnEvaluation(evalon);

		return evalon;
	}

	private static String printFitnessJsonSummary(ModelFitnessEvaluation evalon) {
		return "{\"Avg.fitness\":"
				+ MessagePrinter.formatFloatNumForCSV(evalon.avgFitness()) + ";"
				+ "\"Trace-fit-ratio\":"
				+ MessagePrinter.formatFloatNumForCSV(evalon.traceFitRatio())
				+ "}";
	}

	private void reportOnEvaluation(ModelFitnessEvaluation evalon) {
		if (evalon.isFullyFitting()) {
			logger.info("Yay! The passed declarative process specification is fully fitting with the input traces! Summary:\n"
			+ printFitnessJsonSummary(evalon) + "\n");
		} else {
			logger.warn(
					"The passed declarative process specification is not fully fitting with the input traces. Summary:\n"
					+ printFitnessJsonSummary(evalon) + "\n"
					+ ((chkParams.fileToSaveResultsAsCSV == null) ?
							"See below for further details." :
							"See " + chkParams.fileToSaveResultsAsCSV.getAbsolutePath() + " for further details.")
					);
		}

		if (chkParams.fileToSaveResultsAsCSV != null) {
			logger.info("Saving results in CSV format as " + chkParams.fileToSaveResultsAsCSV + "...");
			PrintWriter outWriter = null;
        	try {
    				outWriter = new PrintWriter(chkParams.fileToSaveResultsAsCSV);
    	        	outWriter.print(evalon.printCSV());
    	        	outWriter.flush();
    	        	outWriter.close();
    			} catch (FileNotFoundException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
		}
	}

}