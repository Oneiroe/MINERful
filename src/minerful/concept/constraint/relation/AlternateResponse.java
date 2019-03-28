/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package minerful.concept.constraint.relation;

import javax.xml.bind.annotation.XmlRootElement;

import dk.brics.automaton.Automaton;
import minerful.concept.TaskChar;
import minerful.concept.TaskCharSet;
import minerful.concept.constraint.Constraint;
import minerful.reactive.automaton.ConjunctAutomata;
import minerful.reactive.automaton.SeparatedAutomaton;
import minerful.reactive.automaton.Utils;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement
public class AlternateResponse extends Response {
	@Override
	public String getRegularExpressionTemplate() {
		return "[^%1$s]*([%1$s][^%1$s]*[%2$s][^%1$s]*)*[^%1$s]*";
	}
	
	protected AlternateResponse() {
		super();
	}

    public AlternateResponse(TaskChar param1, TaskChar param2) {
        super(param1, param2);
    }
    public AlternateResponse(TaskChar param1, TaskChar param2, double support) {
        super(param1, param2, support);
    }
    public AlternateResponse(TaskCharSet param1, TaskCharSet param2,
			double support) {
		super(param1, param2, support);
	}
	public AlternateResponse(TaskCharSet param1, TaskCharSet param2) {
		super(param1, param2);
	}

	@Override
    public int getHierarchyLevel() {
        return super.getHierarchyLevel() + 1;
    }
	
	@Override
	public Constraint suggestConstraintWhichThisShouldBeBasedUpon() {
		return new Response(base, implied);
	}

	@Override
	public Constraint copy(TaskChar... taskChars) {
		super.checkParams(taskChars);
		return new AlternateResponse(taskChars[0], taskChars[1]);
	}

	@Override
	public Constraint copy(TaskCharSet... taskCharSets) {
		super.checkParams(taskCharSets);
		return new AlternateResponse(taskCharSets[0], taskCharSets[1]);
	}


	@Override
	public SeparatedAutomaton buildParametricSeparatedAutomaton() {
		char[] alphabet = {'a', 'b', 'z'};
		char[] alphabetOthers = {alphabet[1], alphabet[2]};
		Automaton activator = Utils.getSingleCharActivatorAutomaton(alphabet[0], alphabetOthers);

		List<ConjunctAutomata> disjunctAutomata = new ArrayList<ConjunctAutomata>();

		char[] others = {alphabet[2]};
		Automaton futureAutomaton = Utils.getNextNegativeUntilAutomaton(alphabet[0], alphabet[1], others);
		ConjunctAutomata conjunctAutomaton = new ConjunctAutomata(null, null, futureAutomaton);

		disjunctAutomata.add(conjunctAutomaton);
		SeparatedAutomaton res = new SeparatedAutomaton(activator, disjunctAutomata, alphabet);
		res.setNominalID(this.type);
		return res;
	}


}