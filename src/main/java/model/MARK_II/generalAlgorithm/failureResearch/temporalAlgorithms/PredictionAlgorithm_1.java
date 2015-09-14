package model.MARK_II.generalAlgorithm.failureResearch.temporalAlgorithms;

import model.MARK_II.generalAlgorithm.ColumnPosition;
import model.MARK_II.generalAlgorithm.Pooler;
import model.MARK_II.generalAlgorithm.SpatialPooler;
import model.MARK_II.region.*;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * An experimental prediction algorithm. Initial ideas behind how this algorithm
 * was designed are here: https://github.com/WalnutiQ/walnut/issues/199
 *
 * This class solidifies those initial ideas into a deterministic prediction
 * algorithm that probably doesn't work well. Fully understanding why this
 * algorithm doesn't predict well will be important to generating new ideas on
 * how to improve it.
 *
 * @author Q Liu (quinnliu@vt.edu)
 * @version 9/13/2015
 */
public class PredictionAlgorithm_1 extends Pooler {
    private SpatialPooler spatialPooler;

    Set<Neuron> previouslyActiveNeurons;
    Set<Neuron> currentActiveNeurons;

    /**
     * TODO: size = ? Set a max & min with a new class? For now set same
     * as activeColumns.size()
     */
    Set<Neuron> isPredictingNeurons;

    public PredictionAlgorithm_1(SpatialPooler spatialPooler) {
        this.spatialPooler = spatialPooler;
        super.region = spatialPooler.getRegion();

        this.previouslyActiveNeurons = new HashSet<>();
        this.currentActiveNeurons = new HashSet<>();
        this.isPredictingNeurons = new HashSet<>();
    }

    /**
     * Call this method to run PredictionAlgorithm_1 once on Region.
     *
     * MAIN LOGIC: For each learning neuron in an active column, connect to all
     * previously active neurons.
     */
    public void run() {
        Set<ColumnPosition> activeColumnPositions = this.spatialPooler.getActiveColumnPositions();
        // Step 1) Which neurons to apply logic to?
        // Possible answer: Iterate through all active neurons in region
        for (ColumnPosition ACP : activeColumnPositions) {
            Column activeColumn = super.getRegion().getColumn(ACP.getRow(), ACP.getRow());
            Neuron learningNeuron = this.getNeuronWithLeastNumberOfConnectedSynapses(activeColumn);

            // Step 2) How do you allow neuronA to predict neuronB will become
            // active in the next time step?
            // Possible answer: For each learning neuron connect to all
            // previously active neurons. 1 new distal segment per learning neuron.
            DistalSegment distalSegment = new DistalSegment();

            for (Neuron previouslyActiveNeuron : this.previouslyActiveNeurons) {
                distalSegment.addSynapse(new Synapse<>(previouslyActiveNeuron,
                        Synapse.MINIMAL_CONNECTED_PERMANENCE, -1, -1));
            }
            learningNeuron.addDistalSegment(distalSegment);
            // Step 3) Which neurons should be active for the current time step?
            // Possible answer: the current list of learning neurons? This
            // is because they aren't connected to anything since they have
            // the least connected synapses
            // TODO: possible answer = only previously active neurons
            //       closeby to current learning neuron
            learningNeuron.setActiveState(true);
            this.currentActiveNeurons.add(learningNeuron);
        }

        // Step 4) What neurons can be used for prediction?
        // Possible answer: which neurons currently have the most # of connected
        // (NOT active Cells)
        // synapses across all distal dendrites connected to the current set of
        // active neurons. This is where we reward all the competition between
        // all synapses to represent an connection to a past time step.

        // NOTE: connectionScores = sorted # of connected synapses for each neuron in Region
        Set<Integer> connectionScores = this.getConnectionScores();

        int index = Math.max(0, connectionScores.size() - this.spatialPooler.getActiveColumnPositions().size());
        int minimumConnectionScore = (Integer) connectionScores.toArray()[index];

        // Step 5) How many number of predicting neurons?
        // Possible answer: same number of currently active neurons.
        // TODO: the same number of active neurons as predicting neurons
        // means a very clear prediction however plus or minus certain
        // predicting neurons means unsureness
        for (Neuron activeNeuron : this.currentActiveNeurons) {
            Column[][] columns = super.region.getColumns();
            for (int ri = 0; ri < columns.length; ri++) {
                for (int ci = 0; ci < columns[0].length; ci++) {
                    for (Neuron maybePredictingNeuron : columns[ri][ci].getNeurons()) {
                        int connectionScore = this.getNumberOfConnectedSynapsesToCurrentActiveNeuron(maybePredictingNeuron, activeNeuron);

                        if (this.isPredictingNeurons.size() >= this.spatialPooler.getActiveColumnPositions().size()) {
                            break; // TODO: move step 5 into method and convert to break
                        }

                        if (connectionScore >= minimumConnectionScore) {
                            maybePredictingNeuron.setPredictingState(true);
                            this.isPredictingNeurons.add(maybePredictingNeuron);
                        }
                    }
                }
            }
        }

        // Step 6) Which synapse connections should be strengthened to model
        // long term potentiation?
        // Possible answer:
        // TODO: strengthen the connection between active neuron @ t = 0 and
        // isPredicting neuron @ t = 0 where isPredicting neuron is
        // active @ t = 1.
        // TODO: investigate if problem if neuron stays active forever?

        // Step 7) Which synapse connections should be weakened to model
        // long term depression?
        // Possible answer:
        // TODO: weaken the connection between active neuron @ t = 0 and
        // isPredicting neuron @ t = 0 where isPredicting neuron is NOT active
        // @ t = 1.

        // prepare for next time step be clearing current info that is out of date
        for (Neuron neuron : this.currentActiveNeurons) {
            this.previouslyActiveNeurons.add(neuron);
            neuron.setActiveState(false);
        }
        this.currentActiveNeurons.clear();
        // TODO: iterate through all neurons and call nextTimeStep()
    }

    /**
     * @return sorted # of connected synapses for each neuron in Region aka
     * connection scores.
     */
    Set<Integer> getConnectionScores() {
        Set<Integer> connectionScores = new TreeSet<>();

        // TODO: note a given neuron might have distal segments attached to
        // multiple current active neurons. Does the following code account
        // for that?

        for (Neuron activeNeuron : this.currentActiveNeurons) {
            // we want to figure out which neurons(let's call them
            // futureNeurons) in any previous time step created a
            // synapse to attach to me(activeNeuron)! This
            // means that in the past after "activeNeuron" was
            // active, then in the next time step "futureNeurons" was
            // active. Thus, if "activeNeuron" is currently
            // active, then this is an INDICATOR that "futureNeurons"
            // will be active in the next time step. Thus we will mark
            // these neurons as "possiblyActiveInNextTimeStep" or
            // "isPredicting".

            // get # of connected synapses for each neuron in Region for the
            // current set of active neurons
            Column[][] columns = super.region.getColumns();
            for (int ri = 0; ri < columns.length; ri++) {
                for (int ci = 0; ci < columns[0].length; ci++) {
                    for (Neuron maybePredictingNeuron : columns[ri][ci].getNeurons()) {
                        connectionScores.add(this.getNumberOfConnectedSynapsesToCurrentActiveNeuron(maybePredictingNeuron, activeNeuron));
                    }
                }
            }
        }
        return connectionScores;
    }

    int getNumberOfConnectedSynapsesToCurrentActiveNeuron(Neuron maybePredictingNeuron, Neuron activeNeuron) {
        // TODO: consider cyclical connections if this is even possible?
        int numberOfConnectedSynapsesToCurrentActiveNeuron = 0;
        for (DistalSegment distalSegment : maybePredictingNeuron.getDistalSegments()) {
            for (Synapse synapse : distalSegment.getConnectedSynapses()) {
                if (synapse.getCell().equals(activeNeuron)) {
                    numberOfConnectedSynapsesToCurrentActiveNeuron++;
                }
            }
        }
        return numberOfConnectedSynapsesToCurrentActiveNeuron;
    }

    /**
     * PROS:
     * 1) Prevents same neuron in column to repeatedly be learning neuron.
     * 2) Events out connections within Region like real neocortex.
     *
     * CONS:
     * 1) Is this enough of an indicator to say next time neuron2 becomes
     *    active -> neuron3 becomes predicted?
     */
    Neuron getNeuronWithLeastNumberOfConnectedSynapses(Column activeColumn) {
        // TODO: implement
        return null;
    }
}
